from app.ics import escape_text, fold_line, items_to_ics
from app.models import StoredItem


def make_item(**overrides) -> StoredItem:
    base = dict(
        id=1,
        kind="event",
        title="Dinner with Sam",
        start="2026-06-14T19:00",
        end=None,
        all_day=False,
        due=None,
        location=None,
        with_people=[],
        notes=None,
        confidence="high",
        source_quote=None,
        done=False,
    )
    base.update(overrides)
    return StoredItem(**base)


def test_escape_text():
    assert escape_text("a,b;c\nd\\e") == "a\\,b\\;c\\nd\\\\e"


def test_fold_line_short_passthrough():
    assert fold_line("SUMMARY:hi") == "SUMMARY:hi"


def test_fold_line_long_is_folded():
    folded = fold_line("DESCRIPTION:" + "x" * 200)
    parts = folded.split("\r\n ")
    assert len(parts) > 1
    assert all(len(p.encode()) <= 75 for p in parts)


def test_timed_event():
    ics = items_to_ics([make_item()])
    assert "BEGIN:VCALENDAR" in ics
    assert "BEGIN:VEVENT" in ics
    assert "DTSTART:20260614T190000" in ics
    # No explicit end -> defaults to a one-hour block
    assert "DTEND:20260614T200000" in ics
    assert "SUMMARY:Dinner with Sam" in ics


def test_all_day_event():
    ics = items_to_ics([make_item(start="2026-06-14", all_day=True)])
    assert "DTSTART;VALUE=DATE:20260614" in ics
    assert "DTEND" not in ics


def test_task_becomes_vtodo():
    ics = items_to_ics(
        [make_item(kind="task", title="Buy gift", start=None, due="2026-06-13")]
    )
    assert "BEGIN:VTODO" in ics
    assert "DUE;VALUE=DATE:20260613" in ics
    assert "STATUS:NEEDS-ACTION" in ics


def test_location_and_people_in_description():
    ics = items_to_ics(
        [make_item(location="Luigi's, Downtown", with_people=["Sam"], notes="moved from Thu")]
    )
    assert "LOCATION:Luigi's\\, Downtown" in ics
    assert "DESCRIPTION:moved from Thu | With: Sam" in ics
