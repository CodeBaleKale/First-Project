"""SQLite storage for confirmed events and tasks."""

import json
import sqlite3
from typing import List, Optional

from .config import get_db_path
from .models import ExtractedItem, ItemUpdate, StoredItem

SCHEMA = """
CREATE TABLE IF NOT EXISTS items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL CHECK (kind IN ('event', 'task')),
    title TEXT NOT NULL,
    start TEXT,
    end TEXT,
    all_day INTEGER NOT NULL DEFAULT 0,
    due TEXT,
    location TEXT,
    with_people TEXT NOT NULL DEFAULT '[]',
    notes TEXT,
    done INTEGER NOT NULL DEFAULT 0,
    confidence TEXT NOT NULL DEFAULT 'high',
    source_quote TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
"""


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(get_db_path())
    conn.row_factory = sqlite3.Row
    conn.execute(SCHEMA)
    return conn


def _row_to_item(row: sqlite3.Row) -> StoredItem:
    return StoredItem(
        id=row["id"],
        kind=row["kind"],
        title=row["title"],
        start=row["start"],
        end=row["end"],
        all_day=bool(row["all_day"]),
        due=row["due"],
        location=row["location"],
        with_people=json.loads(row["with_people"]),
        notes=row["notes"],
        done=bool(row["done"]),
        confidence=row["confidence"],
        source_quote=row["source_quote"],
        created_at=row["created_at"],
    )


def add_items(items: List[ExtractedItem]) -> List[StoredItem]:
    with connect() as conn:
        stored = []
        for item in items:
            cur = conn.execute(
                """INSERT INTO items
                   (kind, title, start, end, all_day, due, location, with_people,
                    notes, confidence, source_quote)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    item.kind,
                    item.title,
                    item.start,
                    item.end,
                    int(item.all_day),
                    item.due,
                    item.location,
                    json.dumps(item.with_people),
                    item.notes,
                    item.confidence,
                    item.source_quote,
                ),
            )
            stored.append(get_item(cur.lastrowid, conn))
        return stored


def get_item(item_id: int, conn: Optional[sqlite3.Connection] = None) -> Optional[StoredItem]:
    owned = conn is None
    conn = conn or connect()
    try:
        row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
        return _row_to_item(row) if row else None
    finally:
        if owned:
            conn.close()


def list_items(kind: Optional[str] = None) -> List[StoredItem]:
    with connect() as conn:
        query = "SELECT * FROM items"
        params: tuple = ()
        if kind:
            query += " WHERE kind = ?"
            params = (kind,)
        query += " ORDER BY COALESCE(start, due, created_at)"
        return [_row_to_item(r) for r in conn.execute(query, params).fetchall()]


def update_item(item_id: int, update: ItemUpdate) -> Optional[StoredItem]:
    fields = update.model_dump(exclude_unset=True)
    if not fields:
        return get_item(item_id)
    columns, values = [], []
    for key, value in fields.items():
        if key == "with_people":
            value = json.dumps(value)
        elif key in ("all_day", "done"):
            value = int(value)
        columns.append(f"{key} = ?")
        values.append(value)
    with connect() as conn:
        cur = conn.execute(
            f"UPDATE items SET {', '.join(columns)} WHERE id = ?", (*values, item_id)
        )
        if cur.rowcount == 0:
            return None
        return get_item(item_id, conn)


def delete_item(item_id: int) -> bool:
    with connect() as conn:
        return conn.execute("DELETE FROM items WHERE id = ?", (item_id,)).rowcount > 0
