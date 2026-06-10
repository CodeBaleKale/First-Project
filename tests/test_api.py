import pytest
from fastapi.testclient import TestClient

import app.main as main
from app.models import ExtractedItem, ExtractionResult


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("SNAPCAL_DB", str(tmp_path / "test.db"))
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    return TestClient(main.app)


@pytest.fixture()
def fake_extraction(monkeypatch):
    result = ExtractionResult(
        items=[
            ExtractedItem(
                kind="event",
                title="Dinner with Sam",
                start="2026-06-14T19:00",
                confidence="high",
                source_quote="dinner sunday at 7?",
            ),
            ExtractedItem(kind="task", title="Buy gift", due="2026-06-13", confidence="medium"),
        ],
        summary="One event and one task.",
    )
    monkeypatch.setattr(main, "extract_items", lambda **kwargs: result)
    return result


def test_health(client):
    body = client.get("/api/health").json()
    assert body["ok"] is True
    assert body["api_key_configured"] is True


def test_extract_requires_input(client):
    assert client.post("/api/extract", data={}).status_code == 400


def test_extract_text(client, fake_extraction):
    res = client.post("/api/extract", data={"text": "dinner sunday at 7?"})
    assert res.status_code == 200
    body = res.json()
    assert len(body["items"]) == 2
    assert body["items"][0]["title"] == "Dinner with Sam"


def test_save_list_update_delete_roundtrip(client):
    items = {
        "items": [
            {"kind": "event", "title": "Dentist", "start": "2026-06-18T14:30", "confidence": "high"},
            {"kind": "task", "title": "Pay rent", "due": "2026-07-01", "confidence": "high"},
        ]
    }
    saved = client.post("/api/items", json=items).json()
    assert [s["id"] for s in saved] == [1, 2]

    events = client.get("/api/items", params={"kind": "event"}).json()
    assert len(events) == 1 and events[0]["title"] == "Dentist"

    updated = client.patch("/api/items/2", json={"done": True}).json()
    assert updated["done"] is True

    assert client.delete("/api/items/1").status_code == 204
    assert client.get("/api/items").json()[0]["id"] == 2
    assert client.patch("/api/items/999", json={"done": True}).status_code == 404


def test_export_ics(client):
    client.post(
        "/api/items",
        json={"items": [{"kind": "event", "title": "Game night", "start": "2026-06-20T18:00", "confidence": "high"}]},
    )
    res = client.get("/api/export.ics")
    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/calendar")
    assert "BEGIN:VCALENDAR" in res.text
    assert "SUMMARY:Game night" in res.text

    single = client.get("/api/items/1/export.ics")
    assert single.status_code == 200
    assert "Game night" in single.text


def test_bad_kind_filter(client):
    assert client.get("/api/items", params={"kind": "banana"}).status_code == 400
