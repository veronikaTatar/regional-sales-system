package by.regsales.analytics;

import by.regsales.common.ApiException;
import by.regsales.common.XlsxReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static by.regsales.analytics.BelstatWebsite.normalized;

final class BelstatCpiWorkbook {
    private static final Pattern NON_FOOD = Pattern.compile("(?:ипц на )?непродовольственные(?: товары)?(?:,? всего)?");
    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*%");
    private static final Pattern FOOTNOTE = Pattern.compile("\\s*(?:[*¹²³⁴⁵]+|\\(?[1-9]\\))$");
    private static final Pattern PERIOD = Pattern.compile("\\bза ([а-я]+) (20\\d{2})\\b", Pattern.UNICODE_CHARACTER_CLASS);

    private BelstatCpiWorkbook() {}

    static Map<String, BigDecimal> read(byte[] file, YearMonth period) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            var format = new DataFormatter(Locale.ROOT);
            format.setUseCachedValuesForFormulaCells(true);
            for (var sheet : workbook) {
                if (sheet.getLastRowNum() >= XlsxReader.MAX_ROWS) continue;
                var values = readSheet(sheet, format);
                if (!values.isEmpty()) {
                    verifyHeading(sheet, format, period);
                    return values;
                }
            }
        } catch (ApiException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw XlsxReader.invalid("BELSTAT_CPI_WORKBOOK", "Не удалось прочитать выпуск ИПЦ за " + period + " в формате XLS или XLSX");
        }
        throw XlsxReader.invalid("BELSTAT_CPI_NON_FOOD", "В выпуске за " + period
                + " не найдены индексы категории «Непродовольственные товары» по всем шести областям и г. Минску");
    }

    private static void verifyHeading(Sheet sheet, DataFormatter format, YearMonth period) {
        for (var row : sheet) {
            if (row.getRowNum() > 5) break;
            for (var cell : row) {
                String text = normalized(format.formatCellValue(cell));
                if (text.contains("к декабрю предыдущего года") || text.contains("декабрь предыдущего года = 100")) {
                    throw XlsxReader.invalid("BELSTAT_CPI_BASE", "В выпуске за " + period + " указана база сравнения с декабрём предыдущего года");
                }
                if (!text.startsWith("индексы потребительских цен")) continue;
                var match = PERIOD.matcher(text);
                if (!match.find()) continue;
                int month = BelstatCpiClient.monthNumber(match.group(1));
                if (month == 0) continue;
                var stated = YearMonth.of(Integer.parseInt(match.group(2)), month);
                if (!period.equals(stated)) {
                    throw XlsxReader.invalid("BELSTAT_CPI_PERIOD", "В заголовке выпуска указан период " + stated + " вместо " + period);
                }
            }
        }
    }

    private static Map<String, BigDecimal> readSheet(Sheet sheet, DataFormatter format) {
        Map<Integer, String> regionColumns = Map.of();
        for (var row : sheet) {
            var headings = regions(row, format);
            if (headings.size() > 1) regionColumns = headings;
            for (var cell : row) {
                if (!NON_FOOD.matcher(label(cell, format)).matches()) continue;
                var across = readAcross(row, cell.getColumnIndex(), regionColumns, format);
                if (!across.isEmpty()) return across;
                if (cell.getColumnIndex() == 0) continue;
                var down = readDown(sheet, cell, format);
                if (!down.isEmpty()) return down;
            }
        }
        return Map.of();
    }

    private static Map<String, BigDecimal> readAcross(Row row, int labelColumn, Map<Integer, String> regions, DataFormatter format) {
        if (regions.size() != BelstatWorkbook.regionCodes().size()) return Map.of();
        var values = new LinkedHashMap<String, BigDecimal>();
        for (var entry : regions.entrySet()) {
            int column = entry.getKey();
            if (column <= labelColumn) return Map.of();
            var index = index(row, column, format);
            if (index == null || values.putIfAbsent(entry.getValue(), index) != null) return Map.of();
        }
        return Map.copyOf(values);
    }

    private static Map<String, BigDecimal> readDown(Sheet sheet, Cell heading, DataFormatter format) {
        int column = heading.getColumnIndex();
        var values = new LinkedHashMap<String, BigDecimal>();
        for (int i = heading.getRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
            var row = sheet.getRow(i);
            if (row == null) continue;
            var regions = regions(row, format);
            if (regions.isEmpty() && values.isEmpty()) continue;
            if (regions.size() != 1) break;
            var region = regions.entrySet().iterator().next();
            if (region.getKey() >= column) break;
            var index = index(row, column, format);
            if (index == null || values.putIfAbsent(region.getValue(), index) != null) break;
            if (values.keySet().equals(BelstatWorkbook.regionCodes())) return Map.copyOf(values);
        }
        return Map.of();
    }

    private static Map<Integer, String> regions(Row row, DataFormatter format) {
        var regions = new LinkedHashMap<Integer, String>();
        for (var cell : row) {
            var code = BelstatWorkbook.region(label(cell, format).replaceFirst(" обл\\.?$", " область"));
            if (code != null) regions.put(cell.getColumnIndex(), code);
        }
        return regions;
    }

    private static BigDecimal index(Row row, int column, DataFormatter format) {
        var display = normalized(format.formatCellValue(row.getCell(column)));
        var percent = PERCENT.matcher(display);
        var value = percent.matches() ? new BigDecimal(percent.group(1).replace(',', '.')) : BelstatWorkbook.number(row, column, format);
        if (value == null) return null;
        var rounded = value.setScale(4, RoundingMode.HALF_UP);
        return rounded.signum() > 0 && rounded.precision() <= 20 ? rounded : null;
    }

    private static String label(Cell cell, DataFormatter format) {
        return FOOTNOTE.matcher(normalized(format.formatCellValue(cell))).replaceFirst("").strip();
    }
}
