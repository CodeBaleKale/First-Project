# SnapCal 📅

**Turn your texts and screenshots into calendar events and to-dos — automatically.**

Paste a text message ("dentist moved my cleaning to next thursday 2:30pm"), or drop in a
screenshot of a group chat, flyer, email, or booking confirmation. SnapCal reads it with
Claude's vision + structured-output APIs, figures out the real dates ("next Friday" → an
actual date), and proposes calendar events and tasks. You review, tweak, and confirm — then
view them on a built-in calendar, check off to-dos, and export `.ics` files that import
straight into Google Calendar, Apple Calendar, or Outlook.

## Features

- **Text → events/tasks** — paste any message, email, or note
- **Screenshots → events/tasks** — drag-drop or paste up to 6 images (chat threads, flyers,
  confirmations); Claude reads who's committing to what
- **Smart date resolution** — "tomorrow", "next Friday", "in two weeks" resolved against the
  current date and your timezone (`SNAPCAL_TZ`)
- **Review before saving** — every extracted item shows its confidence and the exact quote
  that triggered it; edit titles/dates or untick anything before it lands on your calendar
- **Built-in calendar & task list** — month view with day drill-down; tasks with due dates,
  overdue highlighting, and done-state
- **`.ics` export** — per-event or the whole calendar (events as `VEVENT`, tasks as `VTODO`),
  so everything flows into the calendar app you already use

## Quickstart

Requires Python 3.10+.

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

export ANTHROPIC_API_KEY=sk-ant-...   # or copy .env.example to .env and load it

uvicorn app.main:app --reload
```

Open <http://localhost:8000>, paste a text or drop a screenshot, and hit
**Extract events & tasks**.

## Android app 📱

A native Android client (Kotlin + Jetpack Compose) lives in [`android/`](android/) — the
same extraction brain, plus two things only a phone can do:

- **Share-to-SnapCal** — screenshot a conversation, hit share, pick SnapCal. Text and images
  shared from any app land straight in the Capture screen.
- **Straight into your real calendar** — extracted events open pre-filled in Google
  Calendar (or your default calendar app) via the system insert intent, no permissions needed.

It is fully standalone — no server required. Paste your Anthropic API key into the app's
Settings screen (stored in the app's private storage); confirmed items live on-device.

### Build & install

Open `android/` in Android Studio and press Run, or from the CLI:

```bash
cd android && ./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every CI run also uploads a ready-to-install debug APK as the **snapcal-debug-apk**
artifact (Actions → run → Artifacts).

## How it works

```
┌─────────────┐    multipart     ┌──────────────┐   vision + structured   ┌────────┐
│ paste text /│ ───────────────▶ │  FastAPI     │ ──────  outputs  ─────▶ │ Claude │
│ screenshots │                  │ /api/extract │ ◀── ExtractionResult ── │        │
└─────────────┘                  └──────┬───────┘                         └────────┘
                                        │ review & confirm in the UI
                                        ▼
                                 ┌──────────────┐        ┌───────────────────┐
                                 │   SQLite     │ ─────▶ │ calendar / tasks /│
                                 │   (items)    │        │   .ics export     │
                                 └──────────────┘        └───────────────────┘
```

The extraction call ([`app/extractor.py`](app/extractor.py)) sends your text and base64
screenshots to `claude-opus-4-8` with a Pydantic-backed structured output schema
(`client.messages.parse`), so the response is always valid, typed JSON — no regex parsing of
model output. The system prompt handles relative-date resolution, event-vs-task
classification, rescheduled/cancelled-plan handling, and a do-not-invent-details rule.

## Configuration

| Env var             | Default            | Purpose                                  |
| ------------------- | ------------------ | ---------------------------------------- |
| `ANTHROPIC_API_KEY` | — (required)       | Claude API key used for extraction       |
| `SNAPCAL_MODEL`     | `claude-opus-4-8`  | Claude model for extraction              |
| `SNAPCAL_DB`        | `snapcal.db`       | SQLite database path                     |
| `SNAPCAL_TZ`        | server local time  | IANA timezone for resolving "next Friday"|

## Getting events into your real calendar

- **One event**: click ⤓ on any event in the day panel → opens in your calendar app.
- **Everything**: **Export .ics** in the header, then import the file in Google Calendar
  (Settings → Import & export) or double-click it for Apple Calendar/Outlook.
- **Capture on the go (iOS/Android)**: screenshot a conversation → share/upload it to the
  Capture tab. An iOS Shortcut that POSTs the screenshot to `/api/extract` makes this one tap.

## API

| Method   | Path                         | Description                                  |
| -------- | ---------------------------- | -------------------------------------------- |
| `POST`   | `/api/extract`               | multipart `text` + `images[]` → proposed items |
| `POST`   | `/api/items`                 | save confirmed items                         |
| `GET`    | `/api/items?kind=event\|task`| list items                                   |
| `PATCH`  | `/api/items/{id}`            | edit fields / toggle `done`                  |
| `DELETE` | `/api/items/{id}`            | delete                                       |
| `GET`    | `/api/export.ics`            | full calendar export                         |
| `GET`    | `/api/items/{id}/export.ics` | single-item export                           |

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```

The test suite mocks the Claude client — no API key or network needed.

## Privacy note

Whatever you paste or upload is sent to the Claude API for extraction. Don't feed it
anything you wouldn't send to a cloud service, and review Anthropic's data-usage policies
for your account tier.

## Roadmap

- Direct Google Calendar sync (OAuth) instead of `.ics` import
- Gmail/IMAP ingestion ("scan my inbox for events")
- iOS Shortcut / Android share-target recipes for one-tap screenshot capture
- Recurring events and reminders
- Duplicate detection when the same plan is mentioned twice
