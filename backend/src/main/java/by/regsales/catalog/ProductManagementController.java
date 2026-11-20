package by.regsales.catalog;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static by.regsales.catalog.ProductDtos.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ProductManagementController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ProductCatalogService catalog;
    private final ProductWorkbook workbook;

    @GetMapping("/manage")
    public ManagedCatalog manage(@AuthenticationPrincipal AppPrincipal actor) {
        return catalog.managedCatalog(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@Valid @RequestBody ProductInput input, @AuthenticationPrincipal AppPrincipal actor) {
        return catalog.create(input, actor);
    }

    @PutMapping("/{id}")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductInput input,
                              @AuthenticationPrincipal AppPrincipal actor) {
        return catalog.update(id, input, actor);
    }

    @PatchMapping("/{id}/status")
    public ProductView status(@PathVariable Long id, @Valid @RequestBody ProductStatus status,
                              @AuthenticationPrincipal AppPrincipal actor) {
        return catalog.setActive(id, status.active(), actor);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImportResult importCatalog(@RequestParam(value = "file", required = false) MultipartFile file,
                                              @AuthenticationPrincipal AppPrincipal actor) {
        catalog.requireManage(actor);
        return catalog.importProducts(workbook.read(file), actor);
    }

    @GetMapping(value = "/export", produces = XLSX)
    public ResponseEntity<byte[]> exportCatalog(@AuthenticationPrincipal AppPrincipal actor) {
        var contents = workbook.write(catalog.exportProducts(actor));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("products.xlsx").build().toString())
                .body(contents);
    }
}
