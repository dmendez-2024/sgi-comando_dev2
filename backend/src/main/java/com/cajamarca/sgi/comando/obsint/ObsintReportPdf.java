package com.cajamarca.sgi.comando.obsint;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Dibuja el "Informe Diario de Consignas" con el diseño del ejemplo (diapositiva 13): A4, cabecera oscura, tarjetas y tablas. */
final class ObsintReportPdf {
    private static final float W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight(), M = 28, CW = W - 2 * M, FOOTER = 54;
    private static final Color NAVY = new Color(0x1B2635), TEXT = new Color(0x1F2937), MUTED = new Color(0x6B7280), LINE = new Color(0xE3E8EF),
        SOFT = new Color(0xF4F6F9), GREEN = new Color(0x1E9E52), RED = new Color(0xD9343A), AMBER = new Color(0xE5A11A), PINK = new Color(0xFDECEC),
        WHITE = Color.WHITE, HEAD_MUTED = new Color(0xB8C2D0);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-EC"));
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-EC")).withZone(ObsintPlatform.ZONE);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-EC"));

    private final PDDocument doc = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final ObsintDailyReport.Report r;
    private final PDImageXObject logo;
    private final List<PDPage> pages = new ArrayList<>();
    private PDPageContentStream cs;
    private float y;

    private ObsintReportPdf(ObsintDailyReport.Report report) throws IOException {
        this.r = report;
        try (InputStream in = ObsintReportPdf.class.getResourceAsStream("/obsint/sgi-comando-logo.png")) {
            logo = in == null ? null : PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "logo");
        }
    }

    static byte[] render(ObsintDailyReport.Report report) {
        try {
            ObsintReportPdf pdf = new ObsintReportPdf(report);
            try (pdf.doc) { return pdf.draw(); }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] draw() throws IOException {
        newPage();
        header();
        summary();
        consignas();
        failures();
        observations();
        cs.close();
        footers();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    // ── Secciones ────────────────────────────────────────────────────────────────────────────────

    private void header() throws IOException {
        rect(0, H - 118, W, 118, NAVY);
        if (logo != null) cs.drawImage(logo, M, H - 108, 98, 98);
        text("OBSINT", bold, 30, M + 116, H - 58, WHITE);
        text("Informe Diario de Consignas", regular, 16, M + 116, H - 84, WHITE);
        float bx = W - M - 190;
        roundRect(bx, H - 104, 190, 84, 6, new Color(0x243246));
        text("FECHA DEL REPORTE", regular, 7.5f, bx + 14, H - 38, HEAD_MUTED);
        text(LONG_DATE.format(r.date()), regular, 13, bx + 14, H - 54, WHITE);
        text("TURNO", regular, 7.5f, bx + 14, H - 74, HEAD_MUTED);
        text("00:00 – 24:00", regular, 13, bx + 14, H - 90, WHITE);

        y = H - 136;
        String[][] cells = {{"CLIENTE", r.client()}, {"PUNTO", r.point()}, {"SERVICIO", r.service()}, {"GENERADO POR", "OBSINT"},
            {"FECHA DE GENERACIÓN", SHORT.format(r.generatedAt())}};
        float[] widths = {110, 110, 150, 72, 97};
        float x = M;
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) line(x - 8, y + 4, x - 8, y - 26, LINE);
            text(cells[i][0], regular, 7, x, y, MUTED);
            List<String> v = wrap(cells[i][1], bold, 10, widths[i] - 12);
            for (int j = 0; j < Math.min(2, v.size()); j++) text(v.get(j), bold, 10, x, y - 14 - j * 12, TEXT);
            x += widths[i];
        }
        y -= 46;
    }

    private void summary() throws IOException {
        ObsintDailyReport.Summary s = r.summary();
        float boxH = 128;
        roundRect(M, y - boxH, CW, boxH, 8, SOFT);
        text("Resumen General", bold, 15, M + 12, y - 22, TEXT);
        float tw = (CW - 24 - 5 * 8) / 6, ty = y - 118, th = 82;
        String avg = s.averageSeconds() == null ? "—" : "%d:%02d".formatted(s.averageSeconds() / 60, s.averageSeconds() % 60);
        int decided = s.yes() + s.no();
        tile(M + 12, ty, tw, th, "CONSIGNAS\nCONFIGURADAS", String.valueOf(s.configured()), null, MUTED, 'c');
        tile(M + 12 + (tw + 8), ty, tw, th, "CONSIGNAS\nEJECUTADAS", String.valueOf(s.executed()),
            s.uncertain() > 0 ? s.uncertain() + " incierta" + (s.uncertain() == 1 ? "" : "s") : null, MUTED, 'c');
        tile(M + 12 + 2 * (tw + 8), ty, tw, th, "CUMPLIDAS", String.valueOf(s.yes()), decided == 0 ? "—" : pct((double) s.yes() / decided), GREEN, 'v');
        tile(M + 12 + 3 * (tw + 8), ty, tw, th, "INCUMPLIDAS", String.valueOf(s.no()), decided == 0 ? "—" : pct((double) s.no() / decided), RED, '!');
        boolean low = s.verifiability() != null && s.verifiability() < ObsintDailyReport.LOW_VERIFIABILITY;
        tile(M + 12 + 4 * (tw + 8), ty, tw, th, "VERIFICABILIDAD", s.verifiability() == null ? "—" : pct(s.verifiability()), null, low ? AMBER : MUTED, 'e');
        tile(M + 12 + 5 * (tw + 8), ty, tw, th, "TIEMPO PROMEDIO\nDE REVISIÓN", avg, "min", MUTED, 't');
        y -= boxH + 22;
    }

    private void consignas() throws IOException {
        heading("Desempeño por Consigna", null);
        String[] heads = {"#", "CÓDIGO", "CONSIGNA", "CATEGORÍA", "FRECUENCIA\nPROGRAMADA", "VECES\nEJECUTADA", "CUMPLIDAS", "INCUMPLIDAS", "INCIERTAS", "% CUMPLIMIENTO"};
        float[] cols = {18, 46, 92, 72, 54, 44, 46, 50, 46, 71};
        tableHeader(heads, cols);
        int i = 0;
        for (ObsintDailyReport.ConsignaLine l : r.consignas()) {
            List<String> title = wrap(l.title(), regular, 8.5f, cols[2] - 6), cat = wrap(l.category(), regular, 8.5f, cols[3] - 6);
            float rowH = Math.max(24, 8 + 11 * Math.max(title.size(), cat.size()));
            if (ensure(rowH)) tableHeader(heads, cols);
            float x = M, base = y - 14;
            centered(String.valueOf(++i), regular, 8.5f, x, cols[0], base, TEXT); x += cols[0];
            text(l.code(), regular, 8.5f, x + 4, base, TEXT); x += cols[1];
            for (int k = 0; k < title.size(); k++) text(title.get(k), regular, 8.5f, x + 4, base - k * 11, TEXT);
            x += cols[2];
            for (int k = 0; k < cat.size(); k++) text(cat.get(k), regular, 8.5f, x + 4, base - k * 11, TEXT);
            x += cols[3];
            text(l.frequency(), regular, 8.5f, x + 4, base, TEXT); x += cols[4];
            centered(String.valueOf(l.executed()), regular, 8.5f, x, cols[5], base, TEXT); x += cols[5];
            centered(String.valueOf(l.yes()), bold, 8.5f, x, cols[6], base, GREEN); x += cols[6];
            centered(String.valueOf(l.no()), bold, 8.5f, x, cols[7], base, l.no() > 0 ? RED : TEXT); x += cols[7];
            centered(String.valueOf(l.uncertain()), regular, 8.5f, x, cols[8], base, l.uncertain() > 0 ? AMBER : TEXT); x += cols[8];
            bar(x + 4, base - 3, cols[9] - 10, 10, l.compliance());
            y -= rowH;
            line(M, y, M + CW, y, LINE);
        }
        y -= 12;
        text("Cumplimiento = cumplidas / (cumplidas + incumplidas). Verificabilidad = (cumplidas + incumplidas) / ejecutadas.", regular, 7.5f, M, y, MUTED);
        y -= 10;
        text("Incierta: no se pudo confirmar por cámara; cuenta como ejecutada y baja la verificabilidad, no el cumplimiento.", regular, 7.5f, M, y, MUTED);
        y -= 22;
    }

    private void failures() throws IOException {
        List<ObsintDailyReport.Failure> list = r.failures();
        float cardW = (CW - 3 * 10) / 4;
        float firstRow = list.isEmpty() ? 20 : rowHeight(list.subList(0, Math.min(4, list.size())), cardW);
        ensure(60 + firstRow);
        heading("Incumplimientos (" + list.size() + ")", "Evidencia de los incumplimientos detectados durante el día.");
        if (list.isEmpty()) { text("Sin incumplimientos registrados en el día.", regular, 9, M, y - 6, MUTED); y -= 30; return; }
        for (int start = 0; start < list.size(); start += 4) {
            List<ObsintDailyReport.Failure> row = list.subList(start, Math.min(start + 4, list.size()));
            float h = rowHeight(row, cardW);
            if (start > 0 && ensure(h + 4)) heading("Incumplimientos (continuación)", null);
            for (int i = 0; i < row.size(); i++) failureCard(M + i * (cardW + 10), y, cardW, h, row.get(i));
            y -= h + 12;
        }
        y -= 10;
    }

    /** Alto de una fila de tarjetas: el de la tarjeta con más texto (observación de hasta 8 líneas). */
    private float rowHeight(List<ObsintDailyReport.Failure> row, float w) throws IOException {
        float max = 0;
        for (ObsintDailyReport.Failure f : row) {
            int title = Math.min(2, wrap(f.title(), bold, 8.5f, w - 16).size()), place = Math.min(2, wrap(f.location(), regular, 7.5f, w - 24).size());
            int obs = Math.min(8, wrap(f.observation(), regular, 7.8f, w - 16).size());
            max = Math.max(max, 52 + 78 + 14 + title * 11 + 3 + place * 10 + 5 + 11 + obs * 10 + 8);
        }
        return max;
    }

    private void failureCard(float x, float top, float w, float h, ObsintDailyReport.Failure f) throws IOException {
        roundRect(x, top - h, w, h, 6, WHITE);
        strokeRoundRect(x, top - h, w, h, 6, LINE);
        rect(x + 0.5f, top - 46, w - 1, 45.5f, PINK);
        circle(x + 12, top - 14, 4.5f, RED);
        text(f.code(), bold, 8, x + 20, top - 17, TEXT);
        text(SHORT.format(f.answeredAt()), regular, 6.8f, x + w - 6 - width(SHORT.format(f.answeredAt()), regular, 6.8f), top - 17, TEXT);
        roundRect(x + 8, top - 40, 70, 14, 7, new Color(0xFAD4D4));
        text("Incumplimiento", bold, 7, x + 14, top - 36, RED);
        float px = x + 8, pw = w - 16, ph = 78, py = top - 52 - ph;
        if (f.photo() != null) {
            try {
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, f.photo(), "evidencia");
                float scale = Math.max(pw / img.getWidth(), ph / img.getHeight());
                cs.saveGraphicsState();
                cs.addRect(px, py, pw, ph);
                cs.clip();
                float iw = img.getWidth() * scale, ih = img.getHeight() * scale;
                cs.drawImage(img, px + (pw - iw) / 2, py + (ph - ih) / 2, iw, ih);
                cs.restoreGraphicsState();
            } catch (IOException | IllegalArgumentException e) {
                placeholder(px, py, pw, ph, "Foto no disponible");
            }
        } else placeholder(px, py, pw, ph, "Sin evidencia capturada");
        float ty = py - 14;
        for (String l : first(wrap(f.title(), bold, 8.5f, w - 16), 2)) { text(l, bold, 8.5f, x + 8, ty, TEXT); ty -= 11; }
        ty -= 3;
        circle(x + 11, ty + 2.5f, 2.2f, MUTED);
        for (String l : first(wrap(f.location(), regular, 7.5f, w - 24), 2)) { text(l, regular, 7.5f, x + 17, ty, MUTED); ty -= 10; }
        ty -= 5;
        text("Observación:", bold, 8, x + 8, ty, TEXT);
        ty -= 11;
        List<String> obs = wrap(f.observation(), regular, 7.8f, w - 16);
        int max = Math.max(1, (int) ((ty - (top - h) - 8) / 10));
        List<String> shown = first(obs, max);
        if (obs.size() > max) shown.set(max - 1, ellipsis(shown.get(max - 1), regular, 7.8f, w - 16));
        for (String l : shown) { text(l, regular, 7.8f, x + 8, ty, TEXT); ty -= 10; }
    }

    private void observations() throws IOException {
        List<String> lines = wrap(r.observations(), regular, 9, CW - 70);
        float boxH = 22 + lines.size() * 12;
        ensure(boxH + 40);
        roundRect(M, y - 30, 30, 30, 15, SOFT);
        docIcon(M + 9, y - 23);
        text("Observaciones Generales", bold, 13, M + 42, y - 19, TEXT);
        y -= 40;
        roundRect(M + 42, y - boxH, CW - 42, boxH, 6, SOFT);
        strokeRoundRect(M + 42, y - boxH, CW - 42, boxH, 6, LINE);
        float ty = y - 16;
        for (String l : lines) { text(l, regular, 9, M + 54, ty, MUTED); ty -= 12; }
        y -= boxH + 16;
    }

    private void footers() throws IOException {
        for (int i = 0; i < pages.size(); i++) {
            try (PDPageContentStream f = new PDPageContentStream(doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true)) {
                cs = f;
                line(M, FOOTER - 6, W - M, FOOTER - 6, LINE);
                roundRect(M, 12, 30, 30, 4, NAVY);
                if (logo != null) cs.drawImage(logo, M + 1, 13, 28, 28);
                text("SGI COMANDO", bold, 8.5f, M + 38, 30, TEXT);
                text("OBSINT – Observation Intelligence", regular, 8, M + 38, 19, MUTED);
                String who = r.client() + " | " + r.point(), when = SHORT_DATE.format(r.date()), page = (i + 1) + " / " + pages.size();
                text(who, regular, 8, W - M - 46 - width(who, regular, 8), 30, MUTED);
                text(when, regular, 8, W - M - 46 - width(when, regular, 8), 19, MUTED);
                line(W - M - 38, 14, W - M - 38, 38, LINE);
                text(page, bold, 9.5f, W - M - width(page, bold, 9.5f), 23, TEXT);
            }
        }
    }

    // ── Piezas ───────────────────────────────────────────────────────────────────────────────────

    private void tile(float x, float yb, float w, float h, String label, String value, String sub, Color tone, char icon) throws IOException {
        roundRect(x, yb, w, h, 6, WHITE);
        float ix = x + 8, iy = yb + h - 32;
        roundRect(ix, iy, 24, 24, 5, icon == 'v' ? new Color(0xE3F5EA) : icon == '!' ? new Color(0xFDE5E5) : new Color(0xEEF1F5));
        switch (icon) {
            case 'v' -> { cs.setStrokingColor(GREEN); cs.setLineWidth(2.4f); cs.moveTo(ix + 6, iy + 12); cs.lineTo(ix + 10.5f, iy + 7.5f); cs.lineTo(ix + 18, iy + 16); cs.stroke(); }
            case '!' -> { cs.setNonStrokingColor(RED); cs.moveTo(ix + 12, iy + 19); cs.lineTo(ix + 20, iy + 5); cs.lineTo(ix + 4, iy + 5); cs.closePath(); cs.fill();
                text("!", bold, 10, ix + 10.6f, iy + 7, WHITE); }
            case 'e' -> { Color c = tone == AMBER ? AMBER : MUTED; cs.setStrokingColor(c); cs.setLineWidth(1.4f);
                cs.addRect(ix + 4, iy + 7, 12, 10); cs.stroke();
                cs.setNonStrokingColor(c); cs.moveTo(ix + 16, iy + 12); cs.lineTo(ix + 21, iy + 16); cs.lineTo(ix + 21, iy + 8); cs.closePath(); cs.fill(); }
            case 't' -> { cs.setStrokingColor(MUTED); cs.setLineWidth(1.4f); circleStroke(ix + 12, iy + 12, 7); cs.moveTo(ix + 12, iy + 12); cs.lineTo(ix + 12, iy + 16.5f);
                cs.moveTo(ix + 12, iy + 12); cs.lineTo(ix + 15, iy + 10); cs.stroke(); }
            default -> docIcon(ix + 6, iy + 4);
        }
        // Con seis tarjetas no cabe junto al ícono: la etiqueta va debajo, a todo el ancho y en hasta dos líneas.
        List<String> parts = first(wrap(label.replace('\n', ' '), regular, 6.4f, w - 14), 2);
        for (int i = 0; i < parts.size(); i++) text(parts.get(i), regular, 6.4f, x + 8, iy - 10 - i * 7.6f, MUTED);
        text(value, bold, 18, x + 8, yb + 9, icon == 'e' && tone == AMBER ? AMBER : TEXT);
        if (sub != null) text(sub, regular, 7.5f, x + 11 + width(value, bold, 18), yb + 10, tone == MUTED ? MUTED : tone);
    }

    private void docIcon(float x, float yb) throws IOException {
        cs.setStrokingColor(MUTED); cs.setLineWidth(1.2f);
        cs.addRect(x, yb, 12, 15); cs.stroke();
        for (int i = 0; i < 3; i++) { cs.moveTo(x + 3, yb + 10 - i * 3.2f); cs.lineTo(x + 9, yb + 10 - i * 3.2f); }
        cs.stroke();
    }

    private void tableHeader(String[] heads, float[] cols) throws IOException {
        rect(M, y - 28, CW, 28, SOFT);
        float x = M;
        for (int i = 0; i < heads.length; i++) {
            String[] parts = heads[i].split("\n");
            for (int k = 0; k < parts.length; k++) {
                float ty = y - (parts.length == 1 ? 17 : 12 + k * 8);
                if (NUMERIC_COLUMNS.contains(i)) centered(parts[k], bold, 6.6f, x, cols[i], ty, MUTED);
                else text(parts[k], bold, 6.6f, x + 4, ty, MUTED);
            }
            x += cols[i];
        }
        y -= 28;
    }

    /** Columnas con números (#, veces ejecutada, cumplidas, incumplidas, inciertas): centradas bajo su encabezado. */
    private static final java.util.Set<Integer> NUMERIC_COLUMNS = java.util.Set.of(0, 5, 6, 7, 8);

    private void centered(String value, PDType1Font font, float size, float colX, float colW, float yb, Color color) throws IOException {
        text(value, font, size, colX + (colW - width(value, font, size)) / 2, yb, color);
    }

    private void bar(float x, float yb, float w, float h, Double value) throws IOException {
        roundRect(x, yb, w, h, 2, new Color(0xE9EDF2));
        if (value == null) { text("—", regular, 8, x + 4, yb + 2, MUTED); return; }
        Color c = value >= 0.85 ? GREEN : value >= 0.7 ? AMBER : RED;
        roundRect(x, yb, Math.max(h, (float) (w * value)), h, 2, c);
        text(pct(value), bold, 6.8f, x + 3, yb + 2.6f, WHITE);
    }

    private void heading(String title, String subtitle) throws IOException {
        text(title, bold, 15, M, y - 14, TEXT);
        y -= 22;
        if (subtitle != null) { text(subtitle, regular, 8.5f, M, y - 6, MUTED); y -= 16; }
        y -= 4;
    }

    private void placeholder(float x, float yb, float w, float h, String label) throws IOException {
        rect(x, yb, w, h, SOFT);
        text(label, regular, 7.5f, x + (w - width(label, regular, 7.5f)) / 2, yb + h / 2 - 3, MUTED);
    }

    /** Si no entra, abre otra página; devuelve true cuando lo hizo. */
    private boolean ensure(float needed) throws IOException {
        if (y - needed >= FOOTER + 6) return false;
        cs.close();
        newPage();
        y = H - M;
        return true;
    }

    private void newPage() throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        pages.add(page);
        cs = new PDPageContentStream(doc, page);
    }

    // ── Dibujo y texto ───────────────────────────────────────────────────────────────────────────

    private void text(String value, PDType1Font font, float size, float x, float yb, Color color) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, yb);
        cs.showText(safe(value, font));
        cs.endText();
    }

    private float width(String value, PDType1Font font, float size) throws IOException { return font.getStringWidth(safe(value, font)) / 1000 * size; }

    /** Helvetica (WinAnsi) tiene tildes y ñ; lo que no tiene se escribe sin acento o como "?". */
    static String safe(String value, PDType1Font font) {
        StringBuilder out = new StringBuilder();
        for (char c : (value == null ? "" : value).replace('\n', ' ').replace('\t', ' ').toCharArray()) {
            String s = String.valueOf(c);
            if (encodable(font, s)) { out.append(c); continue; }
            String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            out.append(!plain.isEmpty() && encodable(font, plain) ? plain : "?");
        }
        return out.toString();
    }

    private static boolean encodable(PDType1Font font, String s) {
        try { font.encode(s); return true; } catch (IllegalArgumentException | IOException e) { return false; }
    }

    List<String> wrap(String value, PDType1Font font, float size, float max) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : safe(value, font).trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, font, size) <= max) { line.setLength(0); line.append(candidate); continue; }
            if (!line.isEmpty()) { out.add(line.toString()); line.setLength(0); }
            while (width(word, font, size) > max && word.length() > 1) {
                int cut = word.length();
                while (cut > 1 && width(word.substring(0, cut), font, size) > max) cut--;
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty() || out.isEmpty()) out.add(line.toString());
        return out;
    }

    private String ellipsis(String value, PDType1Font font, float size, float max) throws IOException {
        String v = value;
        while (!v.isEmpty() && width(v + "…", font, size) > max) v = v.substring(0, v.length() - 1);
        return v.stripTrailing() + "…";
    }

    private static List<String> first(List<String> list, int n) { return new ArrayList<>(list.subList(0, Math.min(n, list.size()))); }
    private static String pct(double v) { return ObsintDailyReport.percent(v) + "%"; }

    private void rect(float x, float yb, float w, float h, Color c) throws IOException { cs.setNonStrokingColor(c); cs.addRect(x, yb, w, h); cs.fill(); }
    private void line(float x1, float y1, float x2, float y2, Color c) throws IOException {
        cs.setStrokingColor(c); cs.setLineWidth(0.8f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private void roundRect(float x, float yb, float w, float h, float r, Color c) throws IOException { cs.setNonStrokingColor(c); roundPath(x, yb, w, h, r); cs.fill(); }
    private void strokeRoundRect(float x, float yb, float w, float h, float r, Color c) throws IOException {
        cs.setStrokingColor(c); cs.setLineWidth(0.8f); roundPath(x, yb, w, h, r); cs.stroke();
    }
    private void roundPath(float x, float yb, float w, float h, float r) throws IOException {
        float k = 0.5523f * r;
        cs.moveTo(x + r, yb);
        cs.lineTo(x + w - r, yb); cs.curveTo(x + w - r + k, yb, x + w, yb + r - k, x + w, yb + r);
        cs.lineTo(x + w, yb + h - r); cs.curveTo(x + w, yb + h - r + k, x + w - r + k, yb + h, x + w - r, yb + h);
        cs.lineTo(x + r, yb + h); cs.curveTo(x + r - k, yb + h, x, yb + h - r + k, x, yb + h - r);
        cs.lineTo(x, yb + r); cs.curveTo(x, yb + r - k, x + r - k, yb, x + r, yb);
        cs.closePath();
    }
    private void circle(float cx, float cy, float r, Color c) throws IOException { cs.setNonStrokingColor(c); circlePath(cx, cy, r); cs.fill(); }
    private void circleStroke(float cx, float cy, float r) throws IOException { circlePath(cx, cy, r); cs.stroke(); }
    private void circlePath(float cx, float cy, float r) throws IOException { roundPath(cx - r, cy - r, 2 * r, 2 * r, r); }
}
