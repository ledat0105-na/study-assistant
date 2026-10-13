package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.PresentationSlideLayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ bố cục cố định cho slide (canvas 960 x 540 pt, cùng hệ toạ độ với export PPTX).
 *
 * AI chỉ cung cấp NỘI DUNG (tiêu đề + các ý chính). Toàn bộ vị trí, kích thước, cỡ chữ và
 * màu sắc do class này tính, nên:
 *   - chữ không tràn khung, các khối không chồng lên nhau (khối được xếp dọc theo chiều cao ước lượng);
 *   - luôn hợp lệ với {@link SlideElementValidator};
 *   - kết quả lặp lại được (cùng đầu vào cho cùng bố cục).
 *
 * Mỗi ý chính gồm 1 chấm tròn (SHAPE/ELLIPSE) + 1 khung chữ (TEXT) riêng, nên không phụ thuộc
 * việc exporter xử lý nhiều đoạn văn hay định dạng bullet.
 *
 * Ước lượng độ rộng chữ dựa trên số đo thật của Arial/Liberation Sans với tiếng Việt, cộng biên an toàn.
 * Class thuần Java, không phụ thuộc Spring, dễ kiểm thử.
 */
public final class SlideLayoutEngine {

    /**
     * @param overflowBullets các ý không vừa slide; nên được đưa vào ghi chú diễn giả để không mất nội dung.
     * @param fittedTitle     đúng chuỗi chữ của khung tiêu đề đã đặt lên slide (có thể bị rút gọn bằng "…").
     *                        Exporter chỉ coi slide đã có tiêu đề khi có khung chữ TRÙNG KHÍT với slide.title,
     *                        nên slide.title phải được đặt bằng giá trị này.
     */
    public record Result(List<ParsedSlideResult.ParsedElement> elements, List<String> overflowBullets, String fittedTitle) {}

    // ---- Canvas ----
    static final int W = 960;
    static final int H = 540;
    static final int MARGIN_X = 56;
    static final int CONTENT_W = W - 2 * MARGIN_X;   // 848
    static final int CONTENT_BOTTOM = 500;           // chừa 40pt phía dưới

    // ---- Typography ----
    static final double LINE_HEIGHT = 1.25;          // chiều cao dòng so với cỡ chữ (an toàn hơn 1.15 của Arial)
    static final double WRAP_SAFETY = 0.94;          // chỉ dùng 94% bề rộng khung khi ước lượng xuống dòng
    static final double BULLET_GAP = 0.55;           // khoảng cách giữa các ý = 0.55 x cỡ chữ
    static final int BULLET_INDENT = 28;             // cột chứa chấm tròn

    // ---- Palette ----
    static final String INK = "#1E293B";
    static final String BODY = "#334155";
    static final String MUTED = "#475569";
    static final String ACCENT = "#4F46E5";
    static final String ACCENT_SUMMARY = "#059669";
    static final String DARK_BG = "#1E1B4B";
    static final String DARK_ACCENT = "#818CF8";
    static final String DARK_SUBTITLE = "#C7D2FE";
    static final String LIGHT_BG = "#F8FAFC";
    static final String WHITE = "#FFFFFF";

    private SlideLayoutEngine() {}

    public static Result layout(PresentationSlideLayout layout, String title, List<String> bullets) {
        String safeTitle = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        List<String> safeBullets = new ArrayList<>();
        if (bullets != null) {
            for (String b : bullets) {
                if (b != null && !b.isBlank()) safeBullets.add(b.trim().replaceAll("\\s+", " "));
            }
        }
        PresentationSlideLayout l = layout == null ? PresentationSlideLayout.TITLE_CONTENT : layout;
        return switch (l) {
            case TITLE -> titleSlide(safeTitle, safeBullets);
            case SECTION -> sectionSlide(safeTitle, safeBullets);
            case SUMMARY -> contentSlide(safeTitle, safeBullets, ACCENT_SUMMARY, false);
            case TWO_COLUMN -> contentSlide(safeTitle, safeBullets, ACCENT, true);
            default -> contentSlide(safeTitle, safeBullets, ACCENT, false);   // TITLE_CONTENT, IMAGE_TEXT, BLANK
        };
    }

    // =====================================================================
    // Các loại slide
    // =====================================================================

