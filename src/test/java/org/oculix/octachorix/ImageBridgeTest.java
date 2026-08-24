/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 */
package org.oculix.octachorix;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImageBridge#canonicalize(BufferedImage)}.
 *
 * <p>We cover each recognised raster type — the two fast paths
 * ({@code TYPE_BYTE_GRAY}, {@code TYPE_USHORT_GRAY}) and the fallback
 * ({@code TYPE_INT_RGB}, {@code TYPE_INT_ARGB}, {@code TYPE_3BYTE_BGR})
 * — and check that the output has the right shape (bytes per pixel,
 * stride, buffer length) and that the pixel values survive the trip.
 */
class ImageBridgeTest {

    @Test
    void rejectsNullImage() {
        assertThrows(NullPointerException.class,
                () -> ImageBridge.canonicalize(null));
    }

    @Test
    void byteGray_fastPath_shape() {
        BufferedImage img = new BufferedImage(4, 3, BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setSample(0, 0, 0, 0x80);
        img.getRaster().setSample(3, 2, 0, 0xFF);

        PixelBuffer pb = ImageBridge.canonicalize(img);

        assertEquals(4, pb.width());
        assertEquals(3, pb.height());
        assertEquals(1, pb.bytesPerPixel());
        assertEquals(4, pb.bytesPerLine());
        assertEquals(4 * 3, pb.data().length);
        assertEquals((byte) 0x80, pb.data()[0]);
        assertEquals((byte) 0xFF, pb.data()[4 * 3 - 1]);
    }

    @Test
    void byteGray_isDefensivelyCopied() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setSample(0, 0, 0, 0x11);

        PixelBuffer pb = ImageBridge.canonicalize(img);
        byte before = pb.data()[0];

        // Mutating the source image AFTER canonicalize must not
        // corrupt the PixelBuffer we already handed out.
        img.getRaster().setSample(0, 0, 0, 0xEE);

        assertEquals(before, pb.data()[0],
                "PixelBuffer must be defensively copied from the raster");
    }

    @Test
    void ushortGray_downscaledTo8Bit() {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster raster = img.getRaster();
        DataBufferUShort db = (DataBufferUShort) raster.getDataBuffer();
        db.getData()[0] = (short) 0xFFFF; // full white 16-bit
        db.getData()[1] = (short) 0x0100; // 256 / 65535 -> ~0.4%, should map to 0x01

        PixelBuffer pb = ImageBridge.canonicalize(img);

        assertEquals(2, pb.width());
        assertEquals(1, pb.height());
        assertEquals(1, pb.bytesPerPixel());
        assertEquals((byte) 0xFF, pb.data()[0]);
        assertEquals((byte) 0x01, pb.data()[1]);
    }

    @Test
    void intRgb_fallback_shape() {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x11_22_33); // R=11 G=22 B=33
        img.setRGB(1, 0, 0xAA_BB_CC); // R=AA G=BB B=CC

        PixelBuffer pb = ImageBridge.canonicalize(img);

