package by.regsales.catalog;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    public ProductImportResult importPrices(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                            @RequestParam(value = "file", required = false) MultipartFile file,
                                            @RequestParam(value = "replacementToken", required = false) String replacementToken,
                                            @AuthenticationPrincipal AppPrincipal actor) {
        catalog.requireManage(actor);
        var uploads = new ArrayList<>(files == null ? List.of() : files);
        if (file != null) uploads.add(file);
        return history.importPrices(workbook.read(uploads), replacementToken, actor);
    }

    @ExceptionHandler(PriceHistoryService.ReplacementRequired.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ReplacementPrompt requestReplacement(PriceHistoryService.ReplacementRequired error) {
        return new ReplacementPrompt(error.getCode(), error.getMessage(), error.getFieldErrors(), error.token);
    }

    @PostMapping("/snapshot")
    public ProductImportResult snapshot(@Valid @RequestBody QuarterRequest quarter,
                                        @AuthenticationPrincipal AppPrincipal actor) {
        return history.snapshot(quarter, actor);
    }
}
