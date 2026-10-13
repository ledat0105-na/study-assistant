package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.ParsedDocument;
import com.example.studyassistant.retrieval.model.ParsedSegment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentParsingService {

    public ParsedDocument parse(Path filePath, String format) throws IOException {
        ParsedDocument doc = new ParsedDocument();

        switch (format) {
            case "PDF":
                parsePdf(filePath, doc);
                break;
            case "DOCX":
                parseDocx(filePath, doc);
                break;
            case "PPTX":
                parsePptx(filePath, doc);
                break;
            default:
                throw new IOException("Định dạng không hỗ trợ trích xuất: " + format);
        }

        return doc;
    }

    private void parsePdf(Path filePath, ParsedDocument doc) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            int totalPages = pdf.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdf);
                if (pageText != null && !pageText.trim().isEmpty()) {
                    doc.getSegments().add(ParsedSegment.builder()
                            .text(pageText.trim())
                            .pageStart(page)
                            .pageEnd(page)
                            .build());
                }
            }
        }
    }

    private void parseDocx(Path filePath, ParsedDocument doc) throws IOException {
        try (java.io.InputStream is = Files.newInputStream(filePath);
             org.apache.poi.xwpf.usermodel.XWPFDocument docx = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
             
            String currentSection = null;
            StringBuilder currentContent = new StringBuilder();

            for (org.apache.poi.xwpf.usermodel.IBodyElement element : docx.getBodyElements()) {
                if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph para) {
                    String style = para.getStyle();
                    String text = para.getText();
                    if (text == null || text.trim().isEmpty()) continue;
                    
                    // Basic heading detection for English and local POI styles
                    if (style != null && (style.startsWith("Heading") || style.contains("Tiu"))) {
                        if (currentContent.length() > 0) {
                            doc.getSegments().add(ParsedSegment.builder()
                                    .text(currentContent.toString().trim())
                                    .sectionTitle(currentSection)
                                    .isWord(true)
                                    .build());
                            currentContent.setLength(0);
                        }
                        currentSection = text.trim();
                    }
                    currentContent.append(text).append("\n");
                } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table) {
                    currentContent.append(table.getText()).append("\n");
                }
            }
            
            if (currentContent.length() > 0) {
                doc.getSegments().add(ParsedSegment.builder()
                        .text(currentContent.toString().trim())
                        .sectionTitle(currentSection)
                        .isWord(true)
                        .build());
            }
        }
    }

    private void parsePptx(Path filePath, ParsedDocument doc) throws IOException {
        try (java.io.InputStream is = Files.newInputStream(filePath);
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(is)) {
            
            int slideNo = 1;
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                StringBuilder slideText = new StringBuilder();
                for (org.apache.poi.sl.usermodel.Shape<?, ?> shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                        slideText.append(textShape.getText()).append("\n");
                    }
                }
                
                String text = slideText.toString().trim();
                if (!text.isEmpty()) {
                    doc.getSegments().add(ParsedSegment.builder()
                            .text(text)
                            .pageStart(slideNo)
                            .pageEnd(slideNo)
                            .isSlide(true)
                            .build());
                }
                slideNo++;
            }
        }
    }
}
