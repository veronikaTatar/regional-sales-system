package by.regsales.analytics;

import by.regsales.common.XlsxReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static by.regsales.analytics.BelstatWebsite.normalized;

final class BelstatTurnoverWorkbook {
    private static final String TURNOVER = "розничный товарооборот";
    private static final Pattern MILLION_RUBLES = Pattern.compile("\\bмлн\\.?\\s*руб(?:лей|ля)?\\b\\.?", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PERIOD = Pattern.compile(
            "\\bянвар[ьея](?:\\s*(?:[\\p{Pd}−]|по)\\s*([а-я]+))?\\s+(20\\d{2})\\b",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final List<String> MONTH_NAMES = List.of("январ[ьея]", "феврал[ьея]", "март[ае]?", "апрел[ьея]", "ма[йея]", "июн[ьея]",
            "июл[ьея]", "август[ае]?", "сентябр[ьея]", "октябр[ьея]", "ноябр[ьея]", "декабр[ьея]");

    private BelstatTurnoverWorkbook() {}

    static Map<String, BigDecimal> read(byte[] file, YearMonth period) {
        return XlsxReader.readWorkbook(file, workbook -> {
            var format = new DataFormatter(Locale.ROOT);
            format.setUseCachedValuesForFormulaCells(true);
            for (var sheet : workbook) {
                var totals = readSheet(sheet, format, period);
                if (!totals.isEmpty()) return totals;
            }
            throw XlsxReader.invalid("BELSTAT_TURNOVER_FORMAT",
                    "В сводке за " + period + " не найден оборот с января по месяц выпуска в млн рублей по шести областям и г. Минску");
        });
    }

    private static Map<String, BigDecimal> readSheet(Sheet sheet, DataFormatter format, YearMonth period) {
        if (sheet.getLastRowNum() >= XlsxReader.MAX_ROWS) return Map.of();
        for (var row : sheet) {
            for (var cell : row) {
                if (!isTurnoverHeading(format.formatCellValue(cell))) continue;
                var regions = regionLines(sheet, format, cell);
                if (regions.size() != BelstatWorkbook.regionCodes().size()) continue;
                var totals = totals(sheet, format, period, cell, regions);
                if (!totals.isEmpty()) return totals;
            }
        }
        return Map.of();
    }

    private static boolean isTurnoverHeading(String text) {
        String label = normalized(text).replaceAll("[*¹²³⁴]+$", "").strip();
        if (!label.startsWith(TURNOVER)) return false;
        String suffix = label.substring(TURNOVER.length()).replaceAll("^[\\s,;:(–—-]+|[\\s.)]+$", "");
        return suffix.isEmpty() || MILLION_RUBLES.matcher(suffix).matches();
    }

    private static List<RegionLine> regionLines(Sheet sheet, DataFormatter format, Cell heading) {
        var lines = new ArrayList<RegionLine>();
        for (int index = heading.getRowIndex() + 1; index <= sheet.getLastRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null || rowText(row, format).isBlank()) continue;
            var region = regionLine(row, format);
            if (region != null) {
                if (lines.stream().anyMatch(line -> line.regionCode().equals(region.regionCode()))) return List.of();
                lines.add(region);
                if (lines.size() == BelstatWorkbook.regionCodes().size()) return lines;
                continue;
            }
            if (!lines.isEmpty()) break;
            var label = normalized(format.formatCellValue(row.getCell(heading.getColumnIndex())));
            if (!label.isEmpty() && !label.startsWith("республика беларусь") && !label.startsWith("области")
                    && !label.startsWith("в том числе") && !label.equals("итого")
                    && !MILLION_RUBLES.matcher(label).find() && !PERIOD.matcher(label).find()) break;
        }
        return List.of();
    }

    private static RegionLine regionLine(Row row, DataFormatter format) {
        for (var cell : row) {
            var code = BelstatWorkbook.region(format.formatCellValue(cell));
            if (code != null) return new RegionLine(code, cell.getColumnIndex(), row);
        }
        return null;
    }

    private static Map<String, BigDecimal> totals(Sheet sheet, DataFormatter format, YearMonth period, Cell heading, List<RegionLine> regions) {
        var first = regions.getFirst();
        int column = amountColumn(sheet, format, heading.getRowIndex(), first);
        if (column < 0) return Map.of();
        var header = new StringBuilder();
        var columnHeader = new StringBuilder();
        for (int index = 0; index < first.row().getRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null) continue;
            header.append(' ').append(rowText(row, format));
            columnHeader.append(' ').append(format.formatCellValue(row.getCell(column)));
        }
        String columnText = normalized(columnHeader.toString());
        String reportText = normalized(header.toString());
        var blockHeading = normalized(rowText(heading.getRow(), format));

        // The published table places the cumulative amount in the first numeric column.
        boolean correctPeriod = PERIOD.matcher(columnText).find()
                ? matchesPeriod(columnText, period) : matchesPeriod(reportText, period);
        if (!correctPeriod || !(MILLION_RUBLES.matcher(blockHeading).find() || MILLION_RUBLES.matcher(columnText).find())) {
            return Map.of();
        }

        var totals = new LinkedHashMap<String, BigDecimal>();
        for (var region : regions) {
            var value = amount(region.row(), column, format);
            if (value == null) return Map.of();
            var rubles = value.movePointRight(6).setScale(4, RoundingMode.HALF_UP);
            if (rubles.signum() <= 0 || rubles.precision() > 20) return Map.of();
            totals.put(region.regionCode(), rubles);
        }
        return totals;
    }

    private static boolean matchesPeriod(String text, YearMonth period) {
        var matches = PERIOD.matcher(text);
        while (matches.find()) {
            var lastMonth = matches.group(1);
            if (Integer.parseInt(matches.group(2)) == period.getYear()
                    && (lastMonth == null ? period.getMonthValue() == 1 : lastMonth.matches(MONTH_NAMES.get(period.getMonthValue() - 1)))) {
                return true;
            }
        }
        return false;
    }

    private static int amountColumn(Sheet sheet, DataFormatter format, int headingIndex, RegionLine first) {
        for (int index = headingIndex; index < first.row().getRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null) continue;
            for (var cell : row) {
                if (normalized(format.formatCellValue(cell)).equals("республика беларусь")) {
                    return firstNumberColumn(row, cell.getColumnIndex(), format);
                }
            }
        }
        return firstNumberColumn(first.row(), first.labelColumn(), format);
    }

    private static int firstNumberColumn(Row row, int labelColumn, DataFormatter format) {
        for (int column = labelColumn + 1; column < row.getLastCellNum(); column++) {
            if (BelstatWorkbook.number(row, column, format) != null) return column;
        }
        return -1;
    }

    private static BigDecimal amount(Row row, int column, DataFormatter format) {
        var cell = row.getCell(column);
        if (cell == null || format.formatCellValue(cell).contains("%")) return null;
        return BelstatWorkbook.number(row, column, format);
    }

    private static String rowText(Row row, DataFormatter format) {
        return StreamSupport.stream(row.spliterator(), false).map(format::formatCellValue).collect(Collectors.joining(" "));
    }

    private record RegionLine(String regionCode, int labelColumn, Row row) {}
}
