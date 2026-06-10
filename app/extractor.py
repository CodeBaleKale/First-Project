"""Turn raw text and screenshots into structured events/tasks via the Claude API."""

import base64
from typing import Iterable, Optional, Sequence, Tuple

import anthropic

from .config import get_model, now_context
from .models import ExtractionResult

ALLOWED_IMAGE_TYPES = {"image/png", "image/jpeg", "image/gif", "image/webp"}
MAX_IMAGE_BYTES = 8 * 1024 * 1024
MAX_IMAGES = 6

SYSTEM_PROMPT = """\
You are the extraction engine for SnapCal, a calendar and to-do app. You receive raw text \
(SMS/chat messages, emails, notes) and/or screenshots (of conversations, flyers, emails, \
booking confirmations). Extract every actionable calendar event and to-do task for the user.

Rules:
- Use the provided current date, time, and timezone to resolve relative dates ("tomorrow", \
"next Friday", "this weekend", "in two weeks"). An upcoming commitment must never resolve to \
a past date; if a named weekday already passed this week, use its next occurrence.
- kind="event" for anything happening at a specific date (appointments, meetings, dinners, \
flights, games, parties). kind="task" for things to do with no fixed occurrence time \
(errands, "don't forget to...", homework, bills) — set "due" when a deadline is stated or \
clearly implied.
- Dates/times: local ISO 8601 with no timezone suffix, e.g. "2026-06-14T15:30". If only a \
date is known, set all_day=true and use the bare date "2026-06-14". Leave end null unless an \
end time or duration is stated.
- title: short and specific, written from the user's perspective ("Dinner with Sam", \
"Dentist appointment", "Pick up dry cleaning").
- location only if stated or unambiguous. with_people: names of the other participants.
- source_quote: the exact words that triggered the item, kept short.
- confidence: "high" = explicit date and time; "medium" = some inference was needed; \
"low" = vague intent ("we should hang out soon") — skip these entirely unless the intent to \
schedule is clear.
- Do NOT invent details. Skip pleasantries, past events, and declined or cancelled plans. If \
a plan is rescheduled within the conversation, emit one item with the final time and mention \
the change in notes.
- Screenshots: read all visible text. In chat screenshots, right-aligned bubbles are usually \
the user ("me"); infer who is committing to what.
- summary: one short sentence describing what was found. If nothing is actionable, return an \
empty items list and say so in summary.
"""


def validate_image(media_type: str, data: bytes) -> None:
    if media_type not in ALLOWED_IMAGE_TYPES:
        raise ValueError(
            f"Unsupported image type {media_type!r}; expected one of {sorted(ALLOWED_IMAGE_TYPES)}"
        )
    if len(data) > MAX_IMAGE_BYTES:
        raise ValueError(f"Image exceeds {MAX_IMAGE_BYTES // (1024 * 1024)}MB limit")


def build_user_content(
    text: Optional[str],
    images: Sequence[Tuple[str, bytes]],
    now_str: str,
    tz_name: str,
) -> list:
    """Compose the user message: screenshots first, then the dated instruction block."""
    content: list = []
    for media_type, data in images:
        validate_image(media_type, data)
        content.append(
            {
                "type": "image",
                "source": {
                    "type": "base64",
                    "media_type": media_type,
                    "data": base64.standard_b64encode(data).decode("utf-8"),
                },
            }
        )

    parts = [f"Current date and time: {now_str} (timezone: {tz_name})."]
    if images:
        parts.append(
            f"Extract events and tasks from the {len(images)} screenshot(s) above."
        )
    if text:
        parts.append("Extract events and tasks from this text:\n\n" + text)
    content.append({"type": "text", "text": "\n\n".join(parts)})
    return content


def extract_items(
    text: Optional[str] = None,
    images: Iterable[Tuple[str, bytes]] = (),
    client: Optional[anthropic.Anthropic] = None,
    model: Optional[str] = None,
) -> ExtractionResult:
    """Run one extraction call. `images` is an iterable of (media_type, raw bytes)."""
    images = list(images)
    if not text and not images:
        raise ValueError("Provide text, at least one screenshot, or both")

    client = client or anthropic.Anthropic()
    now_str, tz_name = now_context()

    response = client.messages.parse(
        model=model or get_model(),
        max_tokens=16000,
        thinking={"type": "adaptive"},
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": build_user_content(text, images, now_str, tz_name)}],
        output_format=ExtractionResult,
    )
    return response.parsed_output
