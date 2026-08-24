# Octachorix

[![Status](https://img.shields.io/badge/status-planted%20flag-d97706?style=flat-square&logo=github)](.)
[![JDK](https://img.shields.io/badge/JDK-17%2B-1f883d?style=flat-square&logo=openjdk)](.)
[![License](https://img.shields.io/badge/license-GPL%20v3-6f42c1?style=flat-square&logo=gnu)](LICENSE)
[![Upstream](https://img.shields.io/badge/upstream-Tesseract%205.x-0a66c2?style=flat-square)](https://github.com/tesseract-ocr/tesseract)

> Deterministic, session-oriented, geometry-first Tesseract runtime for the JVM.

## Why this exists

On any Unix host with a system Tesseract already installed, embedding a
different Tesseract in a Java application is a landmine. The JVM's usual
native-loading path leans on short-name resolution — `Native.load("tesseract")`
— and JNA's fallback picks the highest-versioned candidate it finds anywhere
on the loader search path. The bundled library loses; the system library
wins; a second Leptonica gets dragged in with a different ABI; `pixDestroy`
segfaults on the first cross-boundary destroy.

Octachorix takes a different route: **absolute paths only**. The caller
tells Octachorix where the shared library lives on disk, and Octachorix
loads exactly that file with no fallback whatsoever. No short-name lookup.
No `LD_LIBRARY_PATH` scan. No version-max heuristic. If the file is not
where the caller said it was, the library fails loud at boot; it does not
silently reach for whatever else the system has to offer.

This makes Octachorix the natural pair to a native provisioner like Legerix
(which extracts a known Tesseract and Leptonica to a known cache
directory) and keeps the whole chain deterministic end to end.

## What this is, and what it is not

This is **not** a tess4j replacement in features. tess4j is a mature,
generalist Java wrapper around Tesseract; it supports PDF-searchable
output, TIFF multipage, OSD detection, and multiple result renderers
(hOCR, ALTO, TSV). If those matter to your project, tess4j remains
the right tool.

Octachorix is deliberately narrower. It focuses on:

- Loading Tesseract by strict absolute path on Linux, macOS, and Windows
- Exposing a session-oriented Java API (`AutoCloseable`, builder-based)
- Returning text **and** geometry **and** confidences in a single
  recognition pass
- Modern type system: enums for `PageSegMode` / `OcrEngineMode`,
  records for `Reading` / `TextElement`
- Zero runtime dependency on tess4j, lept4j, or any project that
  resolves natives by short name

## Usage

```java
try (Scribe scribe = Scribe.builder()
        .tesseractLibrary(Path.of("/abs/path/libtesseract.so.5"))
        .leptonicaLibrary(Path.of("/abs/path/libleptonica.so.6"))
        .datapath(Path.of("/abs/path/tessdata"))
        .language("eng")
        .pageSegMode(PageSegMode.SPARSE_TEXT)
        .build()) {

    Reading r = scribe.read(myImage);

    String text = r.text();
    List<TextElement> words = r.elements(PageLevel.WORD);

    // Multi-language on the same underlying natives:
    try (Scribe fr = scribe.withLanguage("fra")) {
        Reading rf = fr.read(otherImage);
    }
}
```

## Status

Planted flag. Under active initial development. See
[`ARCHITECTURE.txt`](ARCHITECTURE.txt) for the design record — kept in
the repository as a living document of what was decided and why.

## Independent implementation notice

Octachorix is written against the upstream **Tesseract C API**
(`capi.h`, publicly documented). No tess4j source code is copied or
adapted. tess4j remains a fine choice for its own set of trade-offs;
Octachorix simply optimises for a different set.

## License

GPL v3. See [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the Contributor Assignment
Agreement.

---

🦎