    private static Result titleSlide(String title, List<String> bullets) {
        List<ParsedSlideResult.ParsedElement> out = new ArrayList<>();
        out.add(rect(0, 0, W, H, DARK_BG));

        TextFit t = fitSingle(title, true, CONTENT_W, 56, 34, 3);
        TextFit sub = bullets.isEmpty() ? null : fitSingle(bullets.get(0), false, CONTENT_W, 26, 18, 2);

        double titleH = t.height();
        double subH = sub == null ? 0 : sub.height();
        double block = 6 + 24 + titleH + (sub == null ? 0 : 20 + subH);
        double y = Math.max(40, (H - block) / 2.0);

        out.add(rect(MARGIN_X, y, 96, 6, DARK_ACCENT));
        y += 6 + 24;
        if (!t.text().isEmpty()) {
            out.add(text(MARGIN_X, y, CONTENT_W, titleH, t.text(), t.fontSize(), true, WHITE, "LEFT"));
        }
        y += titleH;
        if (sub != null) {
            y += 20;
            out.add(text(MARGIN_X, y, CONTENT_W, subH, sub.text(), sub.fontSize(), false, DARK_SUBTITLE, "LEFT"));
        }
        return new Result(out, subtitleOverflow(bullets, sub), t.text());
    }

    private static Result sectionSlide(String title, List<String> bullets) {
        List<ParsedSlideResult.ParsedElement> out = new ArrayList<>();
        out.add(rect(0, 0, W, H, LIGHT_BG));
        out.add(rect(0, 0, 28, H, ACCENT));

        int x = 96;
        int w = W - x - MARGIN_X;                         // 808
        TextFit t = fitSingle(title, true, w, 48, 30, 3);
        TextFit sub = bullets.isEmpty() ? null : fitSingle(bullets.get(0), false, w, 24, 18, 2);

        double block = t.height() + (sub == null ? 0 : 18 + sub.height());
        double y = Math.max(40, (H - block) / 2.0);

        if (!t.text().isEmpty()) {
            out.add(text(x, y, w, t.height(), t.text(), t.fontSize(), true, INK, "LEFT"));
        }
        y += t.height();
        if (sub != null) {
            y += 18;
            out.add(text(x, y, w, sub.height(), sub.text(), sub.fontSize(), false, MUTED, "LEFT"));
        }
        return new Result(out, subtitleOverflow(bullets, sub), t.text());
    }

    private static List<String> subtitleOverflow(List<String> bullets, TextFit subtitle) {
        List<String> extra = new ArrayList<>();
        if (!bullets.isEmpty() && subtitle != null && !subtitle.text().equals(bullets.get(0))) {
            extra.add(bullets.get(0));
        }
        if (bullets.size() > 1) extra.addAll(bullets.subList(1, bullets.size()));
        return extra;
    }

    private static Result contentSlide(String title, List<String> bullets, String accent, boolean twoColumns) {
        List<ParsedSlideResult.ParsedElement> out = new ArrayList<>();
        out.add(rect(0, 0, W, 10, accent));

        double y = 36;
        TextFit t = fitSingle(title, true, CONTENT_W, 34, 24, 2);
        if (!t.text().isEmpty()) {
            out.add(text(MARGIN_X, y, CONTENT_W, t.height(), t.text(), t.fontSize(), true, INK, "LEFT"));
            y += t.height();
        }
        y += 12;
        out.add(rect(MARGIN_X, y, 96, 4, accent));
        y += 4 + 28;

        double regionH = CONTENT_BOTTOM - y;
        List<String> overflow = new ArrayList<>();

        if (bullets.isEmpty()) {
            return new Result(out, overflow, t.text());
        }

        if (twoColumns && bullets.size() >= 2) {
            int gap = 40;
            int colW = (CONTENT_W - gap) / 2;             // 404
            int split = (bullets.size() + 1) / 2;
            List<String> left = bullets.subList(0, split);
            List<String> right = bullets.subList(split, bullets.size());
            BulletPlan pl = planBullets(left, colW - BULLET_INDENT, regionH, 24, 16);
            BulletPlan pr = planBullets(right, colW - BULLET_INDENT, regionH, 24, 16);
            int fs = Math.min(pl.fontSize(), pr.fontSize());   // chữ nhỏ hơn luôn vừa, vì chiều cao giảm theo cỡ chữ
            placeBullets(out, pl.kept(), fs, MARGIN_X, colW, y, accent);
            placeBullets(out, pr.kept(), fs, MARGIN_X + colW + gap, colW, y, accent);
            overflow.addAll(pl.overflow());
            overflow.addAll(pr.overflow());
        } else {
            BulletPlan p = planBullets(bullets, CONTENT_W - BULLET_INDENT, regionH, 28, 16);
            placeBullets(out, p.kept(), p.fontSize(), MARGIN_X, CONTENT_W, y, accent);
            overflow.addAll(p.overflow());
        }
        return new Result(out, overflow, t.text());
    }

