package by.regsales.catalog;

import by.regsales.common.ApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class CatalogXlsx {
    static final int MAX_ROWS = 5_000;
    static final int MAX_ERRORS = 50;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final DateTimeFormatter RUSSIAN_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    private CatalogXlsx() {}

    interface ColumnHeader {
        String header();
        boolean required();
    }

    static <T> T readSheet(MultipartFile file, String sheetName, String missingFileMessage, Function<Sheet, T> parse) {
        if (file == null || file.isEmpty()) throw invalid("FILE_REQUIRED", missingFileMessage);
        var filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalid("XLSX_REQUIRED", "Загрузите файл в формате XLSX");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Размер файла должен быть не более 1 МБ");
        }
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw invalid("EMPTY_WORKBOOK", "В книге нет листов");
            var sheet = workbook.getSheet(sheetName);
            return parse.apply(sheet == null ? workbook.getSheetAt(0) : sheet);
        } catch (ApiException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw invalid("INVALID_WORKBOOK", "Не удалось прочитать книгу Excel. Сохраните её в формате XLSX и повторите загрузку");
        }
    }

    static <C extends Enum<C> & ColumnHeader> Map<C, Integer> readHeader(Row header, Class<C> type, DataFormatter formatter) {
        var columns = new EnumMap<C, Integer>(type);
        var expected = type.getEnumConstants();
        if (header != null) {
            for (var cell : header) {
                if (cell.getCellType() != CellType.STRING) continue;
                var name = formatter.formatCellValue(cell).replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
                var column = Arrays.stream(expected).filter(item -> item.header().equalsIgnoreCase(name)).findFirst().orElse(null);
                if (column != null && columns.putIfAbsent(column, cell.getColumnIndex()) != null) {
                    throw invalid("DUPLICATE_COLUMN", "В первой строке повторяется столбец «" + column.header() + "»");
                }
            }
        }
        var missing = Arrays.stream(expected).filter(column -> column.required() && !columns.containsKey(column))
                .map(column -> "«" + column.header() + "»").collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw invalid("MISSING_COLUMNS", "Добавьте в первую строку заголовки " + missing);
        }
        return columns;
    }

    static boolean skipRow(Row row, Map<?, Integer> columns) {
        return row.getRowNum() == 0 || columns.values().stream().allMatch(index -> blank(row.getCell(index)));
    }

    private static boolean blank(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    static BigDecimal price(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.STRING) {
            var text = cell.getStringCellValue().strip();
            if (text.length() <= 40 && text.matches("-?[0-9]+([.,][0-9]+)?")) {
                return new BigDecimal(text.replace(',', '.'));
            }
        }
        throw new IllegalArgumentException();
    }

    static LocalDate date(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell.getCellType() == CellType.STRING) {
            var text = cell.getStringCellValue().strip();
            return text.contains(".") ? LocalDate.parse(text, RUSSIAN_DATE) : LocalDate.parse(text);
        }
        throw new IllegalArgumentException();
    }

    static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    static final class RowReader<C extends ColumnHeader> {
        private final Row row;
        private final Map<C, Integer> columns;
        private final DataFormatter formatter;
        final Map<C, String> errors = new LinkedHashMap<>();

        RowReader(Row row, Map<C, Integer> columns, DataFormatter formatter) {
            this.row = row;
            this.columns = columns;
            this.formatter = formatter;
        }

        String text(C column) {
            return read(column, cell -> {
                if (cell.getCellType() != CellType.STRING && cell.getCellType() != CellType.NUMERIC) {
                    throw new IllegalArgumentException();
                }
                return formatter.formatCellValue(cell);
            }, "Введите текстовое значение");
        }

        <T> T read(C column, Function<Cell, T> convert, String invalidValue) {
            var index = columns.get(column);
            var cell = index == null ? null : row.getCell(index);
            if (blank(cell)) return null;
            if (cell.getCellType() == CellType.FORMULA) {
                errors.put(column, "Замените формулу её значением");
                return null;
            }
            try {
                return convert.apply(cell);
            } catch (IllegalArgumentException | DateTimeException error) {
                errors.put(column, invalidValue);
                return null;
            }
        }
    }
}
