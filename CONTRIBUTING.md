# Contributing to Octachorix

Thank you for your interest in contributing.

## Contributor Assignment Agreement (CAA)

Octachorix is licensed under the **GNU General Public License v3.0**.
To preserve the option of relicensing the project in the future — for
example, to offer a commercial license alongside the GPL for consumers
who cannot ship GPL-licensed code — the project uses a **Contributor
Assignment Agreement** in the spirit of the Free Software Foundation's
copyright assignment.

This is not a hoop. It is what keeps the project able to fund itself
without asking every past contributor for permission every time a
sustainability question comes up.

### Draft text

> This is a draft awaiting formal legal review before the first external
> contribution is accepted. The final wording may differ but will
> preserve the same intent.

---

**Octachorix Contributor Assignment Agreement (draft)**

I, the undersigned Contributor, hereby assign to Julien Mer (the
"Assignee") all right, title, and interest, worldwide, in and to my
Contribution to the Octachorix project, including all copyrights and
related rights, in every jurisdiction where such rights are recognized.

In return, the Assignee grants back to me a perpetual, worldwide,
royalty-free, non-exclusive, sublicensable license to use, reproduce,
prepare derivative works of, publicly display, publicly perform,
sublicense, and distribute my Contribution under any license I choose.
Nothing in this agreement prevents me from doing whatever I want with
my own Contribution outside the Octachorix project.

I represent and warrant that:

- I am legally entitled to make this assignment. My Contribution is
  my own original work, or I have secured all necessary rights from
  third parties, including my employer if my employer has any claim
  on work I produce.
- My Contribution does not, to the best of my knowledge, violate any
  patent, copyright, trade secret, or other proprietary right of any
  third party.
- My Contribution is provided under this agreement "as is", without
  warranty of any kind beyond what the GPL v3 itself provides.

Signed: [name, email, date]

---

### How to sign

For now, add a single line to the description of your first pull
request:

> I agree to the Octachorix Contributor Assignment Agreement as
> published in `CONTRIBUTING.md` at commit `<full-sha>`.

Referencing the exact commit SHA of `CONTRIBUTING.md` at the time of
signing pins the agreement text against future edits and protects both
sides.

A more formal signing process (electronic signature, CLA bot) will be
introduced when contribution volume justifies it.

## What we ask of contributions

- **Java 17 baseline.** No preview features. No JDK 21+ syntax.
- **One class per file.** Package-private helpers are welcome inside
  the same package.
- **No dependency on tess4j, lept4j, or any project that resolves
  native libraries by short name.** This is a hard rule, checked at
  review. See `ARCHITECTURE.txt` section 2.4.
- **All native loading MUST go through `NativeBond` with absolute
  paths.** No `System.loadLibrary(...)`. No `Native.load(shortName)`.
  Ever.
- **No PR without tests.** Unit tests at minimum. Integration tests
  against a real native Tesseract when the change touches the loader
  or the native surface (see `src/test/java/.../integration/`).

## Reporting bugs

Open an issue with:

- OS, distribution, JDK version, JNA version
- Tesseract version reported by `scribe.tesseractVersion()`
- The exact absolute paths passed to `Scribe.Builder`
- Minimal reproducer (self-contained `main` if possible)
- Full stack trace, and if the JVM crashed, the `hs_err_pid*.log`
  file (JVM crash dumps beat surefire dumpstreams every time —
  the memory-map section tells us which libraries were actually
  loaded)

## Coexistence with tess4j

If you land here from a tess4j background: welcome. Octachorix does
not seek to replace tess4j and does not compete for its niche. tess4j
is a mature, generalist Tesseract wrapper for the JVM; Octachorix is
a narrower deterministic runtime. Both projects can and should coexist
peacefully in the ecosystem, and code moves easily between them.

---

🦎
