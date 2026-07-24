#!/usr/bin/env python3
"""Run the release smoke flow through the public gateway."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import sys
import uuid
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

EXPECTED_VERSION = "0.3.0-alpha"
DEFAULT_TENANT_ID = "11111111-1111-4111-8111-111111111111"
OCR_FIXTURE = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAXAAAAAkCAAAAAC1Gj62AAABGUlEQVR4nO2a0QrDIAxFdez/"
    "f9nRB0EksbF1txbOedqii3IJV5s1lwRKPtLVAMHVUOFivvVDboLFiI3ideyIt2dC/T6aH4l754y1"
    "5z7+j5x34lS4GAQXg+BPeLjnvSkYb+lzVUaxbIx763qx9rzw9rYq52y8hQoXg+BiEPype/gqzjxs"
    "ZyJ79u7XUahwMQguBsF38/DZXoHXu8hir7ybc3RPt3pA1p3cWosKF4PgYhB8Nw+P9CW88Ts+XCbn"
    "r3xGiPRZzsa8vg0VLgbBxSD423spu1Mu+HG6MN+DCheD4GIQ/AkPv3Jnjr7nMZqfjfGoH3p7jryD"
    "4vl4/V2f11trdv4BFS4GwcUguJj8xv8e3wwVLgbBxSB40vID84VYQSDerIkAAAAASUVORK5CYII="
)


@dataclass(frozen=True)
class Response:
    status: int
    body: bytes
    content_type: str

    def json(self) -> dict[str, Any]:
        decoded = json.loads(self.body.decode("utf-8"))
        if not isinstance(decoded, dict):
            raise AssertionError("Expected a JSON object response")
        return decoded


class SmokeClient:
    def __init__(self, base_url: str, internal_token: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token
        self.timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        body: bytes | None = None,
        headers: dict[str, str] | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> Response:
        request = Request(
            self.base_url + path, data=body, headers=headers or {}, method=method
        )
        try:
            with urlopen(request, timeout=self.timeout) as result:
                response = Response(
                    result.status,
                    result.read(),
                    result.headers.get_content_type(),
                )
        except HTTPError as error:
            response = Response(
                error.code,
                error.read(),
                error.headers.get_content_type(),
            )
        if response.status not in expected:
            excerpt = response.body.decode("utf-8", errors="replace")[:1000]
            raise AssertionError(
                f"{method} {path}: expected {expected}, got {response.status}: {excerpt}"
            )
        return response

    def json_request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        *,
        token: str | None = None,
        extra_headers: dict[str, str] | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> Response:
        headers = {"Accept": "application/json"}
        body = None
        if payload is not None:
            body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        headers.update(extra_headers or {})
        return self.request(method, path, body=body, headers=headers, expected=expected)

    def internal_request(
        self,
        path: str,
        body: bytes,
        content_type: str,
        user_id: str,
        *,
        extra_headers: dict[str, str] | None = None,
    ) -> Response:
        headers = {
            "Content-Type": content_type,
            "X-OpenEIP-Internal-Token": self.internal_token,
            "X-Tenant-ID": DEFAULT_TENANT_ID,
            "X-User-ID": user_id,
            "X-Request-ID": str(uuid.uuid4()),
        }
        headers.update(extra_headers or {})
        return self.request("POST", path, body=body, headers=headers)

    def sse_request(
        self,
        path: str,
        payload: dict[str, Any],
        token: str,
    ) -> Response:
        return self.request(
            "POST",
            path,
            body=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
            headers={
                "Accept": "text/event-stream",
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
        )


def require_envelope(response: Response) -> dict[str, Any]:
    payload = response.json()
    if payload.get("code") != 0 or payload.get("message") != "success":
        raise AssertionError(f"Expected success envelope, got {payload}")
    data = payload.get("data")
    if not isinstance(data, dict):
        raise AssertionError("Expected object data in success envelope")
    return data


def multipart_file(name: str, content_type: str, content: bytes) -> tuple[bytes, str]:
    boundary = f"openeip-{uuid.uuid4().hex}"
    body = b"".join(
        (
            f"--{boundary}\r\n".encode(),
            f'Content-Disposition: form-data; name="file"; filename="{name}"\r\n'.encode(),
            f"Content-Type: {content_type}\r\n\r\n".encode(),
            content,
            f"\r\n--{boundary}--\r\n".encode(),
        )
    )
    return body, f"multipart/form-data; boundary={boundary}"


def assert_sse(response: Response, required_events: tuple[str, ...]) -> str:
    if response.content_type != "text/event-stream":
        raise AssertionError(f"Expected SSE, got {response.content_type}")
    stream = response.body.decode("utf-8")
    for event in required_events:
        if f"event: {event}" not in stream:
            raise AssertionError(f"Missing SSE event {event}: {stream[:2000]}")
    if "event: error" in stream or "event: execution.error" in stream:
        raise AssertionError(f"Unexpected SSE error: {stream[:2000]}")
    return stream


def run(client: SmokeClient) -> None:
    unique = uuid.uuid4().hex[:12]
    username = f"release_{unique}"
    password = "Release-Smoke-2026!"
    text = b"OpenEIP release smoke verifies grounded retrieval across the complete Knowledge boundary."

    root = client.request("GET", "/")
    if b"OpenEIP" not in root.body:
        raise AssertionError("Frontend root does not contain the product name")
    python_health = client.request("GET", "/ai/health").json()
    if (
        python_health.get("status") != "healthy"
        or python_health.get("version") != EXPECTED_VERSION
    ):
        raise AssertionError(f"Unexpected Python health payload: {python_health}")
    passed("gateway, frontend, and Python health")

    registered = require_envelope(
        client.json_request(
            "POST",
            "/api/v1/auth/register",
            {
                "username": username,
                "email": f"{username}@example.test",
                "password": password,
            },
            expected=(201,),
        )
    )
    user_id = registered["id"]
    login = require_envelope(
        client.json_request(
            "POST", "/api/v1/auth/login", {"username": username, "password": password}
        )
    )
    token = login["accessToken"]
    current = require_envelope(
        client.json_request("GET", "/api/v1/auth/me", token=token)
    )
    if current["id"] != user_id or "ROLE_USER" not in current["roles"]:
        raise AssertionError(f"Unexpected current user: {current}")
    client.json_request("GET", "/api/v1/auth/roles", token=token, expected=(403,))
    platform = client.json_request("GET", "/api/v1/platform/info", token=token).json()
    if platform.get("version") != EXPECTED_VERSION:
        raise AssertionError(f"Unexpected Java version: {platform}")
    passed("registration, login, identity, RBAC, and Java version")

    upload_body, upload_type = multipart_file("release-smoke.txt", "text/plain", text)
    uploaded = require_envelope(
        client.request(
            "POST",
            "/api/v1/documents/files",
            body=upload_body,
            headers={"Authorization": f"Bearer {token}", "Content-Type": upload_type},
            expected=(201,),
        )
    )
    document_id = uploaded["id"]
    downloaded = client.request(
        "GET",
        f"/api/v1/documents/files/{document_id}/content",
        headers={"Authorization": f"Bearer {token}"},
    ).body
    if downloaded != text or uploaded["sha256"] != hashlib.sha256(text).hexdigest():
        raise AssertionError("Uploaded and downloaded document digests differ")
    passed("file upload and download integrity")

    knowledge = require_envelope(
        client.json_request(
            "POST",
            "/api/v1/knowledge/bases",
            {
                "name": f"Release smoke {unique}",
                "description": "v0.3 release verification",
            },
            token=token,
            expected=(201,),
        )
    )
    knowledge_base_id = knowledge["id"]
    attached = require_envelope(
        client.json_request(
            "POST",
            f"/api/v1/knowledge/bases/{knowledge_base_id}/documents",
            {"documentId": document_id},
            token=token,
            expected=(201,),
        )
    )
    if attached["documentId"] != document_id:
        raise AssertionError(f"Unexpected attached document: {attached}")
    passed("knowledge base creation and document attachment")

    ocr = require_envelope(
        client.internal_request(
            "/ai/api/v1/ocr/recognitions",
            OCR_FIXTURE,
            "image/png",
            user_id,
        )
    )
    if ocr["text"] != "OPENEIP OCR 2026":
        raise AssertionError(f"Unexpected OCR result: {ocr}")

    parsed = require_envelope(
        client.internal_request(
            "/ai/api/v1/parsing/documents",
            text,
            "text/plain; charset=utf-8",
            user_id,
            extra_headers={"X-Document-ID": document_id},
        )
    )
    chunks = parsed["chunks"]
    if not chunks:
        raise AssertionError("Parser returned no chunks")
    passed("OCR and document parsing")

    embedding_payload = {
        "jobId": str(uuid.uuid4()),
        "knowledgeBaseId": knowledge_base_id,
        "documentId": document_id,
        "chunks": [
            {
                "chunkId": chunk["chunkId"],
                "text": chunk["text"],
                "sourceSha256": chunk["sha256"],
                "pages": chunk["pages"],
                "startChar": chunk["startChar"],
                "endChar": chunk["endChar"],
            }
            for chunk in chunks
        ],
    }
    embedded = require_envelope(
        client.internal_request(
            "/ai/api/v1/embedding/batches",
            json.dumps(embedding_payload, separators=(",", ":")).encode(),
            "application/json",
            user_id,
        )
    )
    if embedded["vectorCount"] != len(chunks):
        raise AssertionError(f"Unexpected embedding result: {embedded}")
    rag_payload = {
        "knowledgeBaseId": knowledge_base_id,
        "query": "What does the release smoke verify?",
        "topK": 3,
    }
    rag = require_envelope(
        client.internal_request(
            "/ai/api/v1/rag/queries",
            json.dumps(rag_payload, separators=(",", ":")).encode(),
            "application/json",
            user_id,
        )
    )
    if not rag["citations"] or "release smoke" not in rag["answer"]:
        raise AssertionError(f"RAG did not return grounded evidence: {rag}")
    passed("embedding and grounded RAG")

    session = require_envelope(
        client.json_request(
            "POST",
            "/api/v1/chat/sessions",
            {"knowledgeBaseId": knowledge_base_id, "title": "Release smoke"},
            token=token,
            expected=(201,),
        )
    )
    session_id = session["sessionId"]
    chat = client.sse_request(
        f"/api/v1/chat/sessions/{session_id}/messages:stream",
        {"message": "What does the release smoke verify?", "topK": 3},
        token,
    )
    assert_sse(chat, ("token", "done"))
    history = client.json_request(
        "GET", f"/api/v1/chat/sessions/{session_id}/messages", token=token
    ).json()["data"]
    if not isinstance(history, list) or len(history) != 2:
        raise AssertionError(f"Chat history was not persisted: {history}")
    passed("Java-to-Python Chat SSE and persisted history")

    catalog = client.json_request("GET", "/api/v1/agents", token=token).json()["data"]
    if (
        not isinstance(catalog, list)
        or not catalog
        or catalog[0]["agentId"] != "openeip.constrained-v1"
    ):
        raise AssertionError(f"Unexpected Agent catalog: {catalog}")
    agent = client.sse_request(
        "/api/v1/agents/openeip.constrained-v1/executions:stream",
        {
            "input": "search: What does the release smoke verify?",
            "knowledgeBaseId": knowledge_base_id,
            "allowedTools": ["knowledge.search"],
            "maxSteps": 4,
        },
        token,
    )
    assert_sse(
        agent,
        ("execution.started", "tool.started", "tool.completed", "execution.completed"),
    )
    passed("authorized Agent tool execution and termination")

    workflow = require_envelope(
        client.json_request(
            "POST",
            "/api/v1/workflows",
            {
                "name": f"Release workflow {unique}",
                "description": "release approval and retry",
            },
            token=token,
            expected=(201,),
        )
    )
    workflow_id = workflow["id"]
    approval_graph = {
        "schemaVersion": 1,
        "nodes": [
            {
                "id": "start",
                "type": "START",
                "schemaVersion": 1,
                "position": {"x": 0, "y": 0},
                "config": {},
            },
            {
                "id": "approve",
                "type": "APPROVAL",
                "schemaVersion": 1,
                "position": {"x": 200, "y": 0},
                "config": {"mode": "ANY", "assigneeIds": [user_id]},
            },
            {
                "id": "end",
                "type": "END",
                "schemaVersion": 1,
                "position": {"x": 400, "y": 0},
                "config": {},
            },
        ],
        "edges": [
            {
                "id": "to_approval",
                "source": "start",
                "sourcePort": "out",
                "target": "approve",
                "targetPort": "in",
            },
            {
                "id": "to_end",
                "source": "approve",
                "sourcePort": "out",
                "target": "end",
                "targetPort": "in",
            },
        ],
    }
    updated = require_envelope(
        client.json_request(
            "PATCH",
            f"/api/v1/workflows/{workflow_id}",
            {
                "name": workflow["name"],
                "description": workflow["description"],
                "graph": approval_graph,
            },
            token=token,
            extra_headers={"If-Match": str(workflow["draftRevision"])},
        )
    )
    client.json_request(
        "POST",
        f"/api/v1/workflows/{workflow_id}/publish",
        token=token,
        extra_headers={"If-Match": str(updated["draftRevision"])},
        expected=(201,),
    )
    execution = require_envelope(
        client.json_request(
            "POST",
            f"/api/v1/workflows/{workflow_id}/executions",
            {"input": {}},
            token=token,
            extra_headers={"Idempotency-Key": f"release-{unique}"},
            expected=(202,),
        )
    )
    execution_id = execution["id"]
    event_payload = client.json_request(
        "GET", f"/api/v1/workflow-executions/{execution_id}/events", token=token
    ).json()["data"]
    approval_id = next(
        event["data"]["approvalId"]
        for event in event_payload
        if event["type"] == "workflow.approval.requested"
    )
    rejected = require_envelope(
        client.json_request(
            "POST",
            f"/api/v1/workflow-approvals/{approval_id}/decisions",
            {"decision": "REJECT", "comment": "exercise retry"},
            token=token,
            extra_headers={"Idempotency-Key": f"reject-{unique}"},
        )
    )
    if rejected["status"] != "FAILED":
        raise AssertionError(f"Workflow rejection did not fail: {rejected}")
    client.json_request(
        "POST",
        f"/api/v1/workflow-executions/{execution_id}/nodes/approve/retry",
        token=token,
        extra_headers={"Idempotency-Key": f"retry-{unique}"},
        expected=(202,),
    )
    approved = require_envelope(
        client.json_request(
            "POST",
            f"/api/v1/workflow-approvals/{approval_id}/decisions",
            {"decision": "APPROVE", "comment": "resume"},
            token=token,
            extra_headers={"Idempotency-Key": f"approve-{unique}"},
        )
    )
    if approved["status"] != "SUCCEEDED":
        raise AssertionError(f"Workflow did not resume to success: {approved}")
    final_events = client.json_request(
        "GET", f"/api/v1/workflow-executions/{execution_id}/events", token=token
    ).json()["data"]
    if not any(
        event["type"] == "workflow.node.retry.requested" for event in final_events
    ):
        raise AssertionError("Workflow retry event is missing")
    client.request(
        "DELETE",
        f"/api/v1/workflows/{workflow_id}",
        headers={"Authorization": f"Bearer {token}"},
        expected=(204,),
    )
    passed("Workflow publish, approval, retry, resume, events, and cleanup")

    client.request(
        "DELETE",
        f"/api/v1/knowledge/bases/{knowledge_base_id}/documents/{document_id}",
        headers={"Authorization": f"Bearer {token}"},
        expected=(204,),
    )
    client.request(
        "DELETE",
        f"/api/v1/knowledge/bases/{knowledge_base_id}",
        headers={"Authorization": f"Bearer {token}"},
        expected=(204,),
    )
    client.request(
        "DELETE",
        f"/api/v1/documents/files/{document_id}",
        headers={"Authorization": f"Bearer {token}"},
        expected=(204,),
    )
    client.json_request(
        "GET", f"/api/v1/documents/files/{document_id}", token=token, expected=(404,)
    )
    passed("resource cleanup and not-found boundary")


def passed(label: str) -> None:
    print(f"PASS: {label}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:3000")
    parser.add_argument(
        "--internal-token",
        default="local-openeip-internal-token",
        help="Must match OPENEIP_INTERNAL_API_TOKEN used by Compose",
    )
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()
    try:
        run(SmokeClient(args.base_url, args.internal_token, args.timeout))
    except (
        AssertionError,
        OSError,
        ValueError,
        KeyError,
        json.JSONDecodeError,
    ) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("PASS: v0.3.0-alpha full-stack release smoke", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
