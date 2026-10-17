package semfind;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Pulls plain text out of a file, page by page (DOCX/TXT/MD count as one page). */
public final class TextExtractor {
    public record Page(int number, String text) {}

    private TextExtractor() {}

    public static List<Page> extract(Path p) throws IOException {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf"))  return pdf(p);
        if (name.endsWith(".docx")) return docx(p);
        String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        return List.of(new Page(1, text));
    }

    private static List<Page> pdf(Path p) throws IOException {
        try (PDDocument doc = Loader.loadPDF(p.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<Page> pages = new ArrayList<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(new Page(i, stripper.getText(doc)));
            }
            return pages;
        }
    }

    private static List<Page> docx(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return List.of(new Page(1, ex.getText()));
        }
    }
}
