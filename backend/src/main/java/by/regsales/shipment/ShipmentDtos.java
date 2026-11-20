package by.regsales.shipment;

import by.regsales.domain.Product;
import by.regsales.domain.Region;
import by.regsales.domain.Shipment;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public enum SortField {
        DATE("shipmentDate"), REFERENCE("referenceNumber"), PRODUCT("product.name"), REGION("region.name"),
        QUANTITY("quantity"), PRICE("unitPrice"), TOTAL("totalAmount");

        final String property;

        SortField(String property) {
            this.property = property;
        }
    }

    public record Filters(
            @Size(max = 120) String search,
            @Positive Long regionId,
            @Positive Long productId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Min(0) Integer page,
            @Min(1) @Max(50) Integer size,
            SortField sort,
            Sort.Direction direction) {
        public Filters {
            page = page == null ? 0 : page;
            size = size == null ? 10 : size;
            sort = sort == null ? SortField.DATE : sort;
            direction = direction == null ? Sort.Direction.DESC : direction;
        }
    }

    public record Input(
            @NotNull(message = "Выберите регион") @Positive Long regionId,
            @NotNull(message = "Выберите товар") @Positive Long productId,
            @NotNull(message = "Укажите дату отгрузки") LocalDate shipmentDate,
            @NotNull(message = "Укажите количество")
            @DecimalMin(value = "0", inclusive = false, message = "Количество должно быть больше нуля")
            @Digits(integer = 11, fraction = 3, message = "Допустимо до 11 цифр до разделителя и до 3 после") BigDecimal quantity,
            @NotNull(message = "Укажите цену")
            @DecimalMin(value = "0", message = "Цена должна быть неотрицательной")
            @Digits(integer = 12, fraction = 2, message = "Допустимо до 12 цифр до разделителя и до 2 после") BigDecimal unitPrice,
            @Size(max = 80, message = "Ключ строки должен содержать не более 80 символов") String referenceNumber) {
        public Input {
            quantity = quantity == null ? null : quantity.stripTrailingZeros();
            unitPrice = unitPrice == null ? null : unitPrice.stripTrailingZeros();
            referenceNumber = trim(referenceNumber);
        }
    }

    public record Entry(Long id, LocalDate shipmentDate, String referenceNumber, Long regionId,
                        String regionName, Long productId, String sku, String productName, String unit,
                        BigDecimal quantity, BigDecimal unitPrice,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount) {
        static Entry from(Shipment shipment) {
            var product = shipment.getProduct();
            var region = shipment.getRegion();
            return new Entry(shipment.getId(), shipment.getShipmentDate(), shipment.getReferenceNumber(), region.getId(),
                    region.getName(), product.getId(), product.getSku(), product.getName(), product.getUnit(),
                    shipment.getQuantity(), shipment.getUnitPrice(), shipment.getTotalAmount());
        }
    }

    public record ResultPage(List<Entry> shipments, long total, int page, int pageSize) {}

    public record RegionOption(Long id, String name) {
        static RegionOption from(Region region) { return new RegionOption(region.getId(), region.getName()); }
    }

    public record ProductOption(Long id, String sku, String name, String unit, boolean active) {
        static ProductOption from(Product product) {
            return new ProductOption(product.getId(), product.getSku(), product.getName(), product.getUnit(), product.isActive());
        }
    }

    public record Options(boolean canRecord, List<RegionOption> regions, List<ProductOption> products) {}
    public record ImportResult(int total, int created, int unchanged, int skipped) {}
    public record ImportRow(int number, String reference, String regionCode, String sku, LocalDate date,
                            BigDecimal quantity, BigDecimal unitPrice) {}

    static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static boolean validDate(LocalDate date) {
        return date == null || (date.getYear() >= 1000 && date.getYear() <= 9999);
    }

    static BigDecimal amount(BigDecimal quantity, BigDecimal price) {
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
