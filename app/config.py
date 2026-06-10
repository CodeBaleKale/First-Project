"""Runtime configuration, read from environment variables."""

import os
from datetime import datetime
from zoneinfo import ZoneInfo

DEFAULT_MODEL = "claude-opus-4-8"


def get_model() -> str:
    return os.environ.get("SNAPCAL_MODEL", DEFAULT_MODEL)


def get_db_path() -> str:
    return os.environ.get("SNAPCAL_DB", "snapcal.db")


def api_key_configured() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def now_context() -> tuple[str, str]:
    """Return (human-readable current datetime, timezone name) for the extraction prompt.

    Honours SNAPCAL_TZ (an IANA name like "America/New_York"); otherwise uses
    the server's local timezone.
    """
    tz_name = os.environ.get("SNAPCAL_TZ")
    now = datetime.now(ZoneInfo(tz_name)) if tz_name else datetime.now().astimezone()
    return now.strftime("%A, %B %d, %Y at %H:%M"), str(now.tzinfo)
