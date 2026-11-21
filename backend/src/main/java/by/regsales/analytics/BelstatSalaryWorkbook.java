package by.regsales.analytics;

import by.regsales.common.XlsxReader;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BelstatSalaryWorkbook {
    private static final List<String> MONTHS = List.of("январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь");

    private BelstatSalaryWorkbook() {}

    static Map<String, BigDecimal> read(byte[] file, YearMonth period) {
        return XlsxReader.readWorkbook(file, workbook -> {
            var format = new DataFormatter(Locale.ROOT);
            format.setUseCachedValuesForFormulaCells(true);
            for (var sheet : workbook) {
                var values = regionalValues(sheet, format, period);
                if (values.keySet().equals(BelstatWorkbook.regionCodes())) return values;
            }
            throw XlsxReader.invalid("BELSTAT_SALARY_FORMAT",
                    "В книге не найдены начисленные зарплаты в рублях по шести областям и г. Минску за " + period);
        });
    }

    private static Map<String, BigDecimal> regionalValues(Sheet sheet, DataFormatter format, YearMonth period) {
        if (sheet.getLastRowNum() >= XlsxReader.MAX_ROWS) return Map.of();
        var rows = new HashMap<String, Integer>();
        for (var row : sheet) {
            for (var cell : row) {
                var region = BelstatWorkbook.region(format.formatCellValue(cell));
                if (region == null) continue;
                if (rows.putIfAbsent(region, row.getRowNum()) != null) return Map.of();
                break;
            }
        }
        if (!rows.keySet().equals(BelstatWorkbook.regionCodes())) return Map.of();

        int headerEnd = rows.values().stream().min(Integer::compareTo).orElseThrow();
        int column = nominalColumn(sheet, format, period, headerEnd);
        if (column == -1) return Map.of();

        var salaries = new HashMap<String, BigDecimal>();
        for (var entry : rows.entrySet()) {
            var salary = BelstatWorkbook.number(sheet.getRow(entry.getValue()), column, format);
            if (salary == null || salary.signum() <= 0) return Map.of();
            salaries.put(entry.getKey(), salary);
        }
        return Map.copyOf(salaries);
    }

    private static int nominalColumn(Sheet sheet, DataFormatter format, YearMonth period, int headerEnd) {
        String month = MONTHS.get(period.getMonthValue() - 1);
        for (int index = 0; index < headerEnd; index++) {
            var header = sheet.getRow(index);
            if (header == null) continue;
            for (var cell : header) {
                var label = BelstatSalaryClient.normalized(format.formatCellValue(cell));
                if (!label.contains("номинальная начисленная") || !label.contains("заработная плата")
                        || (!label.contains("рублей") && !label.contains("byn"))) continue;
                int first = cell.getColumnIndex();
                int last = sheet.getMergedRegions().stream()
                        .filter(range -> range.isInRange(header.getRowNum(), first))
                        .mapToInt(range -> range.getLastColumn()).findFirst().orElse(first);
                for (int column = first; column <= last; column++) {
                    if (matchesPeriod(sheet, format, period, month, index + 1, headerEnd, column)) return column;
                }
            }
        }
        return -1;
    }

    private static boolean matchesPeriod(Sheet sheet, DataFormatter format, YearMonth period, String month,
                                         int first, int end, int column) {
        boolean monthFound = false;
        boolean yearFound = false;
        for (int index = first; index < end; index++) {
            var row = sheet.getRow(index);
            var cell = row == null ? null : row.getCell(column);
            if (cell == null) {
                int rowIndex = index;
                cell = sheet.getMergedRegions().stream().filter(range -> range.isInRange(rowIndex, column))
                        .map(range -> sheet.getRow(range.getFirstRow()).getCell(range.getFirstColumn()))
                        .findFirst().orElse(null);
            }
            if (cell == null) continue;
            var label = BelstatSalaryClient.normalized(format.formatCellValue(cell)).split("/", 2)[0].strip();
            monthFound |= label.equals(month) || label.matches(month + " " + period.getYear() + "( г\\.?)?");
            yearFound |= Integer.valueOf(period.getYear()).equals(BelstatWorkbook.year(cell, format))
                    || label.matches(month + " " + period.getYear() + "( г\\.?)?");
        }
        return monthFound && yearFound;
    }
}
