package by.regsales.shipment;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static by.regsales.shipment.ShipmentDtos.*;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ANALYST', 'SALES_HEAD')")
public class ShipmentController {
    private final ShipmentService shipments;
    private final ShipmentWorkbook workbook;

    @GetMapping
    public ResultPage list(@Valid @ModelAttribute Filters filters, @AuthenticationPrincipal AppPrincipal actor) {
        return shipments.list(filters, actor);
    }

    @GetMapping("/options")
    public Options options(@AuthenticationPrincipal AppPrincipal actor) {
        return shipments.options(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public Entry create(@Valid @RequestBody Input input, @AuthenticationPrincipal AppPrincipal actor) {
        return shipments.create(input, actor);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ImportResult importShipments(@RequestParam(value = "file", required = false) MultipartFile file,
                                        @AuthenticationPrincipal AppPrincipal actor) {
        return shipments.importRows(workbook.read(file), actor);
    }
}
