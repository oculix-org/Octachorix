/**
 * Octachorix — deterministic, session-oriented, geometry-first
 * Tesseract runtime for the JVM.
 *
 * <h2>What lives here</h2>
 * <ul>
 *   <li>{@link org.oculix.octachorix.Scribe} — the session facade,
 *       built via {@link org.oculix.octachorix.Scribe.Builder}, closed
 *       when done, reused for as many reads as needed.</li>
 *   <li>{@link org.oculix.octachorix.Reading} and
 *       {@link org.oculix.octachorix.TextElement} — the result records.</li>
 *   <li>{@link org.oculix.octachorix.PageLevel},
 *       {@link org.oculix.octachorix.PageSegMode},
 *       {@link org.oculix.octachorix.OcrEngineMode} — the typed enums
 *       replacing the C API's magic {@code int} values.</li>
 *   <li>{@link org.oculix.octachorix.NativeBond} — the loader. Absolute
 *       path in, JNA interface out, no fallback in between.</li>
 *   <li>{@link org.oculix.octachorix.Hypercube} — the JNA bindings against
 *       Tesseract's public C API header {@code capi.h}.</li>
 *   <li>{@link org.oculix.octachorix.ImageBridge} and
 *       {@link org.oculix.octachorix.PixelBuffer} — the bridge from
 *       {@link java.awt.image.BufferedImage} to a raw buffer
 *       {@code TessBaseAPISetImage} accepts.</li>
 *   <li>{@link org.oculix.octachorix.OctachorixFault} — the one
 *       unchecked exception carrying every runtime failure.</li>
 * </ul>
 *
 * <h2>Doctrine</h2>
 * <p>All native loading goes through {@link org.oculix.octachorix.NativeBond}
 * with absolute paths. There is no {@code Native.load(shortName)} anywhere
 * in this package, and there never will be. See {@code ARCHITECTURE.txt}
 * for the {@code Legerix#20} war story that made this rule non-negotiable.
 *
 * <h2>Provenance</h2>
 * <p>The JNA bindings in {@link org.oculix.octachorix.Hypercube} are
 * written directly against the upstream Tesseract C API header
 * ({@code capi.h}, publicly documented). No {@code tess4j} source code
 * is copied or adapted. {@code tess4j} remains an excellent generalist
 * Tesseract wrapper for the JVM; Octachorix simply optimises for a
 * different set of trade-offs.
 */
package org.oculix.octachorix;
