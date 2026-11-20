package by.regsales.catalog;

import by.regsales.common.ApiException;
import by.regsales.domain.Product;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static by.regsales.catalog.ProductDtos.*;
import static by.regsales.catalog.ProductWorkbook.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService {
    private final ProductRepository products;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RegionScopeService regionScope;

    public ManagedCatalog managedCatalog(AppPrincipal actor) {
        boolean permitted = hasAssignment(actor);
        var entries = permitted ? products.findAllByOrderByNameAscIdAsc() : products.findAllByActiveTrueOrderByNameAsc();
        return new ManagedCatalog(permitted, entries.stream().map(ProductView::from).toList());
    }

    public List<ProductView> exportProducts(AppPrincipal actor) {
        requireManage(actor);
        return products.findAllByOrderByNameAscIdAsc().stream().map(ProductView::from).toList();
    }

    public void requireManage(AppPrincipal actor) {
        if (!hasAssignment(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGION_ASSIGNMENT_REQUIRED",
                    "Для ведения каталога нужно действующее назначение региона");
        }
    }

    @Transactional
    public ProductView create(ProductInput input, AppPrincipal actor) {
        lockWrites(actor);
        checkUniqueSku(input.sku(), null);
        var product = new Product();
        apply(product, input);
        return ProductView.from(products.saveAndFlush(product));
    }

    @Transactional
    public ProductView update(Long id, ProductInput input, AppPrincipal actor) {
        lockWrites(actor);
        var product = findProduct(id);
        checkUniqueSku(input.sku(), id);
        apply(product, input);
        return ProductView.from(products.saveAndFlush(product));
    }

    @Transactional
    public ProductView setActive(Long id, boolean active, AppPrincipal actor) {
        lockWrites(actor);
        var product = findProduct(id);
        product.setActive(active);
        return ProductView.from(products.saveAndFlush(product));
    }

    @Transactional
    public ProductImportResult importProducts(Spreadsheet spreadsheet, AppPrincipal actor) {
        lockWrites(actor);

        // Resolve existing SKUs before modifying any product. MySQL may match multiple spellings to the same SKU.
        var resolved = new ArrayList<ResolvedRow>();
        var matchedIds = new HashMap<Long, Integer>();
        for (var row : spreadsheet.rows()) {
            var existing = products.findBySkuIgnoreCase(row.input().sku()).orElse(null);
            if (existing != null) {
                var first = matchedIds.putIfAbsent(existing.getId(), row.number());
                if (first != null) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_SKU",
                            "Файл содержит повторяющийся артикул",
                            Map.of("Строка " + row.number(), "Этот товар уже указан в строке " + first));
                }
            }
            resolved.add(new ResolvedRow(row, existing));
        }

        var changes = new ArrayList<Product>();
        int created = 0;
        int updated = 0;
        for (var entry : resolved) {
            var row = entry.row();
            var product = entry.existing();
            if (product == null) {
                product = new Product();
                apply(product, row.input());
                product.setActive(!Boolean.FALSE.equals(row.active()));
                changes.add(product);
                created++;
                continue;
            }

            var input = merge(product, row.input(), spreadsheet.columns());
            boolean active = row.active() == null ? product.isActive() : row.active();
            if (!input.equals(ProductInput.from(product)) || product.isActive() != active) {
                apply(product, input);
                product.setActive(active);
                changes.add(product);
                updated++;
            }
        }

        products.saveAllAndFlush(changes);
        return new ProductImportResult(resolved.size(), created, updated, resolved.size() - created - updated);
    }

    void lockWrites(AppPrincipal actor) {
        // Keep price snapshots and imports consistent with catalog edits and status changes.
        roles.lockByCode(RoleCode.MANAGER).orElseThrow();
        requireManage(actor);
    }

    private boolean hasAssignment(AppPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Требуется учётная запись менеджера");
        var account = users.findById(actor.getId()).orElse(null);
        if (account == null || !account.isActive() || account.getRole().getCode() != RoleCode.MANAGER
                || account.getSessionVersion() != actor.getSessionVersion()) {
            throw new AccessDeniedException("Требуется действующая учётная запись менеджера");
        }
        return !regionScope.accessibleRegions(account).isEmpty();
    }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Товар не найден"));
    }

    private void checkUniqueSku(String sku, Long existingId) {
        if (products.findBySkuIgnoreCase(sku).filter(product -> !product.getId().equals(existingId)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU_EXISTS", "Артикул уже используется",
                    Map.of("sku", "Укажите уникальный артикул"));
        }
    }

    private static ProductInput merge(Product product, ProductInput incoming, Set<Column> columns) {
        boolean priceChanged = incoming.currentPrice().compareTo(product.getCurrentPrice()) != 0;
        return new ProductInput(product.getSku(), incoming.name(),
                columns.contains(Column.CATEGORY) ? incoming.category() : product.getCategory(),
                incoming.unit(),
                columns.contains(Column.DESCRIPTION) ? incoming.description() : product.getDescription(),
                incoming.currentPrice(),
                columns.contains(Column.PRICE_DATE) || priceChanged ? incoming.priceAsOf() : product.getPriceAsOf());
    }

    private static void apply(Product product, ProductInput input) {
        if (!input.hasValidPriceDate()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте дату цены",
                    Map.of("priceAsOf", "Укажите год от 1000 до 9999"));
        }
        product.setSku(input.sku());
        product.setName(input.name());
        product.setCategory(input.category());
        product.setUnit(input.unit());
        product.setDescription(input.description());
        product.setCurrentPrice(input.currentPrice());
        product.setPriceAsOf(input.priceAsOf());
    }

    private record ResolvedRow(WorkbookRow row, Product existing) {}
}