    // =====================================================================
    // Xếp các ý chính
    // =====================================================================

    private record BulletPlan(int fontSize, List<String> kept, List<String> overflow) {}

    /** Chọn cỡ chữ lớn nhất vừa vùng; nếu cỡ nhỏ nhất vẫn không vừa thì đẩy các ý cuối sang overflow. */
    static BulletPlan planBullets(List<String> bullets, double textW, double regionH, int maxFs, int minFs) {
        for (int fs = maxFs; fs >= minFs; fs -= 2) {
            if (bulletsHeight(bullets, fs, textW) <= regionH) {
                return new BulletPlan(fs, new ArrayList<>(bullets), new ArrayList<>());
            }
        }
        List<String> kept = new ArrayList<>(bullets);
        List<String> overflow = new ArrayList<>();
        while (kept.size() > 1 && bulletsHeight(kept, minFs, textW) > regionH) {
            overflow.add(0, kept.remove(kept.size() - 1));
        }
        if (bulletsHeight(kept, minFs, textW) > regionH) {
            // còn đúng 1 ý quá dài: cắt gọn để vừa, giữ bản đầy đủ trong overflow
            String only = kept.get(0);
            overflow.add(0, only);
            int maxLines = Math.max(1, (int) Math.floor(regionH / (minFs * LINE_HEIGHT)));
            kept = new ArrayList<>(List.of(truncateToLines(only, minFs, false, textW, maxLines)));
        }
        return new BulletPlan(minFs, kept, overflow);
    }

    static double bulletsHeight(List<String> bullets, int fs, double textW) {
        double h = 0;
        for (int i = 0; i < bullets.size(); i++) {
            h += Math.ceil(countLines(bullets.get(i), fs, false, textW) * fs * LINE_HEIGHT);
            if (i < bullets.size() - 1) h += fs * BULLET_GAP;
        }
        return h;
    }

    private static void placeBullets(List<ParsedSlideResult.ParsedElement> out, List<String> bullets, int fs,
                                     double x, double colWidth, double top, String accent) {
        double textW = colWidth - BULLET_INDENT;
        double dot = Math.max(8, Math.round(fs * 0.38));
        double y = top;
        for (String b : bullets) {
            double h = Math.ceil(countLines(b, fs, false, textW) * fs * LINE_HEIGHT);
            double dotY = y + (fs * LINE_HEIGHT - dot) / 2.0;
            out.add(shape("ELLIPSE", x, dotY, dot, dot, accent));
            out.add(text(x + BULLET_INDENT, y, textW, h, b, fs, false, BODY, "LEFT"));
            y += h + fs * BULLET_GAP;
        }
    }

    // =====================================================================
    // Đo và vừa chữ
    // =====================================================================

    private record TextFit(String text, int fontSize, int lines) {
        double height() { return Math.ceil(lines * fontSize * LINE_HEIGHT); }
    }

    /** Chọn cỡ chữ lớn nhất sao cho không quá maxLines; nếu vẫn dài thì hạ thêm cỡ chữ và cho thêm 1 dòng; cuối cùng mới cắt và thêm "…". */
    private static TextFit fitSingle(String text, boolean bold, double boxW, int maxFs, int minFs, int maxLines) {
        if (text == null || text.isBlank()) return new TextFit("", minFs, 0);
        for (int fs = maxFs; fs >= minFs; fs -= 2) {
            int lines = countLines(text, fs, bold, boxW);
            if (lines <= maxLines) return new TextFit(text, fs, lines);
        }
        // Dự phòng: thu nhỏ thêm vài nấc và cho phép thêm 1 dòng, để khỏi mất chữ cuối của tiêu đề.
        int floor = Math.max(18, minFs - 6);
        for (int fs = minFs - 2; fs >= floor; fs -= 2) {
            int lines = countLines(text, fs, bold, boxW);
            if (lines <= maxLines + 1) return new TextFit(text, fs, lines);
        }
        String cut = truncateToLines(text, floor, bold, boxW, maxLines + 1);
        return new TextFit(cut, floor, countLines(cut, floor, bold, boxW));
    }

