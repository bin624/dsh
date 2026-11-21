#!/usr/bin/env python3
"""Remove the baked checkerboard from the animated whale-girl GIF for Android."""
from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image


def remove_background(frame: Image.Image) -> Image.Image:
    rgb = np.asarray(frame.convert("RGB"))
    max_c = rgb.max(axis=2).astype(np.int16)
    min_c = rgb.min(axis=2).astype(np.int16)
    mean_c = rgb.mean(axis=2)
    chroma = max_c - min_c

    # Strong blue/navy/skin details identify the character. The wider probable-
    # foreground halo helps GrabCut retain white fabric next to dark outlines.
    sure_fg = ((chroma > 24) & (mean_c < 245)) | (mean_c < 180)
    near_fg = cv2.dilate(
        sure_fg.astype(np.uint8),
        cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (121, 121)),
    ) > 0
    likely_bg = (chroma < 12) & (mean_c > 215)

    labels = np.full(rgb.shape[:2], cv2.GC_PR_BGD, dtype=np.uint8)
    labels[near_fg] = cv2.GC_PR_FGD
    labels[sure_fg] = cv2.GC_FGD
    labels[likely_bg & ~near_fg] = cv2.GC_BGD

    bg_model = np.zeros((1, 65), dtype=np.float64)
    fg_model = np.zeros((1, 65), dtype=np.float64)
    cv2.grabCut(rgb, labels, None, bg_model, fg_model, 6, cv2.GC_INIT_WITH_MASK)

    foreground = np.isin(labels, (cv2.GC_FGD, cv2.GC_PR_FGD))
    # The GIF's bright apron is connected to pale background tones in parts of
    # the source. Protect its inner white panel so the checkerboard removal
    # cannot punch holes through the whale emblem.
    apron = np.zeros(rgb.shape[:2], dtype=np.uint8)
    cv2.ellipse(apron, (253, 386), (72, 50), 0, 0, 360, 1, -1)
    foreground |= apron.astype(bool)

    count, components, stats, _ = cv2.connectedComponentsWithStats(
        foreground.astype(np.uint8), connectivity=8
    )
    clean = np.zeros_like(foreground)
    for component in range(1, count):
        if stats[component, cv2.CC_STAT_AREA] >= 8:
            clean[components == component] = True

    alpha = cv2.GaussianBlur((clean * 255).astype(np.uint8), (3, 3), 0.55)
    return Image.fromarray(np.dstack((rgb, alpha)), "RGBA")


def to_gif_palette(frame: Image.Image) -> Image.Image:
    rgba = frame.convert("RGBA")
    alpha = rgba.getchannel("A")
    indexed = rgba.convert("RGB").quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    palette = indexed.getpalette() or []
    indexed.putpalette((palette + [0] * 768)[:768])
    # Reserve palette index 255 for transparent pixels.
    indexed.paste(255, mask=alpha.point(lambda value: 255 if value < 128 else 0))
    return indexed


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: prepare_whale_gif.py INPUT.gif OUTPUT.gif")
    source, target = map(Path, sys.argv[1:])
    animated = Image.open(source)
    durations: list[int] = []
    processed: list[Image.Image] = []
    for frame_index in range(getattr(animated, "n_frames", 1)):
        animated.seek(frame_index)
        durations.append(int(animated.info.get("duration", 220)))
        processed.append(to_gif_palette(remove_background(animated.copy())))

    target.parent.mkdir(parents=True, exist_ok=True)
    processed[0].save(
        target,
        format="GIF",
        save_all=True,
        append_images=processed[1:],
        duration=durations,
        loop=0,
        transparency=255,
        disposal=2,
        optimize=False,
    )
    print(f"Prepared {len(processed)} transparent GIF frames: {target}")


if __name__ == "__main__":
    main()
