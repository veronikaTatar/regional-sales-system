package by.regsales.catalog;

import by.regsales.domain.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductInput(
            @NotBlank(message = "Укажите артикул")
            @Size(max = 50, message = "Артикул должен содержать не более 50 символов") String sku,
            @NotBlank(message = "Укажите наименование")
            @Size(max = 200, message = "Наименование должно содержать не более 200 символов") String name,
            @Size(max = 100, message = "Группа должна содержать не более 100 символов") String category,
            @NotBlank(message = "Укажите единицу измерения")
            @Size(max = 20, message = "Единица измерения должна содержать не более 20 символов") String unit,
            @Size(max = 500, message = "Характеристики должны содержать не более 500 символов") String description,
            @NotNull(message = "Укажите цену")
            @DecimalMin(value = "0", message = "Цена должна быть неотрицательной")
            @Digits(integer = 12, fraction = 2, message = "Допустимо до 12 цифр до разделителя и до 2 после") BigDecimal currentPrice,
            LocalDate priceAsOf) {

        public ProductInput {
            sku = normalize(sku);
            name = normalize(name);
            category = normalize(category);
            unit = normalize(unit);
            description = normalize(description);
            if (currentPrice != null) currentPrice = currentPrice.stripTrailingZeros();
        }

        boolean hasValidPriceDate() {
            return priceAsOf == null || (priceAsOf.getYear() >= 1000 && priceAsOf.getYear() <= 9999);
        }

        static ProductInput from(Product product) {
            return new ProductInput(product.getSku(), product.getName(), product.getCategory(), product.getUnit(),
                    product.getDescription(), product.getCurrentPrice(), product.getPriceAsOf());
        }
    }

    public record ProductView(Long id, String sku, String name, String category, String unit,
                              String description, BigDecimal currentPrice, LocalDate priceAsOf, boolean active) {
        static ProductView from(Product product) {
            return new ProductView(product.getId(), product.getSku(), product.getName(), product.getCategory(),
                    product.getUnit(), product.getDescription(), product.getCurrentPrice(), product.getPriceAsOf(),
                    product.isActive());
        }
    }

    public record ProductStatus(@NotNull(message = "Укажите статус товара") Boolean active) {}
    public record ManagedCatalog(boolean canManage, List<ProductView> products) {}
    public record ProductImportResult(int total, int created, int updated, int unchanged) {}

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