        assertEquals(3, pb.bytesPerPixel());
        assertEquals(2 * 3, pb.bytesPerLine());
        assertEquals(2 * 1 * 3, pb.data().length);
        // Pixel 0: R,G,B
        assertEquals((byte) 0x11, pb.data()[0]);
        assertEquals((byte) 0x22, pb.data()[1]);
        assertEquals((byte) 0x33, pb.data()[2]);
        // Pixel 1: R,G,B
        assertEquals((byte) 0xAA, pb.data()[3]);
        assertEquals((byte) 0xBB, pb.data()[4]);
        assertEquals((byte) 0xCC, pb.data()[5]);
    }

    @Test
    void intArgb_alphaIsDroppedRgbSurvives() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x77_88_99_AA); // A=77 R=88 G=99 B=AA

        PixelBuffer pb = ImageBridge.canonicalize(img);

        assertEquals(3, pb.bytesPerPixel());
        assertEquals(3, pb.data().length);
        assertEquals((byte) 0x88, pb.data()[0]);
        assertEquals((byte) 0x99, pb.data()[1]);
        assertEquals((byte) 0xAA, pb.data()[2]);
    }

    @Test
    void threeByteBgr_survivesAsRgbAfterFallback() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        // Set pixel via setRGB — TYPE_3BYTE_BGR interprets the sample
        // internally, but getRGB() always returns 0xAARRGGBB, so our
        // fallback picks the right R,G,B triplet regardless of storage.
        img.setRGB(0, 0, new Color(0x33, 0x66, 0x99).getRGB());

        PixelBuffer pb = ImageBridge.canonicalize(img);

        assertEquals(3, pb.bytesPerPixel());
        assertEquals((byte) 0x33, pb.data()[0]);
        assertEquals((byte) 0x66, pb.data()[1]);
        assertEquals((byte) 0x99, pb.data()[2]);
    }

    @Test
    void byteGray_subimage_fallsBackSafely_pixelsCorrect() {
        // Parent with a distinct value at each pixel so we can tell
        // if we accidentally read from the wrong offset.
        BufferedImage parent = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                parent.getRaster().setSample(x, y, 0, (x + y * 10) & 0xFF);
            }
        }
        // Sub-region (3,4) size 4x3 — parent buffer is shared, translation is (3,4).
        BufferedImage sub = parent.getSubimage(3, 4, 4, 3);

        PixelBuffer pb = ImageBridge.canonicalize(sub);

        assertEquals(4, pb.width());
        assertEquals(3, pb.height());
        // Fallback returns 3 bpp RGB. sub.getRGB(x,y) is authoritative
        // for what pixel (x,y) of the subimage actually renders as
        // (TYPE_BYTE_GRAY is CS_GRAY linear, getRGB applies the
        // linear->sRGB gamma, so the numeric value shifts predictably).
        assertEquals(3, pb.bytesPerPixel(),
                "sub-image falls back to RGB (safe path)");
        int topLeftExpectedR = (sub.getRGB(0, 0) >> 16) & 0xFF;
        assertEquals((byte) topLeftExpectedR, pb.data()[0],
                "top-left of buffer must match sub.getRGB(0,0) - "
                + "if this fails, fallback read from parent buffer at "
                + "offset 0 instead of the subimage window");
        int lastX = pb.width() - 1;
        int lastY = pb.height() - 1;
        int brExpectedR = (sub.getRGB(lastX, lastY) >> 16) & 0xFF;
        int lastIdx = (lastY * pb.width() + lastX) * 3;
        assertEquals((byte) brExpectedR, pb.data()[lastIdx],
                "bottom-right of buffer must match sub.getRGB(w-1,h-1)");
    }

    @Test
    void ushortGray_subimage_fallsBackSafely() {
        BufferedImage parent = new BufferedImage(8, 8, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                parent.getRaster().setSample(x, y, 0, (x + y * 32) * 256);
            }
        }
        BufferedImage sub = parent.getSubimage(2, 1, 3, 2);
        PixelBuffer pb = ImageBridge.canonicalize(sub);

        assertEquals(3, pb.width());
        assertEquals(2, pb.height());
        // As long as canonicalize does not throw and returns a buffer
        // sized for the SUB dimensions, the subimage-safe path works.
        assertTrue(pb.data().length >= pb.bytesPerLine() * pb.height());
    }

    @Test
    void producedPixelBufferPassesItsOwnValidation() {
        // If canonicalize() ever produces an inconsistent PixelBuffer,
        // the record's compact constructor will throw. This exercises
        // every branch quickly.
        int[] types = {
                BufferedImage.TYPE_BYTE_GRAY,
                BufferedImage.TYPE_USHORT_GRAY,
                BufferedImage.TYPE_INT_RGB,
                BufferedImage.TYPE_INT_ARGB,
                BufferedImage.TYPE_3BYTE_BGR,
        };
        for (int t : types) {
            BufferedImage img = new BufferedImage(5, 4, t);
            PixelBuffer pb = assertDoesNotThrow(
                    () -> ImageBridge.canonicalize(img),
                    "type " + t + " should canonicalize cleanly");
            assertEquals(5, pb.width());
            assertEquals(4, pb.height());
        }
    }
}
