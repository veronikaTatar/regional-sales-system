package by.regsales.planning;

import by.regsales.analytics.AnalyticsDtos.Estimate;
import by.regsales.analytics.AnalyticsDtos.PreviewInput;
import by.regsales.analytics.AnalyticsDtos.PublishInput;
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
@PreAuthorize("hasAnyRole('MANAGER', 'ANALYST', 'SALES_HEAD')")
public class PlanController {
    private final PlanService plans;

    @GetMapping
    public List<Summary> list(@RequestParam(defaultValue = "false") boolean attention,
                              @AuthenticationPrincipal AppPrincipal actor) {
        return plans.list(actor, attention);
    }

    @GetMapping("/results")
    public List<Result> results(@AuthenticationPrincipal AppPrincipal actor) {
        return plans.results(actor);
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

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SALES_HEAD')")
    public Summary cancel(@PathVariable Long id, @RequestParam Long version, @AuthenticationPrincipal AppPrincipal actor) {
        return plans.cancel(id, version, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SALES_HEAD')")
    public void delete(@PathVariable Long id, @RequestParam Long version, @AuthenticationPrincipal AppPrincipal actor) {
        plans.delete(id, version, actor);
    }

    @PostMapping("/{id}/analysis/preview")
    @PreAuthorize("hasRole('ANALYST')")
    public Estimate previewAnalysis(@PathVariable Long id, @Valid @RequestBody PreviewInput input,
                                    @AuthenticationPrincipal AppPrincipal actor) {
        return plans.previewAnalysis(id, input, actor);
    }

    @PutMapping("/{id}/analysis")
    @PreAuthorize("hasRole('ANALYST')")
    public Summary publishAnalysis(@PathVariable Long id, @Valid @RequestBody PublishInput input,
                                   @AuthenticationPrincipal AppPrincipal actor) {
        return plans.publishAnalysis(id, input, actor);
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasRole('MANAGER')")
    public Summary items(@PathVariable Long id, @Valid @RequestBody ItemsInput input,
                         @AuthenticationPrincipal AppPrincipal actor) {
        return plans.saveItems(id, input, actor);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('MANAGER')")
    public Summary withdraw(@PathVariable Long id, @RequestParam Long version, @AuthenticationPrincipal AppPrincipal actor) {
        return plans.withdraw(id, version, actor);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('SALES_HEAD')")
    public Summary review(@PathVariable Long id, @Valid @RequestBody ReviewInput input,
                          @AuthenticationPrincipal AppPrincipal actor) {
        return plans.review(id, input, actor);
    }
}
