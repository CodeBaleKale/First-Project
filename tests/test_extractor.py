import base64
from types import SimpleNamespace

import pytest

from app.extractor import build_user_content, extract_items, validate_image
from app.models import ExtractedItem, ExtractionResult

NOW = "Wednesday, June 10, 2026 at 09:30"
TZ = "America/New_York"
PNG = b"\x89PNG fake bytes"


def test_text_only_content():
    content = build_user_content("dinner friday 7pm", [], NOW, TZ)
    assert len(content) == 1
    assert content[0]["type"] == "text"
    assert NOW in content[0]["text"]
    assert TZ in content[0]["text"]
    assert "dinner friday 7pm" in content[0]["text"]


def test_images_come_before_instructions():
    content = build_user_content(None, [("image/png", PNG)], NOW, TZ)
    assert [b["type"] for b in content] == ["image", "text"]
    src = content[0]["source"]
    assert src["type"] == "base64"
    assert src["media_type"] == "image/png"
    assert base64.standard_b64decode(src["data"]) == PNG
    assert "1 screenshot(s)" in content[1]["text"]


def test_rejects_unsupported_image_type():
    with pytest.raises(ValueError, match="Unsupported image type"):
        validate_image("application/pdf", b"%PDF")


def test_rejects_oversized_image():
    with pytest.raises(ValueError, match="exceeds"):
        validate_image("image/png", b"x" * (8 * 1024 * 1024 + 1))


def test_requires_some_input():
    with pytest.raises(ValueError, match="Provide text"):
        extract_items()


class StubClient:
    """Captures the parse() kwargs and returns a canned result."""

    def __init__(self, result):
        captured = {}
        self.captured = captured

        class Messages:
            def parse(self, **kwargs):
                captured.update(kwargs)
                return SimpleNamespace(parsed_output=result)

        self.messages = Messages()


def test_extract_items_calls_parse_with_schema():
    canned = ExtractionResult(
        items=[ExtractedItem(kind="task", title="Buy gift", confidence="high")],
        summary="One task found.",
    )
    client = StubClient(canned)

    result = extract_items(text="buy a gift", client=client, model="test-model")

    assert result == canned
    assert client.captured["model"] == "test-model"
    assert client.captured["output_format"] is ExtractionResult
    assert client.captured["thinking"] == {"type": "adaptive"}
    assert client.captured["messages"][0]["role"] == "user"
