"""Pydantic models shared by the extractor, API, and storage layers."""

from typing import List, Literal, Optional

from pydantic import BaseModel


class ExtractedItem(BaseModel):
    """One calendar event or to-do task pulled out of a text or screenshot."""

    kind: Literal["event", "task"]
    title: str
    # Events: local ISO 8601 with no timezone suffix, e.g. "2026-06-14T15:30",
    # or a bare date "2026-06-14" when all_day is true.
    start: Optional[str] = None
    end: Optional[str] = None
    all_day: bool = False
    # Tasks: optional deadline, same format as start.
    due: Optional[str] = None
    location: Optional[str] = None
    with_people: List[str] = []
    notes: Optional[str] = None
    confidence: Literal["high", "medium", "low"]
    source_quote: Optional[str] = None


class ExtractionResult(BaseModel):
    items: List[ExtractedItem]
    summary: str


class StoredItem(ExtractedItem):
    id: int
    done: bool = False
    created_at: Optional[str] = None


class SaveItemsRequest(BaseModel):
    items: List[ExtractedItem]


class ItemUpdate(BaseModel):
    """Partial update; only provided fields are changed."""

    kind: Optional[Literal["event", "task"]] = None
    title: Optional[str] = None
    start: Optional[str] = None
    end: Optional[str] = None
    all_day: Optional[bool] = None
    due: Optional[str] = None
    location: Optional[str] = None
    with_people: Optional[List[str]] = None
    notes: Optional[str] = None
    done: Optional[bool] = None
