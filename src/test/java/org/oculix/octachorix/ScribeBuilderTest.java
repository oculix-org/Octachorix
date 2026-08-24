/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 */
package org.oculix.octachorix;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Scribe.Builder} validation.
 *
 * <p>All configuration errors must be surfaced at {@code build()}
 * time, before any native library is touched. A {@code Scribe} that
 * came out of {@code build()} must be usable; there is no half-built
 * state to worry about.
 *
 * <p>These tests deliberately pass invalid or missing input so the
 * builder throws before {@link NativeBond#preload} or
 * {@link NativeBond#bind} is reached. Real native binding is covered
 * by the integration tests.
 */
class ScribeBuilderTest {

    @Test
    void missingTesseractLibraryFails() {
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .leptonicaLibrary(fakeAbsolutePath("libleptonica.so.6"))
                        .datapath(fakeAbsolutePath("tessdata"))
                        .language("eng")
                        .build());
        assertTrue(f.getMessage().contains("tesseractLibrary"));
    }

    @Test
    void missingLeptonicaLibraryFails() {
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .tesseractLibrary(fakeAbsolutePath("libtesseract.so.5"))
                        .datapath(fakeAbsolutePath("tessdata"))
                        .language("eng")
                        .build());
        assertTrue(f.getMessage().contains("leptonicaLibrary"));
    }

    @Test
    void missingDatapathFails() {
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .tesseractLibrary(fakeAbsolutePath("libtesseract.so.5"))
                        .leptonicaLibrary(fakeAbsolutePath("libleptonica.so.6"))
                        .language("eng")
                        .build());
        assertTrue(f.getMessage().contains("datapath"));
    }

    @Test
    void missingLanguageFails() {
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .tesseractLibrary(fakeAbsolutePath("libtesseract.so.5"))
                        .leptonicaLibrary(fakeAbsolutePath("libleptonica.so.6"))
                        .datapath(fakeAbsolutePath("tessdata"))
                        .build());
        assertTrue(f.getMessage().contains("language"));
    }

    @Test
    void blankLanguageFails() {
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .tesseractLibrary(fakeAbsolutePath("libtesseract.so.5"))
                        .leptonicaLibrary(fakeAbsolutePath("libleptonica.so.6"))
                        .datapath(fakeAbsolutePath("tessdata"))
                        .language("   ")
                        .build());
        assertTrue(f.getMessage().contains("language"));
    }

    @Test
    void datapathMustBeExistingDirectory() {
        // Real absolute path but not a directory (points to a file
        // path that certainly does not exist as a directory).
        Path bogus = Paths.get(
                System.getProperty("java.io.tmpdir"),
                "octachorix-no-such-datapath-" + System.nanoTime());
        OctachorixFault f = assertThrows(OctachorixFault.class,
                () -> Scribe.builder()
                        .tesseractLibrary(fakeAbsolutePath("libtesseract.so.5"))
                        .leptonicaLibrary(fakeAbsolutePath("libleptonica.so.6"))
                        .datapath(bogus)
                        .language("eng")
                        .build());
        assertTrue(f.getMessage().contains("datapath")
                        || f.getMessage().contains(bogus.toString()));
    }

    @Test
    void nullPageSegModeRejected() {
        assertThrows(NullPointerException.class,
                () -> Scribe.builder().pageSegMode(null));
    }

    @Test
    void nullOcrEngineModeRejected() {
        assertThrows(NullPointerException.class,
                () -> Scribe.builder().ocrEngineMode(null));
    }

    @Test
    void nullVariableKeyRejected() {
        assertThrows(NullPointerException.class,
                () -> Scribe.builder().variable(null, "value"));
    }

    @Test
    void nullVariableValueRejected() {
        assertThrows(NullPointerException.class,
                () -> Scribe.builder().variable("key", null));
    }

    /**
     * Returns an absolute-looking path that does not exist. Good
     * enough for {@code build()} to fail at the "required field"
     * check before native loading is attempted — the checks in this
     * class exercise the earlier validation layer only.
     */
    private static Path fakeAbsolutePath(String name) {
        return Paths.get(System.getProperty("java.io.tmpdir"),
                "octachorix-fake-" + name).toAbsolutePath();
    }
}
