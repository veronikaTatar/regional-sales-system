package by.regsales.catalog;

import by.regsales.common.ApiException;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static by.regsales.catalog.PriceHistoryDtos.*;
import static by.regsales.catalog.ProductDtos.ProductImportResult;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceHistoryService {
    private final ProductCatalogService catalog;
    private final ProductRepository products;
    private final ProductPriceRepository history;

    public List<PriceEntry> list(AppPrincipal actor) {
        catalog.requireManage(actor);
        return history.history().stream().map(PriceEntry::from).toList();
    }

    @Transactional
    public ProductImportResult importPrices(List<PriceRow> rows, AppPrincipal actor) {
        catalog.lockWrites(actor);
        var candidates = new ArrayList<Candidate>();
        var errors = new LinkedHashMap<String, String>();
        var knownProducts = new HashMap<String, Optional<Product>>();
        for (var row : rows) {
            var product = knownProducts.computeIfAbsent(row.sku().toUpperCase(Locale.ROOT),
                    key -> products.findBySkuIgnoreCase(row.sku()));
            if (product.isPresent()) {
                candidates.add(new Candidate(product.get(), row.validFrom(), row.validTo(), row.values(), row.number()));
            } else {
                errors.put("Строка " + row.number(), "Артикул «" + row.sku() + "» отсутствует в каталоге");
                if (errors.size() >= CatalogXlsx.MAX_ERRORS) break;
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_PRODUCT_NOT_FOUND",
                    "Загрузите номенклатуру с указанными артикулами", errors);
        }
        return save(candidates, true);
    }

    @Transactional
    public ProductImportResult snapshot(QuarterRequest quarter, AppPrincipal actor) {
        catalog.lockWrites(actor);
        var from = LocalDate.of(quarter.year(), (quarter.quarter() - 1) * 3 + 1, 1);
        var to = from.plusMonths(3).minusDays(1);
        var candidates = products.findAllByActiveTrueOrderByNameAsc().stream()
                .map(product -> new Candidate(product, from, to,
                        new PriceValues(product.getCurrentPrice(), null, null, null, null), 0))
                .toList();
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ACTIVE_PRODUCTS",
                    "Добавьте товары в каталог или верните их в продажу");
        }
        return save(candidates, false);
    }

    private ProductImportResult save(List<Candidate> candidates, boolean replaceExisting) {
        var start = candidates.stream().map(Candidate::from).min(Comparator.naturalOrder()).orElseThrow();
        var end = candidates.stream().map(Candidate::to).max(Comparator.naturalOrder()).orElseThrow();
        var productIds = candidates.stream().map(candidate -> candidate.product().getId()).collect(Collectors.toSet());
        var savedPeriods = history.intersecting(productIds, start, end).stream()
                .collect(Collectors.groupingBy(price -> price.getProduct().getId()));
        var checked = new HashMap<Long, List<Candidate>>();
        var errors = new LinkedHashMap<String, String>();
        var changes = new ArrayList<ProductPrice>();
        int created = 0;
        int updated = 0;
        for (var candidate : candidates) {
            var productId = candidate.product().getId();
            var previous = checked.computeIfAbsent(productId, id -> new ArrayList<>());
            var overlap = previous.stream().filter(row -> candidate.intersects(row.from(), row.to())).findFirst();
            if (overlap.isPresent()) {
                errors.put(candidate.label(), "Период пересекается со строкой " + overlap.get().rowNumber());
                if (errors.size() >= CatalogXlsx.MAX_ERRORS) break;
                continue;
            }
            previous.add(candidate);
            var price = savedPeriods.getOrDefault(productId, List.of()).stream()
                    .filter(row -> candidate.intersects(row.getValidFrom(), row.getValidTo())).findFirst().orElse(null);
            if (price != null && (!candidate.from().equals(price.getValidFrom()) || !candidate.to().equals(price.getValidTo()))) {
                errors.put(candidate.label(), "Для артикула «" + candidate.product().getSku()
                        + "» уже сохранён пересекающийся период " + price.getValidFrom() + " — " + price.getValidTo());
                if (errors.size() >= CatalogXlsx.MAX_ERRORS) break;
                continue;
            }
            if (price == null) {
                price = new ProductPrice();
                price.setProduct(candidate.product());
                price.setValidFrom(candidate.from());
                price.setValidTo(candidate.to());
                created++;
            } else {
                if (!replaceExisting || candidate.values().equals(PriceValues.from(price))) continue;
                updated++;
            }
            apply(price, candidate.values());
            changes.add(price);
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_PERIOD_OVERLAP",
                    "Проверьте периоды действия цен", errors);
        }
        history.saveAllAndFlush(changes);
        return new ProductImportResult(candidates.size(), created, updated, candidates.size() - created - updated);
    }

    private static void apply(ProductPrice price, PriceValues values) {
        price.setUnitPrice(values.unitPrice());
        price.setVatRate(values.vatRate());
        price.setPriceWithVat(values.priceWithVat());
        price.setRetailPrice(values.retailPrice());
        price.setPriceListCode(values.priceListCode());
    }

    private record Candidate(Product product, LocalDate from, LocalDate to, PriceValues values, int rowNumber) {
        boolean intersects(LocalDate otherStart, LocalDate otherEnd) {
            return !from.isAfter(otherEnd) && !to.isBefore(otherStart);
        }

        String label() {
            return rowNumber == 0 ? product.getSku() : "Строка " + rowNumber;
        }
    }
}
