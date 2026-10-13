package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationSlideLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chế độ "bố cục cố định": AI chỉ trả nội dung chữ của slide, server tự bố cục.
 *
 * JSON AI cần trả:
 * <pre>{"title": "...", "bullets": ["..."], "speakerNotes": "...", "sourceRefs": ["S1"]}</pre>
 *
 * So với schema cũ (AI tự đặt x/y/width/height/font):
 *   - output ngắn hơn nhiều (khoảng 250-450 token), ít bị cắt bởi max_tokens;
 *   - không còn lỗi "unknown field", "x + width exceeds canvas"...;
 *   - khoá trích dẫn vẫn nghiêm: slide nội dung bắt buộc có ít nhất 1 nguồn hợp lệ.
 *
 * Class thuần Java (nhận Map đã parse từ Jackson), không phụ thuộc Spring.
 */
public final class SlideContentComposer {

    public static final int MAX_TITLE_CHARS = 120;
    public static final int MAX_BULLETS = 8;
    public static final int MAX_BULLET_CHARS = 220;
    public static final int MAX_NOTES_BYTES = 65000;

    private static final Pattern SOURCE_REF = Pattern.compile("S(\\d{1,3})");
    /** Ký hiệu đầu dòng AI hay tự thêm: •, -, *, 1. , 2) ... (bắt buộc có khoảng trắng phía sau). */
    private static final Pattern LEADING_MARKER = Pattern.compile("^\\s*(?:[•·▪◦*\\-–—]+|\\d{1,2}[.)])\\s+");

    private SlideContentComposer() {}

    public static String systemPrompt() {
        return """
                You write the TEXT CONTENT of ONE presentation slide. Layout, positions, fonts and colors are handled by the system, so NEVER output coordinates, fonts, colors or shapes.

                OUTPUT FORMAT: a single raw JSON object, no markdown, no code fences, with exactly these keys:
                {"title": "...", "bullets": ["...", "..."], "speakerNotes": "...", "sourceRefs": ["S1"]}

                RULES:
                1. title: at most 70 characters. Keep the planned title unless it is clearly too long.
                2. bullets:
                   - Slide type TITLE or SECTION: at most ONE bullet, used as a short subtitle (or an empty array).
                   - Slide type CONTENT or SUMMARY: 3 to 6 bullets. Each bullet is one concise sentence or phrase of at most 140 characters.
                   - Do NOT add bullet symbols, numbering or markdown to the bullets.
                3. Use ONLY facts found in the Evidence. Do not invent facts, numbers or names.
                4. Write in the same language as the Content Points and the Evidence.
                5. sourceRefs: the evidence labels you used, written exactly like "S1", "S2". CONTENT and SUMMARY slides MUST cite at least one source. TITLE and SECTION slides may use an empty array.
                6. speakerNotes: 1 to 3 sentences the presenter could say, or null.
                """;
    }

