package by.regsales.catalog;

import by.regsales.common.ApiException;
import by.regsales.common.XlsxReader;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.security.AppPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
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
    private final ObjectMapper json;

    public List<PriceEntry> list(AppPrincipal actor) {
        catalog.requireManage(actor);
        return history.history().stream().map(PriceEntry::from).toList();
    }

    @Transactional
    public ProductImportResult importPrices(List<PriceRow> rows, String replacementToken, AppPrincipal actor) {
        catalog.lockWrites(actor);
        var candidates = new ArrayList<Candidate>();
        var errors = new LinkedHashMap<String, String>();
        var knownProducts = new HashMap<String, Optional<Product>>();
        for (var row : rows) {
            var product = knownProducts.computeIfAbsent(row.sku().toUpperCase(Locale.ROOT),
                    key -> products.findBySkuIgnoreCase(row.sku()));
            if (product.isPresent()) {
                candidates.add(new Candidate(product.get(), row.validFrom(), row.validTo(), row.values(), row.source()));
            } else {
                errors.put(row.source(), "Артикул «" + row.sku() + "» отсутствует в каталоге");
                if (errors.size() >= XlsxReader.MAX_ERRORS) break;
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_PRODUCT_NOT_FOUND",
                    "Загрузите номенклатуру с указанными артикулами", errors);
        }
        return save(candidates, true, replacementToken);
    }

    @Transactional
    public ProductImportResult snapshot(QuarterRequest quarter, AppPrincipal actor) {
        catalog.lockWrites(actor);
        var from = LocalDate.of(quarter.year(), (quarter.quarter() - 1) * 3 + 1, 1);
        var to = from.plusMonths(3).minusDays(1);
        var candidates = products.findAllByActiveTrueOrderByNameAsc().stream()
                .map(product -> new Candidate(product, from, to,
                        new PriceValues(product.getCurrentPrice(), null, null, null, null), product.getSku()))
                .toList();
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ACTIVE_PRODUCTS",
                    "Добавьте товары в каталог или верните их в продажу");
        }
        return save(candidates, false, null);
    }

    private ProductImportResult save(List<Candidate> candidates, boolean importFile, String replacementToken) {
        var start = candidates.stream().map(Candidate::from).min(Comparator.naturalOrder()).orElseThrow();
        var end = candidates.stream().map(Candidate::to).max(Comparator.naturalOrder()).orElseThrow();
        var productIds = candidates.stream().map(candidate -> candidate.product().getId()).collect(Collectors.toSet());
        var savedPeriods = history.intersecting(productIds, start, end).stream()
                .collect(Collectors.groupingBy(price -> price.getProduct().getId()));
        var checked = new HashMap<Long, List<Candidate>>();
        var errors = new LinkedHashMap<String, String>();
        var matches = new ArrayList<Match>();
        for (var candidate : candidates) {
            var productId = candidate.product().getId();
            var previous = checked.computeIfAbsent(productId, id -> new ArrayList<>());
            var overlap = previous.stream().filter(row -> candidate.intersects(row.from(), row.to())).findFirst();
            if (overlap.isPresent()) {
                var earlier = overlap.get();
                var problem = candidate.from().equals(earlier.from()) && candidate.to().equals(earlier.to())
                        ? "Повтор цены за тот же период. Оставьте одну строку из двух"
                        : "Пересекающиеся периоды для одного товара";
                errors.put(candidate.source(), problem + " («" + earlier.source() + "»)");
                if (errors.size() >= XlsxReader.MAX_ERRORS) break;
                continue;
            }
            previous.add(candidate);
            var price = savedPeriods.getOrDefault(productId, List.of()).stream()
                    .filter(row -> candidate.intersects(row.getValidFrom(), row.getValidTo())).findFirst().orElse(null);
            if (price != null && (!candidate.from().equals(price.getValidFrom()) || !candidate.to().equals(price.getValidTo()))) {
                errors.put(candidate.source(), "Для артикула «" + candidate.product().getSku()
                        + "» уже сохранён пересекающийся период " + price.getValidFrom() + " — " + price.getValidTo());
                if (errors.size() >= XlsxReader.MAX_ERRORS) break;
                continue;
            }
            matches.add(new Match(candidate, price));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_PERIOD_OVERLAP",
                    "Проверьте периоды действия цен", errors);
        }
        if (importFile) requireReplacementConfirmation(matches, replacementToken);

        var changes = new ArrayList<ProductPrice>();
        int created = 0;
        int updated = 0;
        for (var match : matches) {
            var candidate = match.candidate();
            var price = match.existing();
            if (price == null) {
                price = new ProductPrice();
                price.setProduct(candidate.product());
                price.setValidFrom(candidate.from());
                price.setValidTo(candidate.to());
                created++;
            } else {
                if (!importFile || candidate.values().equals(PriceValues.from(price))) continue;
                updated++;
            }
            apply(price, candidate.values());
            changes.add(price);
        }
        history.saveAllAndFlush(changes);
        return new ProductImportResult(candidates.size(), created, updated, candidates.size() - created - updated);
    }

    private void requireReplacementConfirmation(List<Match> matches, String suppliedToken) {
        var existing = matches.stream().filter(match -> match.existing() != null).toList();
        if (existing.isEmpty()) return;
        // Confirm the exact incoming batch and the saved values it would replace.
        var token = replacementToken(matches);
        if (token.equals(suppliedToken)) return;
        var details = new LinkedHashMap<String, String>();
        existing.stream().limit(XlsxReader.MAX_ERRORS).forEach(match -> {
            var row = match.candidate();
            details.put(row.source(), row.product().getSku() + " · " + row.from() + " — " + row.to()
                    + " · " + match.existing().getUnitPrice().toPlainString() + " → "
                    + row.values().unitPrice().toPlainString() + " BYN без НДС");
        });
        var message = "В истории уже есть записи с такими артикулами и периодами (" + existing.size()
                + "). Заменить их данными из выбранных файлов?";
        throw new ReplacementRequired(message, details, token);
    }

    private String replacementToken(List<Match> matches) {
        var state = matches.stream().map(match -> {
            var row = match.candidate();
            var existing = match.existing();
            return new ReplacementState(row.product().getId(), row.from(), row.to(), row.values(),
                    existing == null ? null : existing.getId(), existing == null ? null : PriceValues.from(existing));
        }).toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(state)));
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Could not prepare price replacement confirmation", error);
        }
    }

    private static void apply(ProductPrice price, PriceValues values) {
        price.setUnitPrice(values.unitPrice());
        price.setVatRate(values.vatRate());
        price.setPriceWithVat(values.priceWithVat());
        price.setRetailPrice(values.retailPrice());
        price.setPriceListCode(values.priceListCode());
    }

    private record Candidate(Product product, LocalDate from, LocalDate to, PriceValues values, String source) {
        boolean intersects(LocalDate otherStart, LocalDate otherEnd) {
            return !from.isAfter(otherEnd) && !to.isBefore(otherStart);
        }

    }

    private record Match(Candidate candidate, ProductPrice existing) {}

    private record ReplacementState(Long productId, LocalDate from, LocalDate to, PriceValues incoming,
                                    Long existingId, PriceValues existing) {}

    static final class ReplacementRequired extends ApiException {
        final String token;

        ReplacementRequired(String message, Map<String, String> details, String token) {
            super(HttpStatus.CONFLICT, "PRICE_PERIOD_EXISTS", message, details);
            this.token = token;
        }
    }
}
