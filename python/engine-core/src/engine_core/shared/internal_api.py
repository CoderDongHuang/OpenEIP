"""Authentication, identity, request ID, and bounded-body helpers for internal APIs."""

import hmac
import re
from collections.abc import AsyncIterator
from uuid import UUID

from fastapi import Request

from engine_core.shared.api_error import EngineApiError

_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def authenticate_internal(expected: str, supplied: str, prefix: str, error_type: type[EngineApiError]) -> None:
    """Fail closed and compare a configured internal credential in constant time."""
    if not expected:
        raise error_type(f"{prefix}-S-002", "Internal authentication is not configured", 503)
    if not supplied or not hmac.compare_digest(expected, supplied):
        raise error_type(f"{prefix}-P-001", "Invalid internal credential", 401)


def validate_identity(value: str, name: str, prefix: str, error_type: type[EngineApiError]) -> str:
    """Validate and return a canonical non-nil UUID string."""
    try:
        parsed = UUID(value)
    except ValueError as error:
        raise error_type(f"{prefix}-V-008", f"Invalid {name} identity", 400) from error
    if parsed.int == 0 or str(parsed) != value:
        raise error_type(f"{prefix}-V-008", f"Invalid {name} identity", 400)
    return value


def validate_request_id(value: str, prefix: str, error_type: type[EngineApiError]) -> str:
    """Validate and return a request ID safe for logs and response envelopes."""
    if not _REQUEST_ID_PATTERN.fullmatch(value):
        raise error_type(f"{prefix}-V-009", "Invalid request ID", 400)
    return value


def safe_request_id(value: str) -> str:
    """Return only a bounded request ID suitable for an error response."""
    return value if _REQUEST_ID_PATTERN.fullmatch(value) else "unknown"


async def read_bounded_body(
    request: Request, max_body_bytes: int, prefix: str, error_type: type[EngineApiError]
) -> bytes:
    """Read a request stream with both declared and observed byte limits."""
    content_length = request.headers.get("content-length")
    if content_length:
        try:
            parsed_length = int(content_length)
            if parsed_length < 0:
                raise ValueError
            if parsed_length > max_body_bytes:
                raise error_type(f"{prefix}-V-002", "Request body exceeds the configured limit", 413)
        except ValueError as error:
            raise error_type(f"{prefix}-V-001", "Invalid Content-Length header", 400) from error

    body = bytearray()
    async for chunk in _request_chunks(request):
        body.extend(chunk)
        if len(body) > max_body_bytes:
            raise error_type(f"{prefix}-V-002", "Request body exceeds the configured limit", 413)
    return bytes(body)


async def _request_chunks(request: Request) -> AsyncIterator[bytes]:
    async for chunk in request.stream():
        yield chunk
