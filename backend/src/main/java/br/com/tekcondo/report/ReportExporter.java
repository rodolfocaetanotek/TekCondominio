package br.com.tekcondo.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Exporta {@link ReportTable} para CSV (padrão brasileiro), Excel (.xlsx) e PDF. */
@Component
public class ReportExporter {

    public enum Format {
        CSV("text/csv;charset=UTF-8", "csv"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PDF("application/pdf", "pdf");

        public final String contentType;
        public final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color BRAND = new Color(0, 106, 106);

    public byte[] export(ReportTable table, Format format, String organizationName) {
        return switch (format) {
            case CSV -> csv(table);
            case XLSX -> xlsx(table);
            case PDF -> pdf(table, organizationName);
        };
    }

    // ------------------------------------------------------------------ CSV

    private byte[] csv(ReportTable t) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(csvLine(t.columns().stream().map(ReportTable.Column::label).map(Object.class::cast).toList(), null));
        for (List<Object> row : t.rows()) {
            sb.append(csvLine(row, t.columns()));
        }
        if (t.totals() != null) {
            sb.append(csvLine(t.totals(), t.columns()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvLine(List<Object> values, List<ReportTable.Column> columns) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.append(';');
            Object v = values.get(i);
            String s;
            if (v == null) {
                s = "";
            } else if (v instanceof BigDecimal d) {
                s = d.toPlainString().replace('.', ',');
            } else if (v instanceof LocalDate d) {
                s = DATE.format(d);
            } else {
                s = v.toString();
            }
            // Evita injeção de fórmulas ao abrir no Excel (=, +, -, @).
            if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof BigDecimal) && !(v instanceof Number)) {
                s = "'" + s;
            }
            if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            line.append(s);
        }
        return line.append("\r\n").toString();
    }

    // ------------------------------------------------------------------ Excel

    private byte[] xlsx(ReportTable t) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Relatório");
            CreationHelper helper = wb.getCreationHelper();
            var bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle totalMoney = wb.createCellStyle();
            totalMoney.cloneStyleFrom(money);
            totalMoney.setFont(bold);
            CellStyle totalText = wb.createCellStyle();
            totalText.setFont(bold);

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue(t.title());
            tc.setCellStyle(totalText);
            if (t.subtitle() != null) {
                sheet.createRow(r++).createCell(0).setCellValue(t.subtitle());
            }
            r++;
            Row head = sheet.createRow(r++);
            for (int i = 0; i < t.columns().size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(t.columns().get(i).label());
                c.setCellStyle(header);
            }
            for (List<Object> row : t.rows()) {
                writeRow(sheet.createRow(r++), row, t.columns(), money, date, null);
            }
            if (t.totals() != null) {
                writeRow(sheet.createRow(r++), t.totals(), t.columns(), totalMoney, date, totalText);
            }
            if (t.notes() != null) {
                r++;
                for (String note : t.notes()) {
                    sheet.createRow(r++).createCell(0).setCellValue(note);
                }
            }
            for (int i = 0; i < t.columns().size(); i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeRow(Row row, List<Object> values, List<ReportTable.Column> columns, CellStyle money, CellStyle date,
                          CellStyle text) {
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            Cell c = row.createCell(i);
            if (v instanceof BigDecimal d) {
                c.setCellValue(d.doubleValue());
                if (columns.get(i).type() == ReportTable.Type.MONEY) c.setCellStyle(money);
            } else if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else if (v instanceof LocalDate d) {
                c.setCellValue(d);
                c.setCellStyle(date);
            } else if (v != null) {
                c.setCellValue(v.toString());
                if (text != null) c.setCellStyle(text);
            }
        }
    }

    // ------------------------------------------------------------------ PDF

    private byte[] pdf(ReportTable t, String organizationName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle size = t.columns().size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(size, 28, 28, 36, 36);
        PdfWriter.getInstance(doc, out);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
        Font cell = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        HeaderFooter footer = new HeaderFooter(new Phrase("Tek Condomínio · " + (organizationName == null ? "" : organizationName)
                + " · emitido em " + STAMP.format(ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))) + " · página ", small), true);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setBorder(Rectangle.NO_BORDER);
        doc.setFooter(footer);
        doc.open();
        Paragraph title = new Paragraph(t.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND));
        doc.add(title);
        if (t.subtitle() != null) {
            doc.add(new Paragraph(t.subtitle(), FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        }
        doc.add(new Paragraph(" "));
        PdfPTable table = new PdfPTable(t.columns().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (ReportTable.Column c : t.columns()) {
            PdfPCell h = new PdfPCell(new Phrase(c.label(), headerFont));
            h.setBackgroundColor(BRAND);
            h.setPadding(4);
            h.setHorizontalAlignment(numeric(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(h);
        }
        boolean zebra = false;
        for (List<Object> row : t.rows()) {
            addPdfRow(table, row, t.columns(), cell, zebra ? new Color(242, 246, 246) : null);
            zebra = !zebra;
        }
        if (t.totals() != null) {
            addPdfRow(table, t.totals(), t.columns(), bold, new Color(224, 236, 236));
        }
        if (t.rows().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Nenhum registro no período.", cell));
            empty.setColspan(t.columns().size());
            empty.setPadding(6);
            table.addCell(empty);
        }
        doc.add(table);
        if (t.notes() != null) {
            doc.add(new Paragraph(" "));
            for (String note : t.notes()) {
                doc.add(new Paragraph(note, small));
            }
        }
        doc.close();
        return out.toByteArray();
    }

    private void addPdfRow(PdfPTable table, List<Object> row, List<ReportTable.Column> columns, Font font, Color background) {
        for (int i = 0; i < row.size(); i++) {
            PdfPCell c = new PdfPCell(new Phrase(format(row.get(i), columns.get(i)), font));
            c.setPadding(3.5f);
            c.setBorderColor(new Color(210, 220, 220));
            c.setHorizontalAlignment(numeric(columns.get(i)) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            if (background != null) c.setBackgroundColor(background);
            table.addCell(c);
        }
    }

    private static boolean numeric(ReportTable.Column c) {
        return c.type() == ReportTable.Type.MONEY || c.type() == ReportTable.Type.NUMBER || c.type() == ReportTable.Type.PERCENT;
    }

    static String format(Object v, ReportTable.Column column) {
        if (v == null) return "";
        if (v instanceof BigDecimal d) {
            if (column.type() == ReportTable.Type.MONEY) {
                return NumberFormat.getCurrencyInstance(BR).format(d);
            }
            NumberFormat nf = NumberFormat.getNumberInstance(BR);
            nf.setMaximumFractionDigits(column.type() == ReportTable.Type.PERCENT ? 2 : 6);
            return nf.format(d) + (column.type() == ReportTable.Type.PERCENT ? "%" : "");
        }
        if (v instanceof LocalDate d) return DATE.format(d);
        return v.toString();
    }
}
