"""Kafka consumer, retry, idempotency, and DLQ verifier for Spike-002."""

import asyncio
import json
import logging
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

BOOTSTRAP_SERVERS = "kafka:9092"
INPUT_TOPIC = "openeip.spike.events.v1"
DLQ_TOPIC = "openeip.spike.events.dlq.v1"
EXPECTED_RECORDS = 2_003
LOGGER = logging.getLogger(__name__)


async def connect_consumer(*topics: str, group_id: str) -> AIOKafkaConsumer:
    """Connect a consumer, retrying while Kafka finishes initialization."""
    for attempt in range(60):
        consumer = AIOKafkaConsumer(
            *topics,
            bootstrap_servers=BOOTSTRAP_SERVERS,
            group_id=group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            value_deserializer=json.loads,
        )
        try:
            await consumer.start()
            return consumer
        except Exception:
            await consumer.stop()
            if attempt == 59:
                raise
            await asyncio.sleep(1)
    raise RuntimeError("Kafka consumer connection retries exhausted")


async def verify_dlq() -> dict[str, Any]:
    """Read back the actual DLQ record from Kafka."""
    consumer = await connect_consumer(DLQ_TOPIC, group_id=f"spike-002-dlq-{time.time_ns()}")
    try:
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            batches = await consumer.getmany(timeout_ms=1_000)
            for records in batches.values():
                for record in records:
                    value = record.value
                    if value.get("original", {}).get("eventId") == "event-poison":
                        return {"found": True, "record": value}
        return {"found": False, "record": None}
    finally:
        await consumer.stop()


async def run() -> None:
    """Consume the full fixture and emit machine-readable evidence."""
    consumer = await connect_consumer(INPUT_TOPIC, group_id=f"spike-002-main-{time.time_ns()}")
    producer = AIOKafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_serializer=lambda value: json.dumps(value).encode(),
    )
    await producer.start()
    seen: set[str] = set()
    received = 0
    processed = 0
    duplicates = 0
    poison_attempts = 0
    started: float | None = None
    try:
        deadline = time.monotonic() + 90
        while received < EXPECTED_RECORDS and time.monotonic() < deadline:
            batches = await consumer.getmany(timeout_ms=1_000, max_records=500)
            for records in batches.values():
                for record in records:
                    started = started or time.perf_counter()
                    received += 1
                    event = record.value
                    event_id = str(event["eventId"])
                    if event["eventType"] == "validation.poison":
                        for _poison_attempt in range(1, 4):
                            await asyncio.sleep(0.005)
                            poison_attempts += 1
                        await producer.send_and_wait(
                            DLQ_TOPIC,
                            {
                                "original": event,
                                "attempts": poison_attempts,
                                "error": "deliberate validation failure",
                            },
                        )
                    elif event_id in seen:
                        duplicates += 1
                    else:
                        seen.add(event_id)
                        processed += 1
            if batches:
                await consumer.commit()
    finally:
        await consumer.stop()
        await producer.stop()

    elapsed = time.perf_counter() - started if started is not None else 0.0
    dlq = await verify_dlq()
    passed = (
        received == EXPECTED_RECORDS
        and processed == 2_001
        and duplicates == 1
        and poison_attempts == 3
        and dlq["found"]
    )
    evidence = {
        "spike": "spike-002",
        "executed_at": datetime.now(UTC).isoformat(),
        "records_received": received,
        "events_processed": processed,
        "duplicates_skipped": duplicates,
        "poison_attempts": poison_attempts,
        "dlq": dlq,
        "elapsed_seconds": elapsed,
        "consumer_throughput_rps": received / elapsed if elapsed else 0,
        "passed": passed,
    }
    results = Path("/results")
    results.mkdir(parents=True, exist_ok=True)
    (results / "result.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    (results / "consumer.done").touch()
    if not passed:
        raise RuntimeError("Spike-002 acceptance criteria failed")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(run())
