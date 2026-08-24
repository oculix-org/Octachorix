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
 * Page segmentation mode passed to Tesseract before recognition.
 *
 * <p>Values mirror {@code TessPageSegMode} from Tesseract's public C API
 * header {@code capi.h}. Callers migrating from tess4j who used to set
 * a magic {@code int} (e.g. {@code setPageSegMode(11)}) will find the
 * same numerical mapping preserved — the enum only adds names.
 *
 * <p>Default in Tesseract is {@link #AUTO} (3).
 */
public enum PageSegMode {

    /** Orientation and script detection only, no recognition. */
    OSD_ONLY(0),

    /** Automatic page segmentation with OSD. */
    AUTO_OSD(1),

    /** Automatic page segmentation, no OSD, no OCR (rare). */
    AUTO_ONLY(2),

    /** Fully automatic page segmentation, no OSD. This is Tesseract's default. */
    AUTO(3),

    /** Assume a single column of text of variable sizes. */
    SINGLE_COLUMN(4),

    /** Assume a single uniform block of vertically aligned text. */
    SINGLE_BLOCK_VERT_TEXT(5),

    /** Assume a single uniform block of text. */
    SINGLE_BLOCK(6),

    /** Treat the image as a single text line. */
    SINGLE_LINE(7),

    /** Treat the image as a single word. */
    SINGLE_WORD(8),

    /** Treat the image as a single word in a circle. */
    CIRCLE_WORD(9),

    /** Treat the image as a single character. */
    SINGLE_CHAR(10),

    /** Sparse text: find as much text as possible in no particular order. */
    SPARSE_TEXT(11),

    /** Sparse text with orientation and script detection. */
    SPARSE_TEXT_OSD(12),

    /** Raw line — treat as a single text line, bypassing hacks specific to Tesseract. */
    RAW_LINE(13);

    private final int value;

    PageSegMode(int value) {
        this.value = value;
    }

    /** Returns the C enum value expected by {@code TessBaseAPISetPageSegMode}. */
    public int value() {
        return value;
    }
}
