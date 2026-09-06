/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA bindings against Tesseract's public C API header
 * ({@code tesseract/capi.h}).
 *
 * <p>The name {@code Hypercube} is not decorative. Octachorix is the
 * mathematical name of the 4D tesseract (also called octachoron, the
 * 8-cell), and this interface is the one place in the library where
 * the whole native surface is declared. When someone wants to grep
 * for every C function this project touches, they grep here.
 *
 * <p>This interface intentionally holds no logic. It is a
 * declaration of Tesseract's exported symbols and nothing more.
 * All lifecycle management, buffer marshalling, error interpretation,
 * and iterator walking happens in {@link Scribe} and its collaborators.
 *
 * <h2>C-to-Java type conventions</h2>
 * <ul>
 *   <li>{@code TessBaseAPI*}, {@code TessResultIterator*},
 *       {@code TessPageIterator*} — opaque C pointers, mapped to
 *       {@link Pointer}.</li>
 *   <li>{@code const char*} — mapped to {@link String} for input,
 *       {@link Pointer} for output (which must then be freed with
 *       {@link #TessDeleteText}).</li>
 *   <li>{@code const unsigned char*} for pixel data — mapped to
 *       {@code byte[]}; JNA copies to native memory during the call
 *       and Tesseract itself makes its own copy of the buffer.</li>
 *   <li>{@code BOOL} (a typedef for {@code int} in Tesseract's
 *       {@code platform.h}) — mapped to {@code int}, where {@code 0}
 *       means false and any non-zero value means true. This is
 *       intentional: JNA's automatic {@code boolean} mapping is
 *       fragile across platforms for pure {@code int}-typedef
 *       BOOL definitions.</li>
 *   <li>{@code TessPageIteratorLevel} and other enums — mapped to
 *       {@code int}; the caller passes {@link PageLevel#value()}
 *       or the equivalent for other enums.</li>
 * </ul>
 *
 * <h2>Provenance</h2>
 * <p>Every signature below is transcribed from the upstream
 * Tesseract C API header ({@code capi.h}, publicly documented).
 * No {@code tess4j} source code was consulted while writing this
 * interface.
 */
public interface Hypercube extends Library {

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    /** Allocates and returns a new {@code TessBaseAPI*} instance. */
    Pointer TessBaseAPICreate();

    /**
     * Destroys the {@code TessBaseAPI*} instance and releases all
     * resources associated with it.
     */
    void TessBaseAPIDelete(Pointer handle);

    /**
     * Initialises a {@code TessBaseAPI*} with the given {@code tessdata}
     * directory, language string, and OCR engine mode.
     *
     * @param handle the pointer returned by {@link #TessBaseAPICreate}
     * @param datapath absolute path to the directory containing
     *                 {@code *.traineddata} files
     * @param language BCP-47-ish language code ({@code "eng"},
     *                 {@code "fra"}) or a plus-separated list
     *                 ({@code "eng+fra"})
     * @param oem one of {@link OcrEngineMode#value()}
     * @return {@code 0} on success, non-zero on failure
     */
    int TessBaseAPIInit2(Pointer handle, String datapath, String language, int oem);

    /**
     * Same as {@link #TessBaseAPIInit2} plus a list of Tesseract config
     * file names ({@code "digits"}, {@code "quiet"}, ...) resolved by
     * Tesseract against {@code <datapath>/configs/} and applied at
     * initialisation.
     *
     * @param configs      config file names, {@code null} when
     *                     {@code configsSize} is {@code 0}
     * @param configsSize  number of entries in {@code configs}
     * @return {@code 0} on success, non-zero on failure
     */
    int TessBaseAPIInit1(Pointer handle, String datapath, String language, int oem,
                         String[] configs, int configsSize);

    /**
     * Ends the recognition session for the current image and clears
     * any per-image state, but does not destroy the {@code TessBaseAPI*}
     * itself.
     */
    void TessBaseAPIEnd(Pointer handle);

    // ------------------------------------------------------------------
    //  Configuration
    // ------------------------------------------------------------------

    /**
     * Sets a named Tesseract variable ({@code tessedit_char_whitelist},
     * {@code preserve_interword_spaces}, etc.).
     *
     * @return {@code BOOL}: {@code 0} if the variable name was unknown
     */
    int TessBaseAPISetVariable(Pointer handle, String name, String value);

    /** Sets the page segmentation mode. */
    void TessBaseAPISetPageSegMode(Pointer handle, int mode);

    // ------------------------------------------------------------------
    //  Image input
    // ------------------------------------------------------------------

    /**
     * Provides an image for the next recognition pass.
     *
     * @param imagedata raw pixels, row-major, no leading padding
     * @param bytesPerPixel {@code 1} (grayscale), {@code 3} (RGB) or
     *                      {@code 4} (RGBA); 3-byte data is expected
     *                      in R,G,B order (not BGR)
     * @param bytesPerLine bytes per row, must be at least
     *                     {@code width * bytesPerPixel}
     */
    void TessBaseAPISetImage(Pointer handle, byte[] imagedata,
                             int width, int height,
                             int bytesPerPixel, int bytesPerLine);

    /**
     * Restricts recognition to a rectangular region within the image
     * previously set by {@link #TessBaseAPISetImage}.
     */
    void TessBaseAPISetRectangle(Pointer handle, int left, int top,
                                 int width, int height);

    // ------------------------------------------------------------------
    //  Recognition — the one expensive call
    // ------------------------------------------------------------------

    /**
     * Runs the OCR engine over the currently-set image (and rectangle,
     * if any). Everything the iterator later exposes — text, boxes,
     * confidences, at every {@link PageLevel} — comes from this one
     * recognition pass.
     *
     * @param monitor a progress-monitor pointer, may be {@code null}
     * @return {@code 0} on success, non-zero on failure
     */
    int TessBaseAPIRecognize(Pointer handle, Pointer monitor);

    // ------------------------------------------------------------------
    //  Text extraction
    // ------------------------------------------------------------------

    /**
     * Returns a pointer to a UTF-8 C string with the full recognized
     * text. The caller MUST release the string via
     * {@link #TessDeleteText}.
     */
    Pointer TessBaseAPIGetUTF8Text(Pointer handle);

    /**
     * Frees a UTF-8 string previously returned by
     * {@link #TessBaseAPIGetUTF8Text} or
     * {@link #TessResultIteratorGetUTF8Text}.
     */
    void TessDeleteText(Pointer text);

    // ------------------------------------------------------------------
    //  Iterator over recognized elements
    // ------------------------------------------------------------------

    /**
     * Returns a {@code TessResultIterator*} positioned at the first
     * element of the recognized page. Must be released with
     * {@link #TessResultIteratorDelete}.
     */
    Pointer TessBaseAPIGetIterator(Pointer handle);

    /** Frees a result iterator. */
    void TessResultIteratorDelete(Pointer iter);

    /**
     * Advances the iterator to the next element at the given level.
     *
     * @return {@code BOOL}: {@code 0} when no further element exists
     */
    int TessPageIteratorNext(Pointer iter, int level);

    /**
     * Returns a pointer to a UTF-8 C string with the recognized text
     * for the current element at the given level. Must be released
     * with {@link #TessDeleteText}.
     */
    Pointer TessResultIteratorGetUTF8Text(Pointer iter, int level);

    /** Returns the recognition confidence (0.0 - 100.0) at the given level. */
    float TessResultIteratorConfidence(Pointer iter, int level);

    /**
     * Fills the four out-parameters with the axis-aligned bounding
     * box of the current element at the given level.
     *
     * @return {@code BOOL}: {@code 0} if no box is available
     */
    int TessPageIteratorBoundingBox(Pointer iter, int level,
                                    IntByReference left,
                                    IntByReference top,
                                    IntByReference right,
                                    IntByReference bottom);

    // ------------------------------------------------------------------
    //  Meta
    // ------------------------------------------------------------------

    /**
     * Returns the Tesseract version string of the loaded library,
     * e.g. {@code "5.5.0"} or {@code "5.3.4"}. Reading this value
     * on a freshly-loaded {@code Hypercube} is the cheapest way to
     * assert that the loader picked the file we asked for.
     */
    String TessVersion();
}
