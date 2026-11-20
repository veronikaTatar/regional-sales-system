package by.regsales.shipment;

import by.regsales.common.ApiException;
import by.regsales.common.XlsxReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static by.regsales.common.XlsxReader.*;
import static by.regsales.shipment.ShipmentDtos.*;

@Component
public class ShipmentWorkbook {
    private enum Column implements ColumnHeader {
        REFERENCE("Ключ строки", true),
        DATE("Дата отгрузки", true),
        REGION("Код региона ISO", true),
        SKU("Артикул", true),
        QUANTITY("Количество", true),
        PRICE("Цена ед. без НДС, BYN", true),
        AMOUNT("Сумма без НДС, BYN", false),
        CURRENCY("Валюта", false);

        private final String title;
        private final boolean required;

        Column(String title, boolean required) {
            this.title = title;
            this.required = required;
        }

        @Override public String header() { return title; }
        @Override public boolean required() { return required; }
    }

    List<ImportRow> read(MultipartFile file) {
        return readSheet(file, "Отгрузки", "Выберите файл Excel с отгрузками", this::parse);
    }

    private List<ImportRow> parse(Sheet sheet) {
        var formatter = new DataFormatter(Locale.ROOT);
        var columns = readHeader(sheet.getRow(0), Column.class, formatter);
        var rows = new ArrayList<ImportRow>();
        var errors = new LinkedHashMap<String, String>();
        for (var row : sheet) {
            if (skipRow(row, columns)) continue;
            if (rows.size() >= MAX_ROWS) throw invalid("TOO_MANY_ROWS", "Разделите файл на части по 5 000 строк");
            var reader = new RowReader<>(row, columns, formatter);
            var reference = requiredText(reader, Column.REFERENCE, 80);
            var region = requiredText(reader, Column.REGION, 16);
            var sku = requiredText(reader, Column.SKU, 50);
            var date = reader.read(Column.DATE, XlsxReader::date, "Введите дату в формате ДД.ММ.ГГГГ");
            var quantity = reader.read(Column.QUANTITY, cell -> number(cell, 11, 3, true),
                    "Введите количество больше нуля с точностью до трёх знаков");
            var price = reader.read(Column.PRICE, cell -> number(cell, 12, 2, false),
                    "Введите неотрицательную цену в BYN с точностью до копейки");
            var specifiedAmount = reader.read(Column.AMOUNT, cell -> number(cell, 16, 2, false),
                    "Введите сумму в BYN с точностью до копейки");
            var currency = trim(reader.text(Column.CURRENCY));
            if (currency != null && !currency.equalsIgnoreCase("BYN")) {
                reader.errors().put(Column.CURRENCY, "Укажите валюту BYN");
            }
            if (date == null || !validDate(date)) reader.errors().putIfAbsent(Column.DATE, "Укажите дату с годом от 1000 до 9999");
            if (quantity == null) reader.errors().putIfAbsent(Column.QUANTITY, "Укажите количество");
            if (price == null) reader.errors().putIfAbsent(Column.PRICE, "Укажите фактическую цену за единицу");
            if (quantity != null && price != null) {
                var calculated = amount(quantity, price);
                if (calculated.precision() > 18) {
                    reader.errors().put(Column.AMOUNT, "Сумма отгрузки превышает допустимое значение");
                } else if (specifiedAmount != null && specifiedAmount.compareTo(calculated) != 0) {
                    reader.errors().put(Column.AMOUNT, "Количество × цена даёт " + calculated.toPlainString() + " BYN");
                }
            }
            int number = row.getRowNum() + 1;
            reader.errors().forEach((column, message) -> errors.put("Строка " + number + " · " + column.header(), message));
            rows.add(new ImportRow(number, reference, region, sku, date, quantity, price));
            if (errors.size() >= MAX_ERRORS) break;
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SHIPMENT_ROWS",
                "Проверьте отмеченные строки отгрузок", errors);
        if (rows.isEmpty()) throw invalid("EMPTY_WORKBOOK", "Добавьте отгрузки под заголовками таблицы");
        return rows;
    }

    private static String requiredText(RowReader<Column> reader, Column column, int length) {
        var value = trim(reader.text(column));
        if (value == null || value.length() > length) {
            reader.errors().putIfAbsent(column, "Введите значение длиной до " + length + " символов");
        }
        return value;
    }

    private static BigDecimal number(Cell cell, int integer, int fraction, boolean positive) {
        var value = decimal(cell).stripTrailingZeros();
        if (value.signum() < (positive ? 1 : 0) || value.scale() > fraction
                || value.precision() - value.scale() > integer) throw new IllegalArgumentException();
        return value;
    }
}
