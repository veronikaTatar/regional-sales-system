package by.regsales.planning;

import by.regsales.security.AppPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static by.regsales.planning.PlanDtos.*;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SALES_HEAD')")
public class PlanController {
    private final PlanService plans;

    @GetMapping
    public List<Summary> list(@RequestParam(defaultValue = "false") boolean attention,
                              @AuthenticationPrincipal AppPrincipal actor) {
        return plans.list(actor, attention);
    }

    @GetMapping("/options")
    @PreAuthorize("hasRole('SALES_HEAD')")
    public List<RegionChoice> options(@AuthenticationPrincipal AppPrincipal actor) {
        return plans.options(actor);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal actor) {
        return plans.detail(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SALES_HEAD')")
    public Summary create(@Valid @RequestBody Input input, @AuthenticationPrincipal AppPrincipal actor) {
        return plans.create(input, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SALES_HEAD')")
    public Summary update(@PathVariable Long id, @Valid @RequestBody Input input,
                          @AuthenticationPrincipal AppPrincipal actor) {
        return plans.update(id, input, actor);
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasRole('MANAGER')")
    public Summary items(@PathVariable Long id, @Valid @RequestBody ItemsInput input,
                         @AuthenticationPrincipal AppPrincipal actor) {
        return plans.saveItems(id, input, actor);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('SALES_HEAD')")
    public Summary review(@PathVariable Long id, @Valid @RequestBody ReviewInput input,
                          @AuthenticationPrincipal AppPrincipal actor) {
        return plans.review(id, input, actor);
    }
}