    /** Lấy phần JSON giữa '{' đầu tiên và '}' cuối cùng (chịu được code fence hoặc lời dẫn thừa). */
    public static String extractJson(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a >= 0 && b > a) return s.substring(a, b + 1);
        return s;
    }

    /**
     * @param root          JSON AI trả về, đã parse thành Map
     * @param evidenceCount số đoạn bằng chứng đã đưa cho AI (nhãn hợp lệ là S1..S{evidenceCount})
     * @param plannedType   loại slide trong dàn ý: TITLE / SECTION / CONTENT / SUMMARY
     * @param plannedTitle  tiêu đề trong dàn ý (dự phòng khi AI không trả title)
     */
    public static ParsedSlideResult compose(Map<String, Object> root, int evidenceCount,
                                            String plannedType, String plannedTitle) {
        if (root == null) {
            throw new SlideGenerationException.SlideSchemaViolationException("LLM response is empty");
        }
        PresentationSlideLayout layout = layoutFor(plannedType);

        String title = clip(asText(root.get("title")), MAX_TITLE_CHARS);
        if (title.isBlank()) title = clip(plannedTitle == null ? "" : plannedTitle.trim(), MAX_TITLE_CHARS);
        if (title.isBlank()) {
            throw new SlideGenerationException.SlideSchemaViolationException("title is required");
        }

        List<String> extra = new ArrayList<>();
        List<String> bullets = readBullets(root.get("bullets"), extra);
        List<String> refs = readRefs(root.get("sourceRefs"), evidenceCount);

        boolean needsEvidence = layout == PresentationSlideLayout.TITLE_CONTENT
                || layout == PresentationSlideLayout.TWO_COLUMN
                || layout == PresentationSlideLayout.SUMMARY;
        if (needsEvidence && refs.isEmpty()) {
            throw new SlideGenerationException.SlideSchemaViolationException(
                    "Content requires evidence: at least one valid source reference is needed");
        }
        if (needsEvidence && bullets.isEmpty()) {
            throw new SlideGenerationException.SlideSchemaViolationException("Content slide has no bullets");
        }

        SlideLayoutEngine.Result laid = SlideLayoutEngine.layout(layout, title, bullets);

        // Defense in depth: bố cục sinh ra phải qua đúng bộ kiểm tra của pipeline.
        List<ParsedSlideResult.ParsedElement> elements = laid.elements();
        if (elements.size() > SlideElementValidator.MAX_ELEMENTS_PER_SLIDE) {
            throw new SlideGenerationException.SlideSchemaViolationException(
                    "Layout produced too many elements: " + elements.size());
        }
        for (int i = 0; i < elements.size(); i++) {
            ParsedSlideResult.ParsedElement e = elements.get(i);
            SlideElementValidator.validateGeometry(e.x(), e.y(), e.width(), e.height(), i);
            if ("TEXT".equals(e.type())) {
                SlideElementValidator.validateTextPayload(e.payload(), i);
                SlideElementValidator.validateTextStyle(e.style(), i);
            } else {
                SlideElementValidator.validateShapePayload(e.payload(), i);
                SlideElementValidator.validateShapeStyle(e.style(), i);
            }
        }

        // slide.title phải trùng khít với chữ của khung tiêu đề, nếu không exporter sẽ vẽ thêm một tiêu đề thứ hai.
        String finalTitle = (laid.fittedTitle() == null || laid.fittedTitle().isBlank()) ? title : laid.fittedTitle();
        extra.addAll(laid.overflowBullets());
        String notes = buildNotes(asText(root.get("speakerNotes")), extra);
        return new ParsedSlideResult(finalTitle, layout, notes, elements, refs);
    }

    // ---------------------------------------------------------------------

    static PresentationSlideLayout layoutFor(String plannedType) {
        if (plannedType == null) return PresentationSlideLayout.TITLE_CONTENT;
        return switch (plannedType) {
            case "TITLE" -> PresentationSlideLayout.TITLE;
            case "SECTION" -> PresentationSlideLayout.SECTION;
            case "SUMMARY" -> PresentationSlideLayout.SUMMARY;
            case "TWO_COLUMN" -> PresentationSlideLayout.TWO_COLUMN;
            default -> PresentationSlideLayout.TITLE_CONTENT;   // CONTENT và các giá trị khác
        };
    }

    private static List<String> readBullets(Object raw, List<String> extra) {
        List<String> items = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) items.add(asText(o));
        } else if (raw instanceof String s) {
            for (String line : s.split("\\r?\\n")) items.add(line);   // AI đôi khi trả 1 chuỗi nhiều dòng
        }
        List<String> out = new ArrayList<>();
        for (String item : items) {
            String cleaned = LEADING_MARKER.matcher(item == null ? "" : item).replaceFirst("").trim();
            if (cleaned.isEmpty()) continue;
            if (out.size() >= MAX_BULLETS) {
                extra.add(cleaned);
            } else {
                String display = clip(cleaned, MAX_BULLET_CHARS);
                out.add(display);
                if (!display.equals(cleaned)) extra.add(cleaned);
            }
        }
        return out;
    }

    /** Chuẩn hoá "[S1]", " s1 " -> "S1"; bỏ nhãn lạ (không ném lỗi); loại trùng. */
    private static List<String> readRefs(Object raw, int evidenceCount) {
        Set<String> refs = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String norm = asText(o).replaceAll("[\\[\\]\\s]", "").toUpperCase();
                var m = SOURCE_REF.matcher(norm);
                if (m.matches()) {
                    int n = Integer.parseInt(m.group(1));
                    if (n >= 1 && n <= evidenceCount) refs.add("S" + n);
                }
            }
        }
        return new ArrayList<>(refs);
    }

    private static String buildNotes(String notes, List<String> overflow) {
        StringBuilder sb = new StringBuilder(notes == null ? "" : notes.trim());
        if (overflow != null && !overflow.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Nội dung bổ sung (không vừa trên slide):");
            for (String o : overflow) sb.append("\n- ").append(o);
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) return null;
        if (result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_NOTES_BYTES) {
            throw new SlideGenerationException.SlideSchemaViolationException("speakerNotes exceeds 65,000 UTF-8 bytes");
        }
        return result;
    }

    private static String asText(Object o) {
        return o instanceof String s ? s.trim() : (o == null ? "" : String.valueOf(o).trim());
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max - 1);
        if (cut < max / 2) cut = max - 1;
        return t.substring(0, cut).trim() + "…";
    }
}
