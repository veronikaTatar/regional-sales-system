package by.regsales.catalog;

import by.regsales.domain.Product;
import by.regsales.domain.Region;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Справочники", description = "Чтение справочников; регионы ограничены действующими назначениями менеджера")
@Transactional(readOnly = true)
public class CatalogController {
    private final AppUserRepository users;
    private final ProductRepository products;
    private final RegionScopeService regionScope;

    @GetMapping("/regions")
    @Operation(summary = "Доступные активные регионы")
    public List<RegionResponse> regions(@AuthenticationPrincipal AppPrincipal principal) {
        var user = users.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("Учётная запись недоступна"));
        return regionScope.accessibleRegions(user).stream().map(RegionResponse::from).toList();
    }

    @GetMapping("/products")
    @Operation(summary = "Каталог активной продукции")
    public List<ProductResponse> products() {
        return products.findAllByActiveTrueOrderByNameAsc().stream().map(ProductResponse::from).toList();
    }

    public record RegionResponse(Long id, String code, String name, boolean active) {
        static RegionResponse from(Region region) {
            return new RegionResponse(region.getId(), region.getCode(), region.getName(), region.isActive());
        }
    }

    public record ProductResponse(Long id, String sku, String name, String category, String unit,
                                  BigDecimal currentPrice, boolean active) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                    product.getCategory(), product.getUnit(), product.getCurrentPrice(), product.isActive());
        }
    }
}
