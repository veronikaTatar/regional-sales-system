package by.regsales.catalog;

import by.regsales.domain.ProductPrice;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class PriceHistoryDtos {
    private PriceHistoryDtos() {}

    public record QuarterRequest(
            @NotNull(message = "Укажите год")
            @Min(value = 1000, message = "Укажите год от 1000 до 9999")
            @Max(value = 9999, message = "Укажите год от 1000 до 9999") Integer year,
            @NotNull(message = "Выберите квартал")
            @Min(value = 1, message = "Выберите квартал с I по IV")
            @Max(value = 4, message = "Выберите квартал с I по IV") Integer quarter) {}

    record PriceValues(BigDecimal unitPrice, BigDecimal vatRate, BigDecimal priceWithVat,
                       BigDecimal retailPrice, String priceListCode) {
        static PriceValues from(ProductPrice price) {
            return new PriceValues(price.getUnitPrice(), price.getVatRate(), price.getPriceWithVat(),
                    price.getRetailPrice(), price.getPriceListCode());
        }
    }

    record PriceRow(String source, String sku, LocalDate validFrom, LocalDate validTo, PriceValues values) {}

    public record ReplacementPrompt(String code, String message, Map<String, String> fieldErrors, String replacementToken) {}

    public record PriceEntry(Long id, String sku, String name, LocalDate validFrom, LocalDate validTo,
                             BigDecimal unitPrice, BigDecimal vatRate, BigDecimal priceWithVat,
                             BigDecimal retailPrice, String priceListCode) {
        static PriceEntry from(ProductPrice price) {
            return new PriceEntry(price.getId(), price.getProduct().getSku(), price.getProduct().getName(),
                    price.getValidFrom(), price.getValidTo(), price.getUnitPrice(), price.getVatRate(),
                    price.getPriceWithVat(), price.getRetailPrice(), price.getPriceListCode());
        }
    }
}
