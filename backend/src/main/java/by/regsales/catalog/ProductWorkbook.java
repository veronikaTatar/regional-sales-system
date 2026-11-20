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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static by.regsales.catalog.CatalogXlsx.*;
import static by.regsales.catalog.ProductDtos.*;

@Component
@RequiredArgsConstructor
public class ProductWorkbook {
    private static final String SHEET_NAME = "Товары";
    private final Validator validator;

    enum Column implements ColumnHeader {
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

        @Override public String header() { return header; }
        @Override public boolean required() { return required; }
    }

    record WorkbookRow(int number, ProductInput input, Boolean active) {}
    record Spreadsheet(Set<Column> columns, List<WorkbookRow> rows) {}

    public Spreadsheet read(MultipartFile file) {
        return readSheet(file, SHEET_NAME, "Выберите файл Excel с товарами", this::parse);
    }

    private Spreadsheet parse(Sheet sheet) {
        var formatter = new DataFormatter(Locale.ROOT);
        var columns = readHeader(sheet.getRow(0), Column.class, formatter);
        var rows = new ArrayList<WorkbookRow>();
        var errors = new LinkedHashMap<String, String>();
        var firstSkuRows = new HashMap<String, Integer>();
        int seen = 0;
        for (var row : sheet) {
            if (skipRow(row, columns)) continue;
            if (++seen > MAX_ROWS) throw invalid("TOO_MANY_ROWS", "Разделите файл на части по 5 000 товаров");
            var reader = new RowReader<>(row, columns, formatter);
            var input = new ProductInput(reader.text(Column.SKU), reader.text(Column.NAME), reader.text(Column.CATEGORY),
                    reader.text(Column.UNIT), reader.text(Column.DESCRIPTION),
                    reader.read(Column.PRICE, CatalogXlsx::price, "Введите цену числом в BYN"),
                    reader.read(Column.PRICE_DATE, CatalogXlsx::date, "Введите дату в формате ДД.ММ.ГГГГ"));
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

}
