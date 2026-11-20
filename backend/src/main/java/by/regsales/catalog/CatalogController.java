package by.regsales.catalog;

import by.regsales.common.ApiException;
import by.regsales.catalog.ProductDtos.ProductView;
import by.regsales.domain.Region;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    public List<ProductView> products() {
        return products.findAllByActiveTrueOrderByNameAsc().stream().map(ProductView::from).toList();
    }

    @GetMapping("/products/{id:[0-9]+}")
    @Operation(summary = "Карточка активного товара")
    public ProductView product(@PathVariable Long id) {
        return products.findByIdAndActiveTrue(id).map(ProductView::from).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Товар не найден"));
    }

    public record RegionResponse(Long id, String code, String name, boolean active) {
        static RegionResponse from(Region region) {
            return new RegionResponse(region.getId(), region.getCode(), region.getName(), region.isActive());
        }
    }

}
