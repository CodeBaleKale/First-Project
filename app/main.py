"""SnapCal API — paste texts / upload screenshots, review, and manage the calendar."""

from pathlib import Path
from typing import List, Optional

import anthropic
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, Response
from fastapi.staticfiles import StaticFiles

from . import db
from .config import api_key_configured, get_model
from .extractor import MAX_IMAGES, extract_items
from .models import ExtractionResult, ItemUpdate, SaveItemsRequest, StoredItem

app = FastAPI(title="SnapCal", version="0.1.0")

STATIC_DIR = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


@app.get("/", include_in_schema=False)
def index() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/api/health")
def health() -> dict:
    return {"ok": True, "api_key_configured": api_key_configured(), "model": get_model()}


@app.post("/api/extract", response_model=ExtractionResult)
async def extract(
    text: Optional[str] = Form(None),
    images: List[UploadFile] = File([]),
) -> ExtractionResult:
    text = (text or "").strip() or None
    if not text and not images:
        raise HTTPException(400, "Provide text, at least one screenshot, or both")
    if len(images) > MAX_IMAGES:
        raise HTTPException(400, f"At most {MAX_IMAGES} screenshots per request")
    if not api_key_configured():
        raise HTTPException(503, "ANTHROPIC_API_KEY is not configured on the server")

    image_payloads = []
    for upload in images:
        data = await upload.read()
        image_payloads.append((upload.content_type or "application/octet-stream", data))

    try:
        return extract_items(text=text, images=image_payloads)
    except ValueError as exc:
        raise HTTPException(400, str(exc))
    except anthropic.AuthenticationError:
        raise HTTPException(503, "Claude API rejected the configured ANTHROPIC_API_KEY")
    except anthropic.RateLimitError:
        raise HTTPException(429, "Claude API rate limit hit — try again shortly")
    except anthropic.APIStatusError as exc:
        raise HTTPException(502, f"Claude API error ({exc.status_code}): {exc.message}")
    except anthropic.APIConnectionError:
        raise HTTPException(502, "Could not reach the Claude API")


@app.post("/api/items", response_model=List[StoredItem])
def save_items(request: SaveItemsRequest) -> List[StoredItem]:
    if not request.items:
        raise HTTPException(400, "No items to save")
    return db.add_items(request.items)


@app.get("/api/items", response_model=List[StoredItem])
def list_items(kind: Optional[str] = None) -> List[StoredItem]:
    if kind not in (None, "event", "task"):
        raise HTTPException(400, "kind must be 'event' or 'task'")
    return db.list_items(kind=kind)


@app.patch("/api/items/{item_id}", response_model=StoredItem)
def update_item(item_id: int, update: ItemUpdate) -> StoredItem:
    item = db.update_item(item_id, update)
    if item is None:
        raise HTTPException(404, "Item not found")
    return item


@app.delete("/api/items/{item_id}", status_code=204)
def delete_item(item_id: int) -> Response:
    if not db.delete_item(item_id):
        raise HTTPException(404, "Item not found")
    return Response(status_code=204)


def _ics_response(content: str, filename: str) -> Response:
    return Response(
        content=content,
        media_type="text/calendar",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@app.get("/api/export.ics", include_in_schema=False)
def export_all() -> Response:
    from .ics import items_to_ics

    return _ics_response(items_to_ics(db.list_items()), "snapcal.ics")


@app.get("/api/items/{item_id}/export.ics", include_in_schema=False)
def export_one(item_id: int) -> Response:
    from .ics import items_to_ics

    item = db.get_item(item_id)
    if item is None:
        raise HTTPException(404, "Item not found")
    return _ics_response(items_to_ics([item]), f"snapcal-{item_id}.ics")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)
