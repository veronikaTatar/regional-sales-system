package by.regsales.catalog;

import by.regsales.common.ApiException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static by.regsales.catalog.ProductDtos.*;

@Component
@RequiredArgsConstructor
public class ProductWorkbook {
    private static final String SHEET_NAME = "Товары";
    private static final int MAX_ROWS = 5_000;
    private static final int MAX_ERRORS = 50;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final DateTimeFormatter RUSSIAN_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final Validator validator;

    enum Column {
        SKU("sku", "Артикул", true, 18),
        NAME("name", "Наименование", true, 46),
        CATEGORY("category", "Товарная группа", false, 24),
        UNIT("unit", "Ед. изм.", true, 13),
        DESCRIPTION("description", "Характеристики", false, 55),
        PRICE("currentPrice", "Отпускная цена без НДС, BYN", true, 34),
        PRICE_DATE("priceAsOf", "Цена на дату", false, 17),
        ACTIVE("active", "Активен", false, 12);

        final String field;
        final String header;
        final boolean required;
        final int width;

        Column(String field, String header, boolean required, int width) {
            this.field = field;
            this.header = header;
            this.required = required;
            this.width = width;
        }

        static Column named(String name) {
            var header = name.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
            return Arrays.stream(values()).filter(column -> column.header.equalsIgnoreCase(header)).findFirst().orElse(null);
        }
    }

    record WorkbookRow(int number, ProductInput input, Boolean active) {}
    record Spreadsheet(Set<Column> columns, List<WorkbookRow> rows) {}

