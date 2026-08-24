/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.util.Objects;

/**
 * Converts a {@link BufferedImage} into a {@link PixelBuffer} laid out
 * exactly the way {@code TessBaseAPISetImage} expects it.
 *
 * <p>tess4j delegates this conversion to lept4j's {@code Pix} type.
 * Octachorix drops lept4j on the Java side to keep the dependency
 * surface minimal, so we do the conversion ourselves. It is not
 * conceptually hard, but it has enough {@link BufferedImage} raster
 * variants to be worth its own class.
 *
 * <h2>Supported input types (fast path)</h2>
 * <ul>
 *   <li>{@link BufferedImage#TYPE_BYTE_GRAY} — one byte per pixel,
 *       direct raster copy, 1 bpp.</li>
 *   <li>{@link BufferedImage#TYPE_USHORT_GRAY} — 16-bit grayscale,
 *       right-shifted by 8 to reach 8 bits, 1 bpp.</li>
 * </ul>
 *
 * <h2>Fallback path (correct for every other raster)</h2>
 * <p>Everything else — {@code TYPE_INT_RGB}, {@code TYPE_INT_ARGB},
 * {@code TYPE_3BYTE_BGR}, {@code TYPE_CUSTOM}, indexed rasters,
 * whatever came out of {@code ImageIO.read} on someone's exotic
 * TIFF — flows through {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)},
 * which always returns 32-bit packed {@code 0xAARRGGBB} regardless of
 * the underlying raster. We then unpack to a 3-byte-per-pixel R,G,B
 * buffer, which is what Tesseract expects for {@code bytesPerPixel == 3}.
 *
 * <p>The fast paths are opt-in optimisations. Correctness is the
 * fallback's job.
 */
public final class ImageBridge {

    private ImageBridge() {
        // No instances. This is a bridge, not a passenger.
    }

    /**
     * Converts a {@link BufferedImage} into a {@link PixelBuffer}
     * ready for {@code TessBaseAPISetImage}.
     *
     * <p>The returned buffer never aliases the {@code BufferedImage}'s
     * internal raster — a mutation of the source image after this call
     * cannot corrupt the buffer Tesseract sees.
     *
     * @param image the input image, non-null, non-empty
     * @return a canonical {@link PixelBuffer}, either 1 bpp grayscale
     *         or 3 bpp RGB depending on the source
     * @throws OctachorixFault if {@code image} is null or has
     *                         non-positive dimensions
     */
    public static PixelBuffer canonicalize(BufferedImage image) {
        Objects.requireNonNull(image, "image cannot be null");

        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            throw new OctachorixFault(
                    "Image dimensions must be positive, got "
                    + width + "x" + height);
        }

        int type = image.getType();
        switch (type) {
            case BufferedImage.TYPE_BYTE_GRAY:
                return fastByteGray(image, width, height);
            case BufferedImage.TYPE_USHORT_GRAY:
                return fastUshortGray(image, width, height);
            default:
                return fallbackRgb(image, width, height);
        }
    }

    /**
     * Fast path for {@link BufferedImage#TYPE_BYTE_GRAY}: the raster's
     * underlying {@link DataBufferByte} is already the raw byte-per-pixel
     * grayscale buffer Tesseract wants. We defensively copy so that a
     * subsequent mutation of the source image cannot corrupt the buffer
     * we hand out.
     */
    private static PixelBuffer fastByteGray(BufferedImage image,
                                            int width, int height) {
        DataBufferByte db = (DataBufferByte) image.getRaster().getDataBuffer();
        byte[] shared = db.getData();
        byte[] copy = new byte[width * height];
        // Straight copy: TYPE_BYTE_GRAY has no per-line padding beyond
        // the pixel width in the standard Java raster layout.
        System.arraycopy(shared, 0, copy, 0, copy.length);
        return new PixelBuffer(copy, width, height, 1, width);
    }

    /**
     * Fast path for {@link BufferedImage#TYPE_USHORT_GRAY}: the raster
     * stores one unsigned 16-bit value per pixel. Tesseract only speaks
     * 8-bit-per-channel, so we downscale by shifting right 8 bits
     * ({@code value >>> 8}), which is the standard 16-to-8-bit
     * grayscale conversion.
     */
    private static PixelBuffer fastUshortGray(BufferedImage image,
                                              int width, int height) {
        DataBufferUShort db = (DataBufferUShort) image.getRaster().getDataBuffer();
        short[] src = db.getData();
        int pixels = width * height;
        byte[] dst = new byte[pixels];
        for (int i = 0; i < pixels; i++) {
            int v = src[i] & 0xFFFF;
            dst[i] = (byte) (v >>> 8);
        }
        return new PixelBuffer(dst, width, height, 1, width);
    }

    /**
     * Correctness-first fallback for every other raster: pull pixels
     * via {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)}
     * (which always returns 32-bit packed {@code 0xAARRGGBB}), then
     * unpack to a 3-byte R,G,B buffer.
     *
     * <p>Slower than direct raster access — one {@code int[]} allocation
     * and one pass to unpack — but robust to any raster the caller
     * throws at us. Never breaks on {@code TYPE_CUSTOM} or indexed
     * images the way a hand-rolled raster switch would.
     */
    private static PixelBuffer fallbackRgb(BufferedImage image,
                                           int width, int height) {
        int pixels = width * height;
        int[] argb = new int[pixels];
        image.getRGB(0, 0, width, height, argb, 0, width);

        byte[] rgb = new byte[pixels * 3];
        int di = 0;
        for (int i = 0; i < pixels; i++) {
            int p = argb[i];
            rgb[di++] = (byte) ((p >>> 16) & 0xFF); // R
            rgb[di++] = (byte) ((p >>> 8) & 0xFF);  // G
            rgb[di++] = (byte) (p & 0xFF);          // B
        }
        return new PixelBuffer(rgb, width, height, 3, width * 3);
    }
}
