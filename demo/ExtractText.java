import org.oculix.octachorix.PageLevel;
import org.oculix.octachorix.PageSegMode;
import org.oculix.octachorix.Reading;
import org.oculix.octachorix.Scribe;
import org.oculix.octachorix.TextElement;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Ad-hoc Octachorix demo — takes an image path, extracts text.
 * Zero Legerix, zero tess4j. Just Octachorix + native Tesseract.
 *
 *   Usage: java -cp ... ExtractText <image-path> [lang]
 */
public class ExtractText {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ExtractText <image-path> [lang]");
            System.exit(2);
        }
        String imagePath = args[0];
        String lang = args.length > 1 ? args[1] : "eng";

        // Where UB Mannheim installer puts the DLLs by default.
        Path tesseract = Paths.get("C:/Program Files/Tesseract-OCR/libtesseract-5.dll");
        Path leptonica = Paths.get("C:/Program Files/Tesseract-OCR/libleptonica-6.dll");
        Path tessdata  = Paths.get("C:/Program Files/Tesseract-OCR/tessdata");

        BufferedImage img = ImageIO.read(new File(imagePath));
        if (img == null) {
            System.err.println("Not a readable image: " + imagePath);
            System.exit(3);
        }

        System.out.println("=== Octachorix demo ===");
        System.out.println("Image  : " + imagePath
                + " (" + img.getWidth() + "x" + img.getHeight()
                + ", type=" + img.getType() + ")");
        System.out.println("Lang   : " + lang);

        try (Scribe scribe = Scribe.builder()
                .tesseractLibrary(tesseract)
                .leptonicaLibrary(leptonica)
                .datapath(tessdata)
                .language(lang)
                .pageSegMode(PageSegMode.AUTO)
                .build()) {

            System.out.println("Tess   : " + scribe.tesseractVersion());
            System.out.println("-----------------------");

            long t0 = System.nanoTime();
            Reading r = scribe.read(img);
            long ms = (System.nanoTime() - t0) / 1_000_000L;

            String text = r.text().strip();
            System.out.println("Full text (" + ms + " ms):");
            System.out.println(text.isEmpty() ? "(no text found)" : text);
            System.out.println("-----------------------");

            List<TextElement> words = r.elements(PageLevel.WORD);
            System.out.println(words.size() + " word(s) with geometry:");
            for (TextElement w : words) {
                System.out.printf("  \"%s\"  @ [%d,%d %dx%d]  conf=%.1f%%%n",
                        w.text().strip(),
                        w.bbox().x, w.bbox().y,
                        w.bbox().width, w.bbox().height,
                        w.confidence());
            }
        }
    }
}
