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
 * OCR engine mode selecting which recognition backend Tesseract uses.
 *
 * <p>Values mirror {@code TessOcrEngineMode} from Tesseract's public C
 * API header {@code capi.h}.
 *
 * <ul>
 *   <li>{@link #TESSERACT_ONLY} — the legacy pre-4.0 engine.
 *       Only available if the {@code .traineddata} contains legacy
 *       models. Most modern language packs do not.</li>
 *   <li>{@link #LSTM_ONLY} — the neural engine (LSTM), default since
 *       Tesseract 4.0. This is what you almost always want.</li>
 *   <li>{@link #TESSERACT_LSTM_COMBINED} — both engines, results
 *       merged. Slower and rarely worth it.</li>
 *   <li>{@link #DEFAULT} — let Tesseract pick based on what the
 *       {@code .traineddata} advertises.</li>
 * </ul>
 */
public enum OcrEngineMode {

    TESSERACT_ONLY(0),
    LSTM_ONLY(1),
    TESSERACT_LSTM_COMBINED(2),
    DEFAULT(3);

    private final int value;

    OcrEngineMode(int value) {
        this.value = value;
    }

    /** Returns the C enum value expected by {@code TessBaseAPIInit2}. */
    public int value() {
        return value;
    }
}
