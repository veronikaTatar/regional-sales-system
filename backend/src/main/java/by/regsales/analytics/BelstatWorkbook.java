package by.regsales.analytics;

import by.regsales.common.XlsxReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

final class BelstatWorkbook {
    private static final Map<String, String> REGIONS = Map.of(
            "брестская", "BY-BR", "витебская", "BY-VI", "гомельская", "BY-HO",
            "гродненская", "BY-HR", "минская", "BY-MI", "могилевская", "BY-MA", "минск", "BY-HM");
    private static final Set<String> REGION_CODES = Set.copyOf(REGIONS.values());
    private static final Pattern YEAR = Pattern.compile("(?:на 1 января |январь[ –—-]+декабрь )?((?:19|20)\\d{2})(?:\\s*(?:г\\.?|год))?");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");

    private BelstatWorkbook() {}

    static Set<String> regionCodes() {
        return REGION_CODES;
    }

    static Map<Integer, Map<String, BigDecimal>> read(MultipartFile file, String indicator) {
        return XlsxReader.readWorkbook(file, "Выберите файл для показателя «" + indicator + "»", workbook -> {
            var result = new HashMap<Integer, Map<String, BigDecimal>>();
            var format = new DataFormatter(Locale.ROOT);
            format.setUseCachedValuesForFormulaCells(true);
            for (var sheet : workbook) readSheet(sheet, format, result, indicator);
            if (result.isEmpty()) throw XlsxReader.invalid("BELSTAT_TABLE_NOT_FOUND",
                    "В файле «" + indicator + "» не найдена таблица с годами и названиями регионов");
            return result;
        });
    }

    private static void readSheet(Sheet sheet, DataFormatter format, Map<Integer, Map<String, BigDecimal>> result, String indicator) {
        if (sheet.getLastRowNum() >= XlsxReader.MAX_ROWS) {
            throw XlsxReader.invalid("BELSTAT_TOO_MANY_ROWS", "На листе «" + sheet.getSheetName() + "» больше 5000 строк");
        }
        Map<Integer, Integer> yearColumns = Map.of();
        Map<Integer, String> regionColumns = Map.of();
        for (var row : sheet) {
            var regions = cells(row, cell -> region(format.formatCellValue(cell)));
            var years = cells(row, cell -> year(cell, format));
            if (regions.size() > 1) {
                regionColumns = regions;
                yearColumns = Map.of();
            } else if (!regionColumns.isEmpty() && !years.isEmpty() && years.keySet().iterator().next() < regionColumns.keySet().iterator().next()) {
                int year = years.values().iterator().next();
                regionColumns.forEach((column, code) -> put(result, year, code, number(row, column, format), indicator));
            } else if (!regions.isEmpty() && !yearColumns.isEmpty()
                    && regions.keySet().iterator().next() < yearColumns.keySet().iterator().next()) {
                String code = regions.values().iterator().next();
                yearColumns.forEach((column, year) -> put(result, year, code, number(row, column, format), indicator));
            } else if (regions.isEmpty() && !years.isEmpty() && years.keySet().iterator().next() > 0) {
                yearColumns = years;
                regionColumns = Map.of();
            }
        }
    }

    private static <T> Map<Integer, T> cells(Row row, Function<Cell, T> value) {
        var found = new LinkedHashMap<Integer, T>();
        for (var cell : row) {
            var parsed = value.apply(cell);
            if (parsed != null) found.put(cell.getColumnIndex(), parsed);
        }
        return found;
    }

    static String region(String text) {
        var key = text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[\\s\\p{Z}]+", " ").strip();
        if (key.matches("(г\\.? ?|город )минск")) key = "минск";
        if (key.endsWith(" область")) key = key.substring(0, key.length() - " область".length());
        String code = key.toUpperCase(Locale.ROOT);
        return REGION_CODES.contains(code) ? code : REGIONS.get(key);
    }

    static Integer year(Cell cell, DataFormatter format) {
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var date = cell.getLocalDateTimeCellValue().toLocalDate();
            return date.getDayOfYear() == 1 ? date.getYear() : null;
        }
        var label = format.formatCellValue(cell).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", " ").strip();
        var match = YEAR.matcher(label);
        return match.matches() ? Integer.parseInt(match.group(1)) : null;
    }

    static BigDecimal number(Row row, int column, DataFormatter format) {
        var cell = row.getCell(column);
        if (cell == null) return null;
        try {
            return XlsxReader.decimal(cell);
        } catch (IllegalArgumentException ignored) {
            var label = format.formatCellValue(cell).replaceAll("[\\s\\p{Z}]+", "");
            return NUMBER.matcher(label).matches() ? new BigDecimal(label.replace(',', '.')) : null;
        }
    }

    private static void put(Map<Integer, Map<String, BigDecimal>> result, int year, String region, BigDecimal value, String indicator) {
        if (value == null || value.signum() <= 0) return;
        var existing = result.computeIfAbsent(year, ignored -> new HashMap<>()).putIfAbsent(region, value);
        if (existing != null && existing.compareTo(value) != 0) {
            throw XlsxReader.invalid("BELSTAT_CONFLICT", "В файле «" + indicator + "» разные значения для региона " + region + " за " + year + " год");
        }
    }
}
