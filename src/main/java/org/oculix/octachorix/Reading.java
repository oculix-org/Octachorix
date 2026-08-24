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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The result of a single call to {@link Scribe#read(BufferedImage)}.
 *
 * <p>A {@code Reading} carries the full recognized text as a single
 * string plus a flat list of {@link TextElement}s spanning every
 * {@link PageLevel} — from {@link PageLevel#BLOCK} down to
 * {@link PageLevel#SYMBOL}. The whole tree is produced by a single
 * recognition pass in the underlying Tesseract engine, then walked
 * by the iterator to populate each level. Extracting more granularity
 * from an existing {@code Reading} does not trigger another OCR pass.
 *
 * <p>Records are shallowly immutable in Java; the constructor here
 * additionally wraps the incoming {@code elements} in an
 * unmodifiable copy, so a mutation of the caller's list cannot bleed
 * into an already-returned {@code Reading}.
 *
 * @param text the flat recognized text — never null, may be empty
 * @param elements every extracted region across every
 *                 {@link PageLevel}, in Tesseract's iteration order
 */
public record Reading(
        String text,
        List<TextElement> elements
) {

    public Reading {
        Objects.requireNonNull(text, "Reading.text cannot be null");
        Objects.requireNonNull(elements, "Reading.elements cannot be null");
        elements = List.copyOf(elements);
    }

    /**
     * Returns the subset of {@link #elements()} at the given
     * {@link PageLevel}.
     *
     * <p>This is a pure filter over the already-computed flat list:
     * it does not touch the native side and does not trigger any
     * additional recognition work. Call it as often as you like.
     *
     * @param level the segmentation level to keep
     * @return an unmodifiable list of elements at that level, in the
     *         same order they appeared in {@link #elements()}
     */
    public List<TextElement> elements(PageLevel level) {
        Objects.requireNonNull(level, "PageLevel filter cannot be null");
        return elements.stream()
                .filter(e -> e.level() == level)
                .collect(Collectors.toUnmodifiableList());
    }
}
