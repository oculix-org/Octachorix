/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

import java.awt.Rectangle;
import java.util.Objects;

/**
 * One element of a {@link Reading} — a text region at a given
 * segmentation {@link PageLevel}, its recognized text, its confidence
 * score, and its axis-aligned bounding box in image coordinates.
 *
 * <p>A single {@link Reading} typically contains elements at every
 * {@link PageLevel}: one {@link PageLevel#BLOCK} whose text is the
 * concatenation of its paragraphs, each paragraph whose text is the
 * concatenation of its lines, and so on down to {@link PageLevel#SYMBOL}.
 * Filter to the granularity you need with
 * {@link Reading#elements(PageLevel)}.
 *
 * <p>Naming choice: {@code TextElement}, not {@code Word}. A
 * {@link PageLevel#BLOCK} carries multiple sentences and is not a
 * word by any stretch; a {@link PageLevel#SYMBOL} is a single
 * character and is not a word either. Calling every level "Word"
 * would be a semantic debt we chose not to take on day zero.
 *
 * @param text recognized text; never null but may be empty when
 *             Tesseract confidently found "nothing" at that region
 * @param confidence in the {@code 0.0 .. 100.0} range Tesseract
 *                   reports (not normalized to {@code 0..1})
 * @param bbox axis-aligned bounding box in image coordinates
 * @param level segmentation level at which this element was extracted
 */
public record TextElement(
        String text,
        float confidence,
        Rectangle bbox,
        PageLevel level
) {

    public TextElement {
        Objects.requireNonNull(text, "TextElement.text cannot be null");
        Objects.requireNonNull(bbox, "TextElement.bbox cannot be null");
        Objects.requireNonNull(level, "TextElement.level cannot be null");
    }
}