    public Spreadsheet read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("FILE_REQUIRED", "Выберите файл Excel с товарами");
        }
        var filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalid("XLSX_REQUIRED", "Загрузите файл в формате XLSX");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Размер файла должен быть не более 1 МБ");
        }

        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw invalid("EMPTY_WORKBOOK", "В книге нет листов");
            var sheet = workbook.getSheet(SHEET_NAME);
            return parse(sheet == null ? workbook.getSheetAt(0) : sheet);
        } catch (ApiException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw invalid("INVALID_WORKBOOK", "Не удалось прочитать книгу Excel. Сохраните её в формате XLSX и повторите загрузку");
        }
    }

    private Spreadsheet parse(Sheet sheet) {
        var formatter = new DataFormatter(Locale.ROOT);
        var columns = readHeader(sheet.getRow(0), formatter);
        var rows = new ArrayList<WorkbookRow>();
        var errors = new LinkedHashMap<String, String>();
        var firstSkuRows = new HashMap<String, Integer>();
        int seen = 0;
        for (var row : sheet) {
            if (row.getRowNum() == 0 || columns.values().stream().allMatch(index -> blank(row.getCell(index)))) continue;
            if (++seen > MAX_ROWS) throw invalid("TOO_MANY_ROWS", "Разделите файл на части по 5 000 товаров");
            var reader = new RowReader(row, columns, formatter);
            var input = new ProductInput(reader.text(Column.SKU), reader.text(Column.NAME), reader.text(Column.CATEGORY),
                    reader.text(Column.UNIT), reader.text(Column.DESCRIPTION),
                    reader.read(Column.PRICE, ProductWorkbook::price, "Введите цену числом в BYN"),
                    reader.read(Column.PRICE_DATE, ProductWorkbook::date, "Введите дату в формате ДД.ММ.ГГГГ"));
            var active = reader.read(Column.ACTIVE, ProductWorkbook::status, "Укажите Да, Нет, 1 или 0");

            for (var violation : validator.validate(input)) {
                var field = violation.getPropertyPath().toString();
                var column = Arrays.stream(Column.values()).filter(item -> item.field.equals(field)).findFirst().orElseThrow();
                reader.errors.putIfAbsent(column, violation.getMessage());
            }
            if (!input.hasValidPriceDate()) reader.errors.put(Column.PRICE_DATE, "Укажите год от 1000 до 9999");
            int rowNumber = row.getRowNum() + 1;
            if (input.sku() != null) {
                var first = firstSkuRows.putIfAbsent(input.sku().toUpperCase(Locale.ROOT), rowNumber);
                if (first != null) reader.errors.put(Column.SKU, "Этот артикул уже указан в строке " + first);
            }
            reader.errors.forEach((column, message) -> errors.put("Строка " + rowNumber + " · " + column.header, message));
            rows.add(new WorkbookRow(rowNumber, input, active));
            if (errors.size() >= MAX_ERRORS) break;
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRODUCT_ROWS",
                    "Исправьте указанные строки и загрузите файл снова", errors);
        }
        if (rows.isEmpty()) throw invalid("EMPTY_WORKBOOK", "Добавьте товары под заголовками таблицы");
        return new Spreadsheet(Set.copyOf(columns.keySet()), List.copyOf(rows));
    }

    private Map<Column, Integer> readHeader(Row header, DataFormatter formatter) {
        var columns = new EnumMap<Column, Integer>(Column.class);
        if (header != null) {
            for (var cell : header) {
                if (cell.getCellType() != CellType.STRING) continue;
                var column = Column.named(formatter.formatCellValue(cell));
                if (column != null && columns.putIfAbsent(column, cell.getColumnIndex()) != null) {
                    throw invalid("DUPLICATE_COLUMN", "В первой строке повторяется столбец «" + column.header + "»");
                }
            }
        }
        var missing = Arrays.stream(Column.values()).filter(column -> column.required && !columns.containsKey(column))
                .map(column -> "«" + column.header + "»").collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw invalid("MISSING_COLUMNS", "Добавьте в первую строку заголовки " + missing);
        }
        return columns;
    }

    private static boolean blank(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    private static BigDecimal price(Cell cell) {
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

    private static LocalDate date(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell.getCellType() == CellType.STRING) {
            var text = cell.getStringCellValue().strip();
            return text.contains(".") ? LocalDate.parse(text, RUSSIAN_DATE) : LocalDate.parse(text);
        }
        throw new IllegalArgumentException();
    }

    private static boolean status(Cell cell) {
        if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue();
        if (cell.getCellType() == CellType.NUMERIC) {
            var value = cell.getNumericCellValue();
            if (value == 0 || value == 1) return value == 1;
        }
        if (cell.getCellType() == CellType.STRING) {
            return switch (cell.getStringCellValue().strip().toLowerCase(Locale.ROOT)) {
                case "да", "true", "истина", "1" -> true;
                case "нет", "false", "ложь", "0" -> false;
                default -> throw new IllegalArgumentException();
            };
        }
        throw new IllegalArgumentException();
    }

    public byte[] write(List<ProductView> products) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(SHEET_NAME);
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            var formats = workbook.createDataFormat();
            var priceStyle = workbook.createCellStyle();
            priceStyle.setDataFormat(formats.getFormat("0.00"));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(formats.getFormat("dd.mm.yyyy"));

            var header = sheet.createRow(0);
            for (var column : Column.values()) {
                var cell = header.createCell(column.ordinal());
                cell.setCellValue(column.header);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column.ordinal(), column.width * 256);
            }
            int index = 1;
            for (var product : products) {
                var row = sheet.createRow(index++);
                row.createCell(Column.SKU.ordinal()).setCellValue(product.sku());
                row.createCell(Column.NAME.ordinal()).setCellValue(product.name());
                row.createCell(Column.CATEGORY.ordinal()).setCellValue(product.category());
                row.createCell(Column.UNIT.ordinal()).setCellValue(product.unit());
                row.createCell(Column.DESCRIPTION.ordinal()).setCellValue(product.description());
                var price = row.createCell(Column.PRICE.ordinal());
                price.setCellValue(product.currentPrice().doubleValue());
                price.setCellStyle(priceStyle);
                if (product.priceAsOf() != null) {
                    var date = row.createCell(Column.PRICE_DATE.ordinal());
                    date.setCellValue(product.priceAsOf());
                    date.setCellStyle(dateStyle);
                }
                row.createCell(Column.ACTIVE.ordinal()).setCellValue(product.active());
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, products.size(), 0, Column.values().length - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "Не удалось подготовить файл каталога");
        }
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static final class RowReader {
        private final Row row;
        private final Map<Column, Integer> columns;
        private final DataFormatter formatter;
        private final Map<Column, String> errors = new EnumMap<>(Column.class);

        private RowReader(Row row, Map<Column, Integer> columns, DataFormatter formatter) {
            this.row = row;
            this.columns = columns;
            this.formatter = formatter;
        }

        private String text(Column column) {
            return read(column, cell -> {
                if (cell.getCellType() != CellType.STRING && cell.getCellType() != CellType.NUMERIC) {
                    throw new IllegalArgumentException();
                }
                return formatter.formatCellValue(cell);
            }, "Введите текстовое значение");
        }

        private <T> T read(Column column, Function<Cell, T> convert, String invalidValue) {
            var index = columns.get(column);
            var cell = index == null ? null : row.getCell(index);
            if (blank(cell)) return null;
            if (cell.getCellType() == CellType.FORMULA) {
                errors.put(column, "Замените формулу её значением");
                return null;
            }
            try {
                return convert.apply(cell);
            } catch (IllegalArgumentException | java.time.DateTimeException error) {
                errors.put(column, invalidValue);
                return null;
            }
        }
    }
}
