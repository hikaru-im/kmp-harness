#!/usr/bin/env python3
"""Measure Settings General screenshots without resampling or color conversion.

Coordinate systems:

* screen: the supplied Codex screenshot. It includes a 30px macOS menu bar and the window
  begins at screen y=30. Its in-window coordinates are therefore ``(x, y - 30)``.
* native-window: a ``screencapture -l`` window image. Its origin is the window's top-left and
  it has no menu bar. To compare it with the supplied screen image, add 30px to native y.
* offscreen: ScreenshotTest's Compose image. It uses the same window-frame origin as a native
  window capture, but has no OS chrome. Its coordinates are compared to native-window directly.

The script intentionally reports raw PIL pixels. It does not apply an ICC profile, translate a
render, or resample an image. The screen-to-window comparison is an explicit ``y - 30`` transform.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable

from PIL import Image


REFERENCE_SCREEN_Y_OFFSET = 30
CONTENT_WIDTH = 728


def load(path: Path) -> Image.Image:
    return Image.open(path).convert("RGB")


def dark_bands(image: Image.Image, x0: int, x1: int, y0: int, y1: int) -> list[tuple[int, int]]:
    pixels = image.load()
    rows = []
    for y in range(y0, min(y1, image.height)):
        count = sum(1 for x in range(x0, min(x1, image.width)) if max(pixels[x, y]) < 120)
        if count >= 2:
            rows.append(y)
    return contiguous(rows)


def dense_rows(image: Image.Image, x0: int, x1: int, y0: int, y1: int) -> list[tuple[int, int]]:
    pixels = image.load()
    rows = []
    for y in range(y0, min(y1, image.height)):
        count = sum(1 for x in range(x0, min(x1, image.width)) if max(pixels[x, y]) < 250)
        if count >= 600:
            rows.append(y)
    return contiguous(rows)


def contiguous(values: Iterable[int]) -> list[tuple[int, int]]:
    values = list(values)
    if not values:
        return []
    result = []
    start = previous = values[0]
    for value in values[1:]:
        if value != previous + 1:
            result.append((start, previous))
            start = value
        previous = value
    result.append((start, previous))
    return result


def neutral_edges(image: Image.Image, y: int, x0: int, x1: int) -> tuple[int | None, int | None]:
    pixels = image.load()
    candidates = []
    for x in range(x0, min(x1, image.width)):
        r, g, b = pixels[x, y]
        if 180 <= r < 250 and max(r, g, b) - min(r, g, b) <= 2:
            candidates.append(x)
    return (min(candidates), max(candidates)) if candidates else (None, None)


def bbox_dark(image: Image.Image, box: tuple[int, int, int, int]) -> tuple[int, int, int, int] | None:
    pixels = image.load()
    x0, y0, x1, y1 = box
    points = []
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            if max(pixels[x, y]) < 120:
                points.append((x, y))
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return min(xs), min(ys), max(xs), max(ys)


def blue_components(image: Image.Image, box: tuple[int, int, int, int]) -> list[tuple[int, int, int, int]]:
    pixels = image.load()
    x0, y0, x1, y1 = box
    mask = set()
    for y in range(max(0, y0), min(image.height, y1)):
        for x in range(max(0, x0), min(image.width, x1)):
            r, g, b = pixels[x, y]
            if b > 130 and b - r > 35 and b - g > 10:
                mask.add((x, y))
    components = []
    while mask:
        seed = mask.pop()
        stack = [seed]
        points = [seed]
        while stack:
            x, y = stack.pop()
            for neighbour in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbour in mask:
                    mask.remove(neighbour)
                    stack.append(neighbour)
                    points.append(neighbour)
        if len(points) >= 8:
            xs = [point[0] for point in points]
            ys = [point[1] for point in points]
            components.append((min(xs), min(ys), max(xs), max(ys)))
    return sorted(components, key=lambda box: (box[1], box[0]))


def measure(path: Path, coordinate_system: str) -> dict:
    image = load(path)
    y_offset = -REFERENCE_SCREEN_Y_OFFSET if coordinate_system == "screen" else 0
    if image.width >= 1000:
        page_x0 = round((341 + image.width - 16 - CONTENT_WIDTH) / 2)
        page_x1 = page_x0 + CONTENT_WIDTH - 1
        page_x_range = (page_x0 - 40, page_x1 + 40)
    else:
        page_x0 = page_x1 = None
        page_x_range = (0, image.width)

    def source_y(y: int) -> int:
        return y - y_offset

    if page_x0 is not None:
        permission_lines = dense_rows(image, *page_x_range, source_y(150), source_y(450))
        preference_lines = dense_rows(image, *page_x_range, source_y(450), source_y(1350))
        title = dark_bands(image, *page_x_range, source_y(90), source_y(180))
        permission_heading = dark_bands(image, *page_x_range, source_y(180), source_y(240))
        general_heading = dark_bands(image, *page_x_range, source_y(430), source_y(520))
        first_permission = bbox_dark(image, (page_x0 + 15, source_y(240), page_x1 - 80, source_y(320)))
        second_permission = bbox_dark(image, (page_x0 + 15, source_y(320), page_x1 - 80, source_y(410)))
        controls = blue_components(image, (page_x0, source_y(200), page_x1, source_y(1350)))
    else:
        permission_lines = preference_lines = []
        title = permission_heading = general_heading = []
        first_permission = second_permission = None
        controls = []

    def shift_band(band: tuple[int, int]) -> tuple[int, int]:
        return band[0] + y_offset, band[1] + y_offset

    def shift_bbox(box: tuple[int, int, int, int] | None) -> tuple[int, int, int, int] | None:
        return None if box is None else (box[0], box[1] + y_offset, box[2], box[3] + y_offset)

    return {
        "path": str(path),
        "coordinate_system": coordinate_system,
        "size": list(image.size),
        "screen_to_window_y_offset": REFERENCE_SCREEN_Y_OFFSET if coordinate_system == "screen" else 0,
        "rail_nav_main_samples": {
            "window_row_700": [image.getpixel((x, min(source_y(700), image.height - 1))) for x in (50, 51, 52, 339, 340, 341) if x < image.width],
        },
        "permission_card_edge_rows": [shift_band(band) for band in permission_lines],
        "preference_card_edge_rows": [shift_band(band) for band in preference_lines],
        "edges_at_mid_permission": neutral_edges(image, source_y(314), page_x0 - 40, page_x1 + 40) if page_x0 is not None else (None, None),
        "edges_at_mid_preferences": neutral_edges(image, source_y(600), page_x0 - 40, page_x1 + 40) if page_x0 is not None else (None, None),
        "text_bands": {
            "page_title": [shift_band(band) for band in title],
            "permission_heading": [shift_band(band) for band in permission_heading],
            "general_heading": [shift_band(band) for band in general_heading],
        },
        "permission_text_bboxes": {
            "first": shift_bbox(first_permission),
            "second": shift_bbox(second_permission),
        },
        "blue_control_bboxes": [shift_bbox(box) for box in controls],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--current", type=Path, action="append", required=True)
    args = parser.parse_args()
    results = [measure(args.reference, "screen")]
    results.extend(measure(path, "offscreen-window") for path in args.current)
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
