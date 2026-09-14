package com.baseProject.myBaseProject.util.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // sắp xếp theo vị trí để đọc đúng thứ tự các cột
            stripper.setSortByPosition(true);

            return stripper.getText(document);
        }
    }
}
