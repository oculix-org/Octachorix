/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The public-facing session facade: a configured, reusable Tesseract
 * OCR session with an explicit lifecycle.
 *
 * <h2>What a {@code Scribe} is</h2>
 * <p>One {@code Scribe} owns one native {@code TessBaseAPI*} handle,
 * initialised once at {@link Builder#build()} time and destroyed once
 * at {@link #close()} time. Between the two, the session can be reused
 * for as many {@link #read(BufferedImage)} calls as you like — every
 * call reuses the same native handle and only pays for image
 * marshalling and one recognition pass.
 *
 * <h2>The single recognition pass</h2>
 * <p>Each {@link #read} call performs exactly one
 * {@code TessBaseAPIRecognize} on the native side. Everything the
 * returned {@link Reading} carries — full text, per-{@link PageLevel}
 * elements, bounding boxes, confidences — comes from that one pass.
 * Reading at a different {@link PageLevel} later, via
 * {@link Reading#elements(PageLevel)}, does not touch the native side.
 *
 * <h2>Multi-language via {@link #withLanguage}</h2>
 * <p>{@link #withLanguage(String)} returns a NEW {@code Scribe} that
 * shares the same underlying native libraries (JNA caches by path,
 * so no double load) but carries its own {@code TessBaseAPI*} handle
 * initialised on the new language. The original {@code Scribe}
 * remains fully usable in parallel; the two must be closed
 * independently.
 *
 * <h2>Thread-safety — read this before pooling</h2>
 * <p><b>A {@code Scribe} is NOT thread-safe.</b>
 *
 * <p>The final Java fields hide a mutable native state: the sequence
 * {@code TessBaseAPISetImage → TessBaseAPIRecognize → GetIterator}
 * mutates the handle at every step, and two threads sharing one
 * handle will race. For concurrent OCR, allocate one {@code Scribe}
 * per thread. The JNA cache makes the second allocation cheap: the
 * native libraries are loaded once and shared, only the
 * {@code TessBaseAPI*} handle is per-{@code Scribe}.
 *
 * <h2>Lifecycle</h2>
 * <p>{@code Scribe} implements {@link AutoCloseable}. Prefer
 * try-with-resources over manual {@link #close()}, and never rely on
 * the garbage collector to release the native handle — there is no
 * finaliser here on purpose.
 *
 * <pre>{@code
 * try (Scribe scribe = Scribe.builder()
 *         .tesseractLibrary(Path.of("/abs/libtesseract.so.5"))
 *         .leptonicaLibrary(Path.of("/abs/libleptonica.so.6"))
 *         .datapath(Path.of("/abs/tessdata"))
 *         .language("eng")
 *         .build()) {
 *     Reading r = scribe.read(myImage);
 *     String text = r.text();
 *     List<TextElement> words = r.elements(PageLevel.WORD);
 * }
 * }</pre>
 */
public final class Scribe implements AutoCloseable {

    // --- Configuration (final, carried for withLanguage) ---
    private final Path tesseractLib;
    private final Path leptonicaLib;
    private final Path datapath;
    private final String language;
    private final PageSegMode pageSegMode;
    private final OcrEngineMode ocrEngineMode;
    private final Map<String, String> variables;

    // --- Native session state ---
    private final Hypercube api;
    private final Pointer handle;

    // --- Lifecycle guard ---
    private boolean closed;

    private Scribe(Builder b, Hypercube api, Pointer handle) {
        this.tesseractLib = b.tesseractLib;
        this.leptonicaLib = b.leptonicaLib;
        this.datapath = b.datapath;
        this.language = b.language;
        this.pageSegMode = b.pageSegMode;
        this.ocrEngineMode = b.ocrEngineMode;
        this.variables = Map.copyOf(b.variables);
        this.api = api;
        this.handle = handle;
        this.closed = false;
    }

    /** Starts a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------
    //  Public API — reading
    // ------------------------------------------------------------------

    /**
     * Runs one recognition pass on the given image and returns the full
     * recognized text along with elements at EVERY {@link PageLevel}.
     *
     * <p>Convenience for the common case. If you only need one or two
     * levels — for instance just {@link PageLevel#WORD} for a bounding
     * box pass — prefer {@link #read(BufferedImage, EnumSet)}: skipping
     * the levels you don't need spares one iterator walk each.
     *
     * @throws OctachorixFault if the session is closed, the image is
     *                         invalid, or the native side reports an
     *                         error
     */
    public Reading read(BufferedImage image) {
        return read(image, null, EnumSet.allOf(PageLevel.class));
    }

    /**
     * Runs one recognition pass on a rectangular region of the image
     * and returns elements at every {@link PageLevel}. Passing
     * {@code null} for {@code roi} is equivalent to
     * {@link #read(BufferedImage)}.
     */
    public Reading read(BufferedImage image, Rectangle roi) {
        return read(image, roi, EnumSet.allOf(PageLevel.class));
    }

    /**
     * Runs one recognition pass and extracts only the requested
     * {@link PageLevel}s. The returned {@link Reading#elements()} list
     * contains exactly the elements at those levels; asking
     * {@link Reading#elements(PageLevel)} for a level not requested
     * returns an empty list.
     *
     * <p>The single {@code TessBaseAPIRecognize} pass is the same
     * regardless of how many levels you ask for — it is the iterator
     * walk that scales linearly with the number of requested levels.
     */
    public Reading read(BufferedImage image, EnumSet<PageLevel> levels) {
        return read(image, null, levels);
    }

    /**
     * Runs one recognition pass on a rectangular region and extracts
     * only the requested {@link PageLevel}s. This is the full-form
     * method; the other {@code read} overloads delegate here.
     *
     * @param image  the image to recognize, non-null
     * @param roi    a sub-rectangle in image coordinates, or
     *               {@code null} for the whole image
     * @param levels the segmentation levels to extract; may be empty
     *               (recognition still runs, but the resulting
     *               {@link Reading#elements()} list will be empty)
     */
    public Reading read(BufferedImage image, Rectangle roi,
                        EnumSet<PageLevel> levels) {
        ensureOpen();
        Objects.requireNonNull(image, "image cannot be null");
        Objects.requireNonNull(levels, "levels cannot be null");

        PixelBuffer buffer = ImageBridge.canonicalize(image);
        api.TessBaseAPISetImage(
                handle,
                buffer.data(),
                buffer.width(),
                buffer.height(),
                buffer.bytesPerPixel(),
                buffer.bytesPerLine());

        if (roi != null) {
            if (roi.width <= 0 || roi.height <= 0) {
                throw new OctachorixFault(
                        "read() rectangle must have positive dimensions, got "
                        + roi);
            }
            api.TessBaseAPISetRectangle(
                    handle, roi.x, roi.y, roi.width, roi.height);
        }

        int rc = api.TessBaseAPIRecognize(handle, null);
        if (rc != 0) {
            throw new OctachorixFault(
                    "TessBaseAPIRecognize returned non-zero: " + rc);
        }

        String fullText = extractFullText();
        List<TextElement> elements = extractLevels(levels);
        return new Reading(fullText, elements);
    }

    // ------------------------------------------------------------------
    //  Public API — multi-language
    // ------------------------------------------------------------------

    /**
     * Returns a new {@code Scribe} configured for the given language,
     * sharing the same underlying native libraries as this one.
     *
     * <p>The new {@code Scribe} has its own {@code TessBaseAPI*} handle
     * (fresh {@link Hypercube#TessBaseAPICreate} + {@link
     * Hypercube#TessBaseAPIInit2}). This {@code Scribe} remains fully
     * usable and unaffected. Both must be closed independently.
     *
     * @param newLanguage BCP-47-ish language code accepted by
     *                    Tesseract ({@code "eng"}, {@code "fra"}, ...)
     *                    or a plus-separated list ({@code "eng+fra"})
     * @return a fresh {@code Scribe} initialised on {@code newLanguage}
     * @throws OctachorixFault if this {@code Scribe} is closed or the
     *                         new language is null or blank
     */
    public Scribe withLanguage(String newLanguage) {
        ensureOpen();
        Objects.requireNonNull(newLanguage, "language cannot be null");
        if (newLanguage.isBlank()) {
            throw new OctachorixFault("language cannot be blank");
        }

        Builder b = new Builder()
                .tesseractLibrary(tesseractLib)
                .leptonicaLibrary(leptonicaLib)
                .datapath(datapath)
                .language(newLanguage)
                .pageSegMode(pageSegMode)
                .ocrEngineMode(ocrEngineMode);
        for (Map.Entry<String, String> e : variables.entrySet()) {
            b.variable(e.getKey(), e.getValue());
        }
        return b.build();
    }

    // ------------------------------------------------------------------
    //  Public API — lifecycle
    // ------------------------------------------------------------------

    /**
     * Releases the underlying {@code TessBaseAPI*} handle. Idempotent:
     * calling {@code close()} more than once is a silent no-op.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            api.TessBaseAPIDelete(handle);
        }
    }

    /** Returns {@code true} after {@link #close()} has been called. */
    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------
    //  Public API — meta
    // ------------------------------------------------------------------

    /**
     * Returns the Tesseract version string of the loaded library
     * (e.g. {@code "5.5.0"}). Comparing this to the version bundled by
     * your native provisioner is the cheapest post-hoc check that
     * short-name resolution did not steal the show.
     */
    public String tesseractVersion() {
        ensureOpen();
        return api.TessVersion();
    }

    /** Returns the language string this session was initialised on. */
    public String language() {
        return language;
    }

    /** Returns an unmodifiable view of the effective variables. */
    public Map<String, String> variables() {
        return variables;
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    private void ensureOpen() {
        if (closed) {
            throw new OctachorixFault(
                    "Scribe is closed (TessBaseAPIDelete has been called)");
        }
    }

    private String extractFullText() {
        Pointer p = api.TessBaseAPIGetUTF8Text(handle);
        if (p == null) {
            return "";
        }
        try {
            String s = p.getString(0, StandardCharsets.UTF_8.name());
            return s == null ? "" : s;
        } finally {
            api.TessDeleteText(p);
        }
    }

    /**
     * Walks the recognition result at each requested {@link PageLevel}
     * and returns the flat list of {@link TextElement}s. One
     * {@code GetIterator} + {@code IteratorDelete} per level asked:
     * the Tesseract C API does not expose a reset/begin function on
     * {@code ResultIterator}, and re-obtaining an iterator is cheap
     * (it does not re-run recognition).
     *
     * <p>Iteration order follows the enum declaration order of
     * {@link PageLevel} regardless of the {@code Set} implementation
     * passed in, so {@link Reading#elements()} is deterministic.
     */
    private List<TextElement> extractLevels(Set<PageLevel> levels) {
        List<TextElement> out = new ArrayList<>();
        if (levels.isEmpty()) {
            return out;
        }
        for (PageLevel level : PageLevel.values()) {
            if (!levels.contains(level)) {
                continue;
            }
            Pointer iter = api.TessBaseAPIGetIterator(handle);
            if (iter == null) {
                continue;
            }
            try {
                collect(iter, level, out);
                while (api.TessPageIteratorNext(iter, level.value()) != 0) {
                    collect(iter, level, out);
                }
            } finally {
                api.TessResultIteratorDelete(iter);
            }
        }
        return out;
    }

    /**
     * Collects one {@link TextElement} at the iterator's current
     * position for the given level, if that position yields non-empty
     * text and a valid bounding box.
     */
    private void collect(Pointer iter, PageLevel level, List<TextElement> out) {
        Pointer textPtr = api.TessResultIteratorGetUTF8Text(iter, level.value());
        if (textPtr == null) {
            return;
        }
        String text;
        try {
            String raw = textPtr.getString(0, StandardCharsets.UTF_8.name());
            text = raw == null ? "" : raw;
        } finally {
            api.TessDeleteText(textPtr);
        }
        if (text.isEmpty()) {
            return;
        }

        float confidence = api.TessResultIteratorConfidence(iter, level.value());

        IntByReference left = new IntByReference();
        IntByReference top = new IntByReference();
        IntByReference right = new IntByReference();
        IntByReference bottom = new IntByReference();
        int hasBox = api.TessPageIteratorBoundingBox(
                iter, level.value(), left, top, right, bottom);
        if (hasBox == 0) {
            return;
        }

        int x = left.getValue();
        int y = top.getValue();
        int w = right.getValue() - x;
        int h = bottom.getValue() - y;
        if (w <= 0 || h <= 0) {
            return;
        }
        Rectangle bbox = new Rectangle(x, y, w, h);

        out.add(new TextElement(text, confidence, bbox, level));
    }

    // ------------------------------------------------------------------
    //  Builder
    // ------------------------------------------------------------------

    /**
     * Fluent builder for a {@link Scribe}. All configuration happens
     * here; the resulting {@code Scribe} is immutable in its Java
     * fields (though the underlying native state is not — see the
     * class-level thread-safety warning).
     */
    public static final class Builder {

        private Path tesseractLib;
        private Path leptonicaLib;
        private Path datapath;
        private String language;
        private PageSegMode pageSegMode = PageSegMode.AUTO;
        private boolean pageSegModeApplied = true;
        private OcrEngineMode ocrEngineMode = OcrEngineMode.DEFAULT;
        private final Map<String, String> variables = new LinkedHashMap<>();

        private Builder() {
        }

        /** Absolute path to the Tesseract shared library. Required. */
        public Builder tesseractLibrary(Path absolutePath) {
            this.tesseractLib = absolutePath;
            return this;
        }

        /** Absolute path to the Leptonica shared library. Required. */
        public Builder leptonicaLibrary(Path absolutePath) {
            this.leptonicaLib = absolutePath;
            return this;
        }

        /** Absolute path to the tessdata directory. Required. */
        public Builder datapath(Path tessdataDir) {
            this.datapath = tessdataDir;
            return this;
        }

        /**
         * Language string ({@code "eng"}, {@code "fra"}, or a plus-
         * separated list like {@code "eng+fra"}). Required.
         */
        public Builder language(String lang) {
            this.language = lang;
            return this;
        }

        /** Page segmentation mode. Defaults to {@link PageSegMode#AUTO}. */
        public Builder pageSegMode(PageSegMode mode) {
            this.pageSegMode = Objects.requireNonNull(
                    mode, "pageSegMode cannot be null");
            this.pageSegModeApplied = true;
            return this;
        }

        /**
         * Do not touch Tesseract's internal page segmentation mode at
         * {@code build()} time — leave whatever value the library sets
         * itself when its {@code TessBaseAPI*} is initialised.
         *
         * <p>Exists for consumers that need to preserve the historical
         * "PSM = -1 means do not call {@code TessBaseAPISetPageSegMode}"
         * sentinel (SikuliX legacy {@code OCR.Options.resetPSM()}).
         * Regular callers do not need this — the default AUTO covers
         * the common case.
         */
        public Builder pageSegModeUnset() {
            this.pageSegModeApplied = false;
            return this;
        }

        /** OCR engine mode. Defaults to {@link OcrEngineMode#DEFAULT}. */
        public Builder ocrEngineMode(OcrEngineMode mode) {
            this.ocrEngineMode = Objects.requireNonNull(
                    mode, "ocrEngineMode cannot be null");
            return this;
        }

        /**
         * Sets a Tesseract variable. Repeatable — the last value for
         * a given key wins.
         */
        public Builder variable(String key, String value) {
            Objects.requireNonNull(key, "variable key cannot be null");
            Objects.requireNonNull(value, "variable value cannot be null");
            variables.put(key, value);
            return this;
        }

        /**
         * Validates the configuration, preloads Leptonica (with
         * {@code RTLD_GLOBAL} on Linux so Tesseract's {@code NEEDED}
         * resolves against ours before the loader falls back to the
         * system), binds Tesseract, creates and initialises a fresh
         * {@code TessBaseAPI*}, applies the page segmentation mode
         * and any variables, and returns the {@link Scribe}.
         *
         * @throws OctachorixFault on any missing required field, any
         *                         invalid path, any native load
         *                         failure, or a non-zero return from
         *                         {@code TessBaseAPIInit2}
         */
        public Scribe build() {
            // Required fields
            requirePresent(tesseractLib, "tesseractLibrary");
            requirePresent(leptonicaLib, "leptonicaLibrary");
            requirePresent(datapath, "datapath");
            if (language == null || language.isBlank()) {
                throw new OctachorixFault("language is required");
            }
            if (!Files.isDirectory(datapath)) {
                throw new OctachorixFault(
                        "datapath must be an existing directory: "
                        + datapath);
            }

            // Load order matters. Leptonica first, with RTLD_GLOBAL on
            // Linux (default in NativeBond), so that when Tesseract is
            // loaded next its DT_NEEDED on liblept.so.5 is satisfied by
            // our Leptonica already in the global namespace.
            NativeBond.preload(leptonicaLib);

            Hypercube tessApi = NativeBond.bind(tesseractLib, Hypercube.class);

            Pointer h = tessApi.TessBaseAPICreate();
            if (h == null) {
                throw new OctachorixFault(
                        "TessBaseAPICreate returned null "
                        + "(library at " + tesseractLib + ")");
            }

            int rc = tessApi.TessBaseAPIInit2(
                    h,
                    datapath.toAbsolutePath().toString(),
                    language,
                    ocrEngineMode.value());
            if (rc != 0) {
                tessApi.TessBaseAPIDelete(h);
                throw new OctachorixFault(
                        "TessBaseAPIInit2 returned " + rc
                        + " (datapath=" + datapath
                        + ", language=" + language
                        + ", oem=" + ocrEngineMode + ")");
            }

            if (pageSegModeApplied) {
                tessApi.TessBaseAPISetPageSegMode(h, pageSegMode.value());
            }
            // else: leave whatever Tesseract initialised itself with,
            //       as requested via Builder.pageSegModeUnset().

            for (Map.Entry<String, String> e : variables.entrySet()) {
                // SetVariable returns BOOL: 0 means the variable name
                // was unknown to this Tesseract build. Not fatal —
                // some variables are version-specific and callers
                // regularly probe with best-effort semantics.
                tessApi.TessBaseAPISetVariable(h, e.getKey(), e.getValue());
            }

            return new Scribe(this, tessApi, h);
        }

        private static void requirePresent(Path p, String field) {
            if (p == null) {
                throw new OctachorixFault(field + " is required");
            }
        }
    }
}