    static String truncateToLines(String text, int fs, boolean bold, double boxW, int maxLines) {
        String[] words = text.trim().split("\\s+");
        int n = words.length;
        while (n > 1) {
            String candidate = String.join(" ", java.util.Arrays.copyOf(words, n)) + "…";
            if (countLines(candidate, fs, bold, boxW) <= maxLines) return candidate;
            n--;
        }
        // 1 từ quá dài: cắt theo ký tự
        String w = words[0];
        while (w.length() > 1 && countLines(w + "…", fs, bold, boxW) > maxLines) {
            w = w.substring(0, w.length() - 1);
        }
        return w + "…";
    }

    /**
     * Độ rộng tiến (advance) của Arial / Liberation Sans, đơn vị 1/2048 em, cho ASCII 32..126.
     * Lấy trực tiếp từ font (Liberation Sans là bản thay thế đo-tương-đương Arial), nên chữ hoa rộng như W, M
     * và chữ hẹp như i, l được tính đúng thay vì dùng giá trị trung bình.
     */
    private static final int[] ADV_REGULAR = {
            569, 569, 727, 1139, 1139, 1821, 1366, 391, 682, 682, 797, 1196,
            569, 682, 569, 569, 1139, 1139, 1139, 1139, 1139, 1139, 1139, 1139,
            1139, 1139, 569, 569, 1196, 1196, 1196, 1139, 2079, 1366, 1366, 1479,
            1479, 1366, 1251, 1593, 1479, 569, 1024, 1366, 1139, 1706, 1479, 1593,
            1366, 1593, 1479, 1366, 1251, 1479, 1366, 1933, 1366, 1366, 1251, 569,
            569, 569, 961, 1139, 682, 1139, 1139, 1024, 1139, 1139, 569, 1139,
            1139, 455, 455, 1024, 455, 1706, 1139, 1139, 1139, 1139, 682, 1024,
            569, 1139, 1024, 1479, 1024, 1024, 1024, 684, 532, 684, 1196
    };
    private static final int[] ADV_BOLD = {
            569, 682, 971, 1139, 1139, 1821, 1479, 487, 682, 682, 797, 1196,
            569, 682, 569, 569, 1139, 1139, 1139, 1139, 1139, 1139, 1139, 1139,
            1139, 1139, 682, 682, 1196, 1196, 1196, 1251, 1997, 1479, 1479, 1479,
            1479, 1366, 1251, 1593, 1479, 569, 1139, 1479, 1251, 1706, 1479, 1593,
            1366, 1593, 1479, 1366, 1251, 1479, 1366, 1933, 1366, 1366, 1251, 682,
            569, 682, 1196, 1139, 682, 1139, 1251, 1139, 1251, 1139, 682, 1251,
            1251, 569, 569, 1139, 569, 1821, 1251, 1251, 1251, 1251, 797, 1139,
            682, 1251, 1139, 1593, 1139, 1139, 1024, 797, 573, 797, 1196
    };
    private static final double UNITS = 2048.0;
    private static final java.util.concurrent.ConcurrentHashMap<Long, Double> NON_ASCII_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    static double charWidthEm(char c, boolean bold) {
        int[] t = bold ? ADV_BOLD : ADV_REGULAR;
        if (c >= 32 && c <= 126) return t[c - 32] / UNITS;
        long key = ((long) c << 1) | (bold ? 1 : 0);
        return NON_ASCII_CACHE.computeIfAbsent(key, k -> nonAsciiWidthEm(c, bold));
    }

