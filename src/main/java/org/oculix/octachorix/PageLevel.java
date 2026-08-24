/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

/**
 * Segmentation level at which a piece of recognized text was extracted.
 *
 * <p>Values mirror {@code TessPageIteratorLevel} from Tesseract's public
 * C API header {@code capi.h}. A single recognition pass populates every
 * level; use {@link Reading#elements(PageLevel)} to filter down to the
 * granularity you actually need.
 *
 * <p>Ordered from coarsest ({@link #BLOCK}) to finest ({@link #SYMBOL}).
 * A {@link #BLOCK} contains {@link #PARAGRAPH}s, each paragraph contains
 * {@link #TEXTLINE}s, each text line contains {@link #WORD}s, each word
 * contains {@link #SYMBOL}s (single characters).
 */
public enum PageLevel {

    /** A logical page block: a paragraph group, an image caption, a column. */
    BLOCK(0),

    /** A paragraph inside a block. */
    PARAGRAPH(1),

    /** A single line of text inside a paragraph. */
    TEXTLINE(2),

    /** A whitespace-delimited word inside a text line. */
    WORD(3),

    /** A single character (Unicode code point) inside a word. */
    SYMBOL(4);

    private final int value;

    PageLevel(int value) {
        this.value = value;
    }

    /**
     * Returns the C enum value expected by
     * {@code TessResultIterator*} and {@code TessPageIterator*}
     * functions in Tesseract's C API.
     */
    public int value() {
        return value;
    }
}
