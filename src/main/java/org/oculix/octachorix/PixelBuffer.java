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
 * A canonical pixel buffer ready for {@code TessBaseAPISetImage}.
 *
 * <p>Produced by {@link ImageBridge#canonicalize} and consumed by
 * {@link Scribe#read}. The record is public so tests and diagnostics
 * can inspect its fields, but consumers of the {@link Scribe} facade
 * will typically never construct one directly.
 *
 * <p>Layout matches the four arguments Tesseract's C API expects:
 * a contiguous {@code byte[]} of raw pixels, {@code width} and
 * {@code height} in pixels, {@code bytesPerPixel} of 1 (grayscale),
 * 3 (RGB) or 4 (RGBA), and {@code bytesPerLine} for the stride
 * (often equal to {@code width * bytesPerPixel}, but may be padded
 * for alignment).
 *
 * @param data raw pixels, row-major, no gaps beyond {@code bytesPerLine}
 * @param width image width in pixels
 * @param height image height in pixels
 * @param bytesPerPixel 1, 3, or 4
 * @param bytesPerLine bytes per row, must be at least
 *                     {@code width * bytesPerPixel}
 */
public record PixelBuffer(
        byte[] data,
        int width,
        int height,
        int bytesPerPixel,
        int bytesPerLine
) {

    public PixelBuffer {
        if (data == null) {
            throw new OctachorixFault("PixelBuffer.data cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new OctachorixFault(
                    "PixelBuffer dimensions must be positive, got "
                    + width + "x" + height);
        }
        if (bytesPerPixel != 1 && bytesPerPixel != 3 && bytesPerPixel != 4) {
            throw new OctachorixFault(
                    "PixelBuffer.bytesPerPixel must be 1, 3, or 4, got "
                    + bytesPerPixel);
        }
        int minStride = width * bytesPerPixel;
        if (bytesPerLine < minStride) {
            throw new OctachorixFault(
                    "PixelBuffer.bytesPerLine (" + bytesPerLine
                    + ") is smaller than width*bytesPerPixel ("
                    + minStride + ")");
        }
        long required = (long) bytesPerLine * height;
        if (data.length < required) {
            throw new OctachorixFault(
                    "PixelBuffer.data has " + data.length
                    + " bytes but needs at least " + required
                    + " (" + bytesPerLine + " * " + height + ")");
        }
    }
}
