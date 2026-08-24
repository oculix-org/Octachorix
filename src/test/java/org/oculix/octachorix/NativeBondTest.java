/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 */
package org.oculix.octachorix;

import com.sun.jna.Library;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NativeBond}. These do not load a real native
 * library — that is the job of the integration tests. Here we prove
 * that the loader refuses everything it is documented to refuse:
 * null paths, relative paths, and paths that do not point to an
 * existing regular file.
 *
 * <p>These tests are the first line of defence against a regression of
 * Legerix#20. If any of them start passing on a garbage input, someone
 * has quietly reintroduced a fallback path.
 */
class NativeBondTest {

    private interface DummyLib extends Library {
    }

    @Test
    void bindRejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> NativeBond.bind(null, DummyLib.class));
    }

    @Test
    void bindRejectsNullApiClass() {
        assertThrows(NullPointerException.class,
                () -> NativeBond.bind(Paths.get("/tmp/whatever.so"), null));
    }

    @Test
    void bindRejectsRelativePath() {
        Path relative = Paths.get("libtesseract.so.5");
        OctachorixFault fault = assertThrows(OctachorixFault.class,
                () -> NativeBond.bind(relative, DummyLib.class));
        assertTrue(fault.getMessage().contains("absolute"),
                "message should mention 'absolute' — got: "
                + fault.getMessage());
    }

    @Test
    void bindRejectsMissingFile() {
        Path missing = Paths.get(
                System.getProperty("java.io.tmpdir"),
                "octachorix-no-such-file-" + System.nanoTime() + ".so");
        OctachorixFault fault = assertThrows(OctachorixFault.class,
                () -> NativeBond.bind(missing, DummyLib.class));
        assertTrue(fault.getMessage().contains("does not exist")
                        || fault.getMessage().contains("not a regular"),
                "message should mention 'does not exist' or "
                + "'not a regular file' — got: " + fault.getMessage());
    }

    @Test
    void preloadRejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> NativeBond.preload(null));
    }

    @Test
    void preloadRejectsRelativePath() {
        Path relative = Paths.get("libleptonica.so.6");
        OctachorixFault fault = assertThrows(OctachorixFault.class,
                () -> NativeBond.preload(relative));
        assertTrue(fault.getMessage().contains("absolute"),
                "message should mention 'absolute' — got: "
                + fault.getMessage());
    }

    @Test
    void preloadRejectsMissingFile() {
        Path missing = Paths.get(
                System.getProperty("java.io.tmpdir"),
                "octachorix-no-such-file-" + System.nanoTime() + ".so");
        OctachorixFault fault = assertThrows(OctachorixFault.class,
                () -> NativeBond.preload(missing));
        assertTrue(fault.getMessage().contains("does not exist")
                        || fault.getMessage().contains("not a regular"),
                "message should mention 'does not exist' or "
                + "'not a regular file' — got: " + fault.getMessage());
    }

    @Test
    void defaultOptionsAreNonNull() {
        assertNotNull(NativeBond.defaultOptions());
    }
}
