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
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The absolute-path loader — the single point in Octachorix that binds
 * a JNA interface to a shared library on disk.
 *
 * <p>Every native lib Octachorix touches goes through this class.
 * The caller supplies an absolute path; {@code NativeBond} verifies
 * the file exists, applies the correct dlopen/LoadLibrary flags for
 * the platform, hands the file to JNA, and returns the ready-to-use
 * interface. No short-name resolution. No {@code LD_LIBRARY_PATH}
 * fallback. No {@code /usr/lib} scan. No version-max heuristic.
 *
 * <p>This exists because of {@code Legerix#20}: on any Unix host with
 * a system Tesseract installed, JNA's default {@code Native.load(name)}
 * pipeline will happily fall back to whatever the system offers when
 * the exact short-name lookup fails, and the version-max heuristic will
 * usually make the system library win over a project-bundled one.
 * That path is closed here.
 *
 * <h2>Two operations</h2>
 * <ul>
 *   <li>{@link #bind(Path, Class)} — load the library AND bind a JNA
 *       interface to it. This is what you want for Tesseract, which we
 *       call through {@link Hypercube}.</li>
 *   <li>{@link #preload(Path)} — load the library into the process
 *       address space (with {@code RTLD_GLOBAL} on Linux) without
 *       binding any Java interface to it. Use this for a dependency
 *       library that must be present in memory for another library's
 *       {@code DT_NEEDED} to resolve — Leptonica is the textbook case:
 *       Tesseract's {@code NEEDED liblept.so.5} must be satisfied by
 *       our Leptonica before Tesseract loads, otherwise the loader
 *       falls back to the system's copy.</li>
 * </ul>
 *
 * <h2>Platform-specific defaults</h2>
 * <ul>
 *   <li><b>Linux / FreeBSD / AIX:</b> {@code RTLD_LAZY | RTLD_GLOBAL}
 *       is set via {@link Library#OPTION_OPEN_FLAGS}. {@code RTLD_GLOBAL}
 *       makes the loaded library visible to subsequent {@code dlopen}
 *       calls, which is exactly what {@link #preload(Path)} exists to
 *       exploit.</li>
 *   <li><b>macOS / Windows:</b> default flags. The macOS flat namespace
 *       and the Windows loader handle this case without extra options.</li>
 * </ul>
 */
public final class NativeBond {

    /** Linux {@code dlopen} flag: resolve symbols on first use. */
    private static final int RTLD_LAZY_LINUX = 0x1;

    /**
     * Linux {@code dlopen} flag: make this library's symbols available
     * to subsequently-loaded libraries.
     */
    private static final int RTLD_GLOBAL_LINUX = 0x100;

    private NativeBond() {
        // No instances. Ever. The operations this class offers are static,
        // and adding an instance would only encourage subclassing tricks
        // that have no place in a loader.
    }

    /**
     * Loads the native library at the given absolute path and returns
     * an implementation of the JNA interface.
     *
     * <p>Uses platform-appropriate default open flags — see the class
     * javadoc. For explicit control, use the three-argument overload.
     *
     * @param absolutePath absolute path to the native library file
     * @param apiClass the JNA {@link Library} interface to bind
     * @param <T> the interface type
     * @return a proxy instance implementing {@code apiClass}
     * @throws OctachorixFault if the path is not absolute, the file
     *                         does not exist, or the underlying
     *                         {@code dlopen}/{@code LoadLibrary} fails
     */
    public static <T extends Library> T bind(Path absolutePath,
                                             Class<T> apiClass) {
        return bind(absolutePath, apiClass, defaultOptions());
    }

    /**
     * Loads the native library at the given absolute path with the
     * caller-supplied JNA options.
     */
    public static <T extends Library> T bind(Path absolutePath,
                                             Class<T> apiClass,
                                             Map<String, ?> options) {
        Objects.requireNonNull(absolutePath, "absolutePath cannot be null");
        Objects.requireNonNull(apiClass, "apiClass cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        String pathString = validate(absolutePath);

        try {
            // Native.load(String, Class, Map) accepts an absolute path
            // as its first argument. JNA detects the absolute form
            // (File.isAbsolute()) and skips short-name resolution
            // entirely: no jna.library.path scan, no matchLibrary()
            // version-max heuristic, no fallback to /usr/lib.
            return Native.load(pathString, apiClass, options);
        } catch (UnsatisfiedLinkError e) {
            throw new OctachorixFault(
                    "Failed to load native library at " + pathString
                    + " (interface " + apiClass.getName() + ")", e);
        } catch (RuntimeException e) {
            throw new OctachorixFault(
                    "Unexpected failure loading native library at "
                    + pathString + " (interface " + apiClass.getName() + ")",
                    e);
        }
    }

    /**
     * Loads the native library at the given absolute path into the
     * process address space, without binding any Java interface to it.
     *
     * <p>Useful for a dependency library that must be present in memory
     * so another library's {@code DT_NEEDED} can resolve against it.
     * Preloading Leptonica before {@link #bind}-ing Tesseract is the
     * canonical use case: on Linux, {@code libtesseract.so.5} has a
     * {@code NEEDED liblept.so.5} entry, and if our Leptonica is not
     * already visible in the global namespace, the dynamic linker will
     * happily reach for {@code /usr/lib/.../liblept.so.5.0.4} and give
     * us the system copy — which is exactly the crash trap we exist to
     * avoid.
     *
     * <p>The library is cached by JNA against its path, so a subsequent
     * {@link #bind} call on the same path will reuse the same underlying
     * {@code NativeLibrary} — no double load.
     *
     * @param absolutePath absolute path to the native library file
     * @throws OctachorixFault if the path is not absolute, the file
     *                         does not exist, or {@code dlopen} fails
     */
    public static void preload(Path absolutePath) {
        Objects.requireNonNull(absolutePath, "absolutePath cannot be null");
        String pathString = validate(absolutePath);
        try {
            NativeLibrary.getInstance(pathString, defaultOptions());
        } catch (UnsatisfiedLinkError e) {
            throw new OctachorixFault(
                    "Failed to preload native library at " + pathString, e);
        } catch (RuntimeException e) {
            throw new OctachorixFault(
                    "Unexpected failure preloading native library at "
                    + pathString, e);
        }
    }

    /**
     * Validates that a path is absolute and points to an existing
     * regular file. Returns the resolved string form.
     */
    private static String validate(Path absolutePath) {
        if (!absolutePath.isAbsolute()) {
            throw new OctachorixFault(
                    "NativeBond requires an absolute path, got: "
                    + absolutePath);
        }
        if (!Files.isRegularFile(absolutePath)) {
            throw new OctachorixFault(
                    "NativeBond target does not exist or is not a regular file: "
                    + absolutePath);
        }
        return absolutePath.toAbsolutePath().toString();
    }

    /**
     * Returns the default JNA options for the current OS. Exposed
     * package-private for tests; not part of the public API.
     */
    static Map<String, ?> defaultOptions() {
        if (Platform.isLinux() || Platform.isFreeBSD() || Platform.isAIX()) {
            Map<String, Object> opts = new HashMap<>();
            opts.put(Library.OPTION_OPEN_FLAGS,
                    RTLD_LAZY_LINUX | RTLD_GLOBAL_LINUX);
            return Collections.unmodifiableMap(opts);
        }
        return Collections.emptyMap();
    }
}
