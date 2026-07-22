"""Stable internal API error envelope."""

from datetime import UTC, datetime


class EngineApiError(Exception):
    """Expected engine failure mapped to a stable public envelope."""

    def __init__(self, code: str, message: str, status_code: int) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code

    def envelope(self, request_id: str) -> dict[str, object]:
        """Build the standard error response without internal exception details."""
        return {
            "code": self.code,
            "message": self.message,
            "data": None,
            "requestId": request_id,
            "timestamp": datetime.now(UTC).isoformat(),
        }
