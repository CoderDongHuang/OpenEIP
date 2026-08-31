"""Verification and bounded replay protection for Java-issued execution capabilities."""

import base64
import hashlib
import hmac
import json
import time
from collections import OrderedDict
from threading import Lock
from uuid import UUID

from pydantic import ValidationError

from engine_core.agent.v2.models import ExecutionCapability
from engine_core.shared.api_error import EngineApiError


class CapabilityError(EngineApiError):
    """Stable error used by the internal Agent v2 API."""


class CapabilityVerifier:
    """Verify a compact HMAC capability and reject nonce replay in this runtime."""

    def __init__(self, secret: str, *, clock_skew_seconds: int = 5, max_nonces: int = 20_000) -> None:
        if len(secret) < 32:
            raise ValueError("Agent capability secret must contain at least 32 characters")
        self._secret = secret.encode("utf-8")
        self._clock_skew = clock_skew_seconds
        self._max_nonces = max_nonces
        self._nonces: OrderedDict[str, int] = OrderedDict()
        self._lock = Lock()

    def verify(self, token: str) -> ExecutionCapability:
        try:
            payload_part, signature_part = token.split(".", 1)
            expected = hmac.new(self._secret, payload_part.encode("ascii"), hashlib.sha256).digest()
            supplied = _decode(signature_part)
            if not hmac.compare_digest(expected, supplied):
                raise ValueError("signature")
            raw = json.loads(_decode(payload_part))
            capability = ExecutionCapability.model_validate(raw)
            now = int(time.time())
            if capability.iat > now + self._clock_skew or capability.exp < now - self._clock_skew:
                raise ValueError("time")
            if capability.exp - capability.iat < 30 or capability.exp - capability.iat > 900:
                raise ValueError("ttl")
            if capability.tenant_id != "default":
                raise ValueError("tenant")
            for value in (
                capability.principal_id,
                capability.run_id,
                capability.agent_version_id,
                capability.nonce,
            ):
                if str(UUID(value)) != value:
                    raise ValueError("identity")
            self._consume(capability.nonce, capability.exp, now)
            return capability
        except (UnicodeError, ValueError, TypeError, json.JSONDecodeError, ValidationError) as error:
            raise CapabilityError("AGENT2-P-001", "Invalid or expired Agent capability", 401) from error

    def _consume(self, nonce: str, expires_at: int, now: int) -> None:
        with self._lock:
            while self._nonces:
                first_nonce, first_expiry = next(iter(self._nonces.items()))
                if first_expiry >= now and len(self._nonces) < self._max_nonces:
                    break
                self._nonces.pop(first_nonce)
            if nonce in self._nonces:
                raise ValueError("replay")
            self._nonces[nonce] = expires_at


def _decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
