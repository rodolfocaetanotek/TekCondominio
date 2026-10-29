package br.com.tekcondo.relatorio;

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

/** Exporta {@link RelatorioTabela} para CSV (padrão brasileiro), Excel (.xlsx) e PDF. */
@Component
public class RelatorioExporter {

    public enum Formato {
        CSV("text/csv;charset=UTF-8", "csv"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PDF("application/pdf", "pdf");

        public final String tipoConteudo;
        public final String extensao;

        Formato(String tipoConteudo, String extensao) {
            this.tipoConteudo = tipoConteudo;
            this.extensao = extensao;
        }
    }

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color COR_MARCA = new Color(0, 106, 106);

    public byte[] exportar(RelatorioTabela tabela, Formato formato, String nomeAdministradora) {
        return switch (formato) {
            case CSV -> csv(tabela);
            case XLSX -> xlsx(tabela);
            case PDF -> pdf(tabela, nomeAdministradora);
        };
    }

    // ------------------------------------------------------------------ CSV

    private byte[] csv(RelatorioTabela t) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(linhaCsv(t.colunas().stream().map(RelatorioTabela.Coluna::rotulo).map(Object.class::cast).toList(), null));
        for (List<Object> row : t.linhas()) {
            sb.append(linhaCsv(row, t.colunas()));
        }
        if (t.totais() != null) {
            sb.append(linhaCsv(t.totais(), t.colunas()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String linhaCsv(List<Object> valores, List<RelatorioTabela.Coluna> colunas) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) line.append(';');
            Object v = valores.get(i);
            String s;
            if (v == null) {
                s = "";
            } else if (v instanceof BigDecimal d) {
                s = d.toPlainString().replace('.', ',');
            } else if (v instanceof LocalDate d) {
                s = DATA.format(d);
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

    private byte[] xlsx(RelatorioTabela t) {
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
            Row titulo = sheet.createRow(r++);
            Cell tc = titulo.createCell(0);
            tc.setCellValue(t.titulo());
            tc.setCellStyle(totalText);
            if (t.subtitulo() != null) {
                sheet.createRow(r++).createCell(0).setCellValue(t.subtitulo());
            }
            r++;
            Row head = sheet.createRow(r++);
            for (int i = 0; i < t.colunas().size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(t.colunas().get(i).rotulo());
                c.setCellStyle(header);
            }
            for (List<Object> row : t.linhas()) {
                escreverLinha(sheet.createRow(r++), row, t.colunas(), money, date, null);
            }
            if (t.totais() != null) {
                escreverLinha(sheet.createRow(r++), t.totais(), t.colunas(), totalMoney, date, totalText);
            }
            if (t.observacoes() != null) {
                r++;
                for (String note : t.observacoes()) {
                    sheet.createRow(r++).createCell(0).setCellValue(note);
                }
            }
            for (int i = 0; i < t.colunas().size(); i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void escreverLinha(Row row, List<Object> valores, List<RelatorioTabela.Coluna> colunas, CellStyle money, CellStyle date,
                          CellStyle text) {
        for (int i = 0; i < valores.size(); i++) {
            Object v = valores.get(i);
            Cell c = row.createCell(i);
            if (v instanceof BigDecimal d) {
                c.setCellValue(d.doubleValue());
                if (colunas.get(i).tipo() == RelatorioTabela.Tipo.MONEY) c.setCellStyle(money);
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

    private byte[] pdf(RelatorioTabela t, String nomeAdministradora) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle size = t.colunas().size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(size, 28, 28, 36, 36);
        PdfWriter.getInstance(doc, out);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
        Font cell = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        HeaderFooter footer = new HeaderFooter(new Phrase("Tek Condomínio · " + (nomeAdministradora == null ? "" : nomeAdministradora)
                + " · emitido em " + DATA_HORA.format(ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))) + " · página ", small), true);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setBorder(Rectangle.NO_BORDER);
        doc.setFooter(footer);
        doc.open();
        Paragraph titulo = new Paragraph(t.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COR_MARCA));
        doc.add(titulo);
        if (t.subtitulo() != null) {
            doc.add(new Paragraph(t.subtitulo(), FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        }
        doc.add(new Paragraph(" "));
        PdfPTable tabela = new PdfPTable(t.colunas().size());
        tabela.setWidthPercentage(100);
        tabela.setHeaderRows(1);
        for (RelatorioTabela.Coluna c : t.colunas()) {
            PdfPCell h = new PdfPCell(new Phrase(c.rotulo(), headerFont));
            h.setBackgroundColor(COR_MARCA);
            h.setPadding(4);
            h.setHorizontalAlignment(numerica(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabela.addCell(h);
        }
        boolean zebra = false;
        for (List<Object> row : t.linhas()) {
            adicionarLinhaPdf(tabela, row, t.colunas(), cell, zebra ? new Color(242, 246, 246) : null);
            zebra = !zebra;
        }
        if (t.totais() != null) {
            adicionarLinhaPdf(tabela, t.totais(), t.colunas(), bold, new Color(224, 236, 236));
        }
        if (t.linhas().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Nenhum registro no período.", cell));
            empty.setColspan(t.colunas().size());
            empty.setPadding(6);
            tabela.addCell(empty);
        }
        doc.add(tabela);
        if (t.observacoes() != null) {
            doc.add(new Paragraph(" "));
            for (String note : t.observacoes()) {
                doc.add(new Paragraph(note, small));
            }
        }
        doc.close();
        return out.toByteArray();
    }

    private void adicionarLinhaPdf(PdfPTable tabela, List<Object> row, List<RelatorioTabela.Coluna> colunas, Font font, Color background) {
        for (int i = 0; i < row.size(); i++) {
            PdfPCell c = new PdfPCell(new Phrase(formatar(row.get(i), colunas.get(i)), font));
            c.setPadding(3.5f);
            c.setBorderColor(new Color(210, 220, 220));
            c.setHorizontalAlignment(numerica(colunas.get(i)) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            if (background != null) c.setBackgroundColor(background);
            tabela.addCell(c);
        }
    }

    private static boolean numerica(RelatorioTabela.Coluna c) {
        return c.tipo() == RelatorioTabela.Tipo.MONEY || c.tipo() == RelatorioTabela.Tipo.NUMBER || c.tipo() == RelatorioTabela.Tipo.PERCENT;
    }

    static String formatar(Object v, RelatorioTabela.Coluna coluna) {
        if (v == null) return "";
        if (v instanceof BigDecimal d) {
            if (coluna.tipo() == RelatorioTabela.Tipo.MONEY) {
                return NumberFormat.getCurrencyInstance(BR).format(d);
            }
            NumberFormat nf = NumberFormat.getNumberInstance(BR);
            nf.setMaximumFractionDigits(coluna.tipo() == RelatorioTabela.Tipo.PERCENT ? 2 : 6);
            return nf.format(d) + (coluna.tipo() == RelatorioTabela.Tipo.PERCENT ? "%" : "");
        }
        if (v instanceof LocalDate d) return DATA.format(d);
        return v.toString();
    }
}
