package by.regsales.catalog;

import by.regsales.common.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static by.regsales.common.XlsxReader.*;
import static by.regsales.catalog.PriceHistoryDtos.*;

@Component
public class PriceHistoryWorkbook {
    private static final int MAX_FILES = 24;

    private enum Column implements ColumnHeader {
        SKU("Артикул", true),
        FROM("Действует с", true),
        TO("Действует по", true),
        PRICE("Отпускная без НДС, BYN", true),
        CURRENCY("Валюта", false),
        VAT("Ставка НДС, %", false),
        GROSS("Отпускная с НДС, BYN", false),
        RETAIL("Розничный ориентир с НДС, BYN", false),
        LIST_CODE("Код прайс-листа", false);

        private final String header;
        private final boolean required;

        Column(String header, boolean required) {
            this.header = header;
            this.required = required;
        }

        @Override public String header() { return header; }
        @Override public boolean required() { return required; }
    }

    List<PriceRow> read(List<MultipartFile> files) {
        if (files.isEmpty()) throw invalid("FILE_REQUIRED", "Выберите один или несколько прайсов Excel");
        if (files.size() > MAX_FILES) throw invalid("TOO_MANY_FILES", "Выберите не более 24 прайсов за одну загрузку");
        var rows = new ArrayList<PriceRow>();
        for (int index = 0; index < files.size(); index++) {
            var file = files.get(index);
            var name = file.getOriginalFilename();
            var source = name == null || name.isBlank() ? "Файл " + (index + 1) : name;
            try {
                rows.addAll(readSheet(file, "Прайс", "Выберите файл Excel с прайс-листом", sheet -> parse(sheet, source)));
            } catch (ApiException error) {
                if (!error.getFieldErrors().isEmpty()) throw error;
                throw new ApiException(error.getStatus(), error.getCode(), "Проверьте файл «" + source + "». " + error.getMessage());
            }
            if (rows.size() > MAX_ROWS) throw invalid("TOO_MANY_ROWS", "За одну загрузку поддерживается до 5 000 строк");
        }
        return rows;
    }

    private List<PriceRow> parse(Sheet sheet, String file) {
        var formatter = new DataFormatter(Locale.ROOT);
        var columns = readHeader(sheet.getRow(0), Column.class, formatter);
        var rows = new ArrayList<PriceRow>();
        var errors = new LinkedHashMap<String, String>();
        for (var row : sheet) {
            if (skipRow(row, columns)) continue;
            if (rows.size() >= MAX_ROWS) throw invalid("TOO_MANY_ROWS", "Разделите прайс на части по 5 000 строк");
            var reader = new RowReader<>(row, columns, formatter);
            var sku = trimmed(reader.text(Column.SKU));
            var start = reader.read(Column.FROM, PriceHistoryWorkbook::validDate, "Введите дату с годом от 1000 до 9999");
            var end = reader.read(Column.TO, PriceHistoryWorkbook::validDate, "Введите дату с годом от 1000 до 9999");
            var unitPrice = amount(reader, Column.PRICE);
            var vat = reader.read(Column.VAT, PriceHistoryWorkbook::vatRate, "Введите ставку от 0 до 100 %");
            var gross = amount(reader, Column.GROSS);
            var retail = amount(reader, Column.RETAIL);
            var currency = trimmed(reader.text(Column.CURRENCY));
            var code = trimmed(reader.text(Column.LIST_CODE));

            if (sku == null || sku.length() > 50) {
                reader.errors().putIfAbsent(Column.SKU, "Укажите артикул длиной до 50 символов");
            }
            if (start == null) reader.errors().putIfAbsent(Column.FROM, "Укажите начало действия цены");
            if (end == null) reader.errors().putIfAbsent(Column.TO, "Укажите окончание действия цены");
            if (start != null && end != null && end.isBefore(start)) {
                reader.errors().put(Column.TO, "Дата окончания должна быть не раньше даты начала");
            }
            if (unitPrice == null) reader.errors().putIfAbsent(Column.PRICE, "Укажите отпускную цену");
            if (currency != null && !currency.equalsIgnoreCase("BYN")) {
                reader.errors().put(Column.CURRENCY, "Укажите валюту BYN");
            }
            if (code != null && code.length() > 50) {
                reader.errors().put(Column.LIST_CODE, "Код должен содержать не более 50 символов");
            }

            var source = file + " · строка " + (row.getRowNum() + 1);
            reader.errors().forEach((column, message) -> errors.put(source + " · " + column.header(), message));
            rows.add(new PriceRow(source, sku, start, end, new PriceValues(unitPrice, vat, gross, retail, code)));
            if (errors.size() >= MAX_ERRORS) break;
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRICE_ROWS",
                    "Исправьте указанные строки прайса и загрузите файл снова", errors);
        }
        if (rows.isEmpty()) throw invalid("EMPTY_WORKBOOK", "Добавьте цены под заголовками таблицы");
        return rows;
    }

    private static BigDecimal amount(RowReader<Column> reader, Column column) {
        return reader.read(column, PriceHistoryWorkbook::money, "Введите неотрицательную цену в BYN с точностью до копейки");
    }

    private static BigDecimal money(Cell cell) {
        var value = decimal(cell).stripTrailingZeros();
        if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 12) {
            throw new IllegalArgumentException();
        }
        return value.setScale(2);
    }

    private static BigDecimal vatRate(Cell cell) {
        var value = money(cell);
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException();
        return value;
    }

    private static LocalDate validDate(Cell cell) {
        var value = date(cell);
        if (value.getYear() < 1000 || value.getYear() > 9999) throw new IllegalArgumentException();
        return value;
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
