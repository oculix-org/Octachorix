/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.oculix.octachorix;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the loader actually resolved for a {@link Scribe}, as opposed to
 * what the caller asked for.
 *
 * <p>Each path is the file the dynamic loader reports as defining a
 * symbol looked up through the bound Tesseract's own handle, so it
 * names the object that answers Tesseract's calls: {@code TessVersion}
 * for Tesseract, {@code pixCreate} for Leptonica, one entry point per
 * image codec. It is never an echo of the path given to the
 * {@link Scribe.Builder}; when the platform cannot tell, the value is
 * empty rather than a guess.
 *
 * @param tesseract the file defining {@code TessVersion}, if reported
 * @param leptonica the file defining {@code pixCreate}, if reported
 * @param codecs codec name to the file defining its entry point, for
 *               the codecs reachable from Tesseract; a codec linked
 *               statically into Leptonica reports Leptonica's file, and
 *               a codec absent from the dependency tree has no entry;
 *               empty as well when {@code tesseract} is, since then
 *               nothing could be looked up
 * @param tesseractVersion {@code TessVersion()} of the library that
 *                         answered
 */
public record Provenance(
        Optional<Path> tesseract,
        Optional<Path> leptonica,
        Map<String, Path> codecs,
        String tesseractVersion
) {

    public Provenance {
        Objects.requireNonNull(tesseract, "tesseract cannot be null");
        Objects.requireNonNull(leptonica, "leptonica cannot be null");
        Objects.requireNonNull(tesseractVersion, "tesseractVersion cannot be null");
        codecs = Map.copyOf(codecs);
    }

    /** One line, for a start-up log: versions, both libraries, codecs. */
    @Override
    public String toString() {
        String c = tesseract.isEmpty() ? "(not reported)"
                : codecs.isEmpty() ? "none resolved dynamically"
                : codecs.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", "));
        return "Tesseract " + tesseractVersion
                + " from " + tesseract.map(Path::toString).orElse("(not reported)")
                + "; Leptonica from " + leptonica.map(Path::toString).orElse("(not reported)")
                + "; codecs: " + c;
    }
}