    private static double nonAsciiWidthEm(char c, boolean bold) {
        switch (c) {
            case '\u2018': case '\u2019': return (bold ? 569 : 455) / UNITS;       // ' '
            case '\u201c': case '\u201d': return (bold ? 1024 : 682) / UNITS;      // " "
            case '\u2013': return 1139 / UNITS;                                      // –
            case '\u2014': case '\u2026': case '\u2192': return 1.0;               // — … →
            case '\u2022': return 717 / UNITS;                                       // •
            case '\u00b7': return 682 / UNITS;                                       // ·
            case '\u00d7': return 1196 / UNITS;                                      // ×
            case '\u00b0': return 819 / UNITS;                                       // °
            case '\u00a0': return 569 / UNITS;                                       // nbsp
            case '\u0111': return (bold ? 1251 : 1139) / UNITS;                      // đ
            case '\u0110': return 1479 / UNITS;                                      // Đ
            default: break;
        }
        // Chữ có dấu (tiếng Việt, Latin mở rộng): dấu không đổi độ rộng, lấy theo chữ cái gốc sau khi tách dấu.
        String decomposed = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD);
        char base = decomposed.charAt(0);
        int[] t = bold ? ADV_BOLD : ADV_REGULAR;
        if (base >= 32 && base <= 126) return t[base - 32] / UNITS;
        if (c >= 0x2E80) return 1.0;                                                  // CJK
        if (Character.isLetter(c)) return Character.isUpperCase(c) ? 0.78 : (bold ? 0.62 : 0.58);
        return 0.6;                                                                   // ký hiệu lạ: ước lượng rộng
    }

    static double wordWidth(String word, double fs, boolean bold) {
        double em = 0;
        for (int i = 0; i < word.length(); i++) em += charWidthEm(word.charAt(i), bold);
        return em * fs;
    }

    /** Ước lượng số dòng khi xuống dòng theo từ (greedy). Luôn >= 1 với văn bản không rỗng. */
    static int countLines(String text, double fs, boolean bold, double boxW) {
        if (text == null || text.isBlank()) return 0;
        double usable = boxW * WRAP_SAFETY;
        double space = charWidthEm(' ', bold) * fs;
        int lines = 1;
        double cur = 0;
        for (String word : text.trim().split("\\s+")) {
            double w = wordWidth(word, fs, bold);
            if (w > usable) {                              // từ dài hơn cả dòng (URL, mã…): bị bẻ nhiều dòng
                if (cur > 0) lines++;
                int parts = (int) Math.ceil(w / usable);
                lines += parts - 1;
                cur = w - (parts - 1) * usable;
            } else if (cur == 0) {
                cur = w;
            } else if (cur + space + w <= usable) {
                cur += space + w;
            } else {
                lines++;
                cur = w;
            }
        }
        return lines;
    }

    // =====================================================================
    // Dựng element (làm tròn về số nguyên pt, luôn nằm trong canvas)
    // =====================================================================

    private static ParsedSlideResult.ParsedElement text(double x, double y, double w, double h, String text,
                                                         int fontSize, boolean bold, String color, String align) {
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("fontSizePt", fontSize);
        style.put("bold", bold);
        style.put("textAlign", align);
        style.put("color", color);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        return element("TEXT", x, y, w, h, payload, style);
    }

    private static ParsedSlideResult.ParsedElement shape(String primitive, double x, double y, double w, double h,
                                                          String fill) {
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("fillColor", fill);
        style.put("strokeColor", fill);
        style.put("strokeWidthPt", 0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("primitive", primitive);
        return element("SHAPE", x, y, w, h, payload, style);
    }

    private static ParsedSlideResult.ParsedElement rect(double x, double y, double w, double h, String fill) {
        return shape("RECTANGLE", x, y, w, h, fill);
    }

    private static ParsedSlideResult.ParsedElement element(String type, double x, double y, double w, double h,
                                                            Map<String, Object> payload, Map<String, Object> style) {
        int ix = (int) Math.max(0, Math.min(W - 1, Math.floor(x)));
        int iy = (int) Math.max(0, Math.min(H - 1, Math.floor(y)));
        int iw = (int) Math.max(1, Math.min(W - ix, Math.ceil(w)));
        int ih = (int) Math.max(1, Math.min(H - iy, Math.ceil(h)));
        return new ParsedSlideResult.ParsedElement(type,
                BigDecimal.valueOf(ix), BigDecimal.valueOf(iy), BigDecimal.valueOf(iw), BigDecimal.valueOf(ih),
                payload, style);
    }
}
