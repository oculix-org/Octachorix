/*
 * Octachorix — deterministic Tesseract runtime for the JVM.
 * Copyright (C) 2026 Julien Mer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.oculix.octachorix;

/**
 * The single unchecked exception carrying every runtime failure from
 * Octachorix.
 *
 * <p>Runtime, because Tesseract-native failures — a library missing at
 * the given absolute path, a botched init, a crash during recognition —
 * are rarely worth handling at every call site. Fail loud, fail high,
 * let the consumer decide where (or whether) to catch.
 *
 * <p>Consumers migrating from tess4j replace
 * {@code net.sourceforge.tess4j.TesseractException} with this class.
 * The semantics are the same: something went wrong down in the native
 * layer, and the message will tell you what.
 */
public final class OctachorixFault extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OctachorixFault(String message) {
        super(message);
    }

    public OctachorixFault(String message, Throwable cause) {
        super(message, cause);
    }
}
