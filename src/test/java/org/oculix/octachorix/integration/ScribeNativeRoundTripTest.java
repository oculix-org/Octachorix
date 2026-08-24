/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 */
package org.oculix.octachorix.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oculix.octachorix.PageLevel;
import org.oculix.octachorix.PageSegMode;
import org.oculix.octachorix.Reading;
import org.oculix.octachorix.Scribe;
import org.oculix.octachorix.TextElement;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration test that actually loads Tesseract, runs a
 * recognition pass on a synthetic image, and checks the recognized
 * text.
 *
 * <p>Skipped unless three system properties point at real files:
 * <ul>
 *   <li>{@code -Doctachorix.test.libs.tesseract=/abs/libtesseract.so.5}</li>
 *   <li>{@code -Doctachorix.test.libs.leptonica=/abs/libleptonica.so.6}</li>
 *   <li>{@code -Doctachorix.test.libs.tessdata=/abs/tessdata}</li>
 * </ul>
 *
 * <p>This lets the build pass anywhere (developer laptop with no
 * Tesseract, CI runners without the extra step) while remaining a
 * hard test on the CI matrix where those paths are provisioned.
 */
class ScribeNativeRoundTripTest {

    private static Path tesseractLib;
    private static Path leptonicaLib;
    private static Path tessdata;

    @BeforeAll
    static void loadPathsOrSkip() {
        String t = System.getProperty("octachorix.test.libs.tesseract");
        String l = System.getProperty("octachorix.test.libs.leptonica");
        String d = System.getProperty("octachorix.test.libs.tessdata");
        assumeTrue(t != null && l != null && d != null,
                () -> "Native integration test skipped — set "
                        + "-Doctachorix.test.libs.{tesseract,leptonica,tessdata} "
                        + "to enable it.");
        tesseractLib = Paths.get(t);
        leptonicaLib = Paths.get(l);
        tessdata = Paths.get(d);
    }

    @Test
    void readsSyntheticTextAndReportsAWordWithBoundingBox() {
        BufferedImage img = renderText("Octachorix", 320, 90);

        try (Scribe scribe = Scribe.builder()
                .tesseractLibrary(tesseractLib)
                .leptonicaLibrary(leptonicaLib)
                .datapath(tessdata)
                .language("eng")
                .pageSegMode(PageSegMode.SINGLE_LINE)
                .build()) {

            String version = scribe.tesseractVersion();
            assertNotNull(version, "TessVersion() should not return null");
            assertFalse(version.isBlank(), "TessVersion() should not be blank");

            Reading reading = scribe.read(img);
            assertNotNull(reading);
            assertNotNull(reading.text());
            assertTrue(reading.text().toLowerCase().contains("octachorix"),
                    "recognized text should contain 'octachorix' — got: '"
                            + reading.text().strip() + "'");

            List<TextElement> words = reading.elements(PageLevel.WORD);
            assertFalse(words.isEmpty(), "expected at least one WORD element");

            TextElement first = words.get(0);
            assertNotNull(first.bbox(), "WORD element must carry a bbox");
            assertTrue(first.bbox().width > 0, "bbox width must be positive");
            assertTrue(first.bbox().height > 0, "bbox height must be positive");
            assertTrue(first.confidence() > 0f,
                    "confidence must be positive for a well-recognized word");
        }
    }

    @Test
    void multiLanguageForkSharesLibsIndependentSessions() {
        BufferedImage img = renderText("Octachorix", 320, 90);

        try (Scribe english = Scribe.builder()
                .tesseractLibrary(tesseractLib)
                .leptonicaLibrary(leptonicaLib)
                .datapath(tessdata)
                .language("eng")
                .build()) {

            Reading r1 = english.read(img);
            assertTrue(r1.text().toLowerCase().contains("octachorix"));

            // withLanguage returns a new Scribe. english must remain
            // usable in parallel and after the child is closed.
            try (Scribe english2 = english.withLanguage("eng")) {
                Reading r2 = english2.read(img);
                assertTrue(r2.text().toLowerCase().contains("octachorix"));
            }

            Reading r3 = english.read(img);
            assertTrue(r3.text().toLowerCase().contains("octachorix"),
                    "parent Scribe must remain usable after child close()");
        }
    }

    /**
     * Renders the given text on a white background with a large font,
     * antialiased, in a way that Tesseract reliably recognises across
     * platforms. Uses {@code Font.DIALOG} so the test does not depend
     * on a specific typeface being installed.
     */
    private static BufferedImage renderText(String text, int width, int height) {
        BufferedImage img = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 48));
            g.drawString(text, 15, 60);
        } finally {
            g.dispose();
        }
        return img;
    }
}
