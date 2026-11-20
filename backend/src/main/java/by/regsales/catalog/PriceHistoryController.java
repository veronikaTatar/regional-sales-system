package by.regsales.catalog;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static by.regsales.catalog.PriceHistoryDtos.*;
import static by.regsales.catalog.ProductDtos.ProductImportResult;

@RestController
@RequestMapping("/api/products/price-history")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class PriceHistoryController {
    private final ProductCatalogService catalog;
    private final PriceHistoryService history;
    private final PriceHistoryWorkbook workbook;

    @GetMapping
    public List<PriceEntry> list(@AuthenticationPrincipal AppPrincipal actor) {
        return history.list(actor);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImportResult importPrices(@RequestParam(value = "file", required = false) MultipartFile file,
                                            @AuthenticationPrincipal AppPrincipal actor) {
        catalog.requireManage(actor);
        return history.importPrices(workbook.read(file), actor);
    }

    @PostMapping("/snapshot")
    public ProductImportResult snapshot(@Valid @RequestBody QuarterRequest quarter,
                                        @AuthenticationPrincipal AppPrincipal actor) {
        return history.snapshot(quarter, actor);
    }
}
