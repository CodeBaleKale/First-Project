"""Minimal RFC 5545 (.ics) export — events as VEVENT, tasks as VTODO."""

from datetime import datetime, timedelta, timezone
from typing import List

from .models import StoredItem

PRODID = "-//SnapCal//SnapCal 0.1//EN"
UID_DOMAIN = "snapcal.local"


def escape_text(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
    )


def fold_line(line: str) -> str:
    """Fold content lines longer than 75 octets per RFC 5545 §3.1."""
    encoded = line.encode("utf-8")
    if len(encoded) <= 75:
        return line
    chunks = []
    while encoded:
        # Take up to 75 bytes; back up if the cut would split a UTF-8 sequence
        # (the byte after the cut would be a continuation byte).
        cut = min(75, len(encoded))
        while 1 < cut < len(encoded) and (encoded[cut] & 0xC0) == 0x80:
            cut -= 1
        chunks.append(encoded[:cut].decode("utf-8"))
        encoded = encoded[cut:]
    return "\r\n ".join(chunks)


def _format_dt(value: str) -> str:
    dt = datetime.fromisoformat(value)
    return dt.strftime("%Y%m%dT%H%M%S")


def _format_date(value: str) -> str:
    return datetime.fromisoformat(value).strftime("%Y%m%d")


def _dtstamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _common_lines(item: StoredItem) -> List[str]:
    lines = [f"UID:snapcal-{item.id}@{UID_DOMAIN}", f"DTSTAMP:{_dtstamp()}"]
    lines.append(f"SUMMARY:{escape_text(item.title)}")
    if item.location:
        lines.append(f"LOCATION:{escape_text(item.location)}")
    description = []
    if item.notes:
        description.append(item.notes)
    if item.with_people:
        description.append("With: " + ", ".join(item.with_people))
    if description:
        lines.append(f"DESCRIPTION:{escape_text(' | '.join(description))}")
    return lines


def event_to_vevent(item: StoredItem) -> List[str]:
    lines = ["BEGIN:VEVENT", *_common_lines(item)]
    if item.start:
        if item.all_day:
            lines.append(f"DTSTART;VALUE=DATE:{_format_date(item.start)}")
        else:
            lines.append(f"DTSTART:{_format_dt(item.start)}")
            if item.end:
                lines.append(f"DTEND:{_format_dt(item.end)}")
            else:
                # Default to a one-hour block so importers render something useful.
                end = datetime.fromisoformat(item.start) + timedelta(hours=1)
                lines.append(f"DTEND:{end.strftime('%Y%m%dT%H%M%S')}")
    lines.append("END:VEVENT")
    return lines


def task_to_vtodo(item: StoredItem) -> List[str]:
    lines = ["BEGIN:VTODO", *_common_lines(item)]
    if item.due:
        if "T" in item.due:
            lines.append(f"DUE:{_format_dt(item.due)}")
        else:
            lines.append(f"DUE;VALUE=DATE:{_format_date(item.due)}")
    lines.append(f"STATUS:{'COMPLETED' if item.done else 'NEEDS-ACTION'}")
    lines.append("END:VTODO")
    return lines


def items_to_ics(items: List[StoredItem]) -> str:
    lines = ["BEGIN:VCALENDAR", "VERSION:2.0", f"PRODID:{PRODID}", "CALSCALE:GREGORIAN"]
    for item in items:
        if item.kind == "event":
            lines.extend(event_to_vevent(item))
        else:
            lines.extend(task_to_vtodo(item))
    lines.append("END:VCALENDAR")
    return "\r\n".join(fold_line(line) for line in lines) + "\r\n"
