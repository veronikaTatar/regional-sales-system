package by.regsales.analytics;

import by.regsales.common.CalendarPeriod;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static by.regsales.analytics.AnalyticsDtos.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST', 'SALES_HEAD')")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final BelstatStatistics belstat;
    private final BelstatSalaryClient salaryClient;
    private final BelstatTurnoverClient turnoverClient;
    private final BelstatCpiClient cpiClient;
    private final NbrbExchangeRates exchangeRates;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ANALYST')")
    public Statistics statistics(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return analytics.statistics(new CalendarPeriod(periodStart, periodEnd));
    }

    @GetMapping("/statistics/latest")
    @PreAuthorize("hasRole('ANALYST')")
    public ResponseEntity<Statistics> latestStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        var statistics = through == null ? belstat.latest() : belstat.latestThrough(through);
        return statistics.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/statistics/salaries")
    @PreAuthorize("hasRole('ANALYST')")
    public List<SalaryMonth> salaries() {
        return belstat.monthlySalaries();
    }

    @PostMapping("/statistics/salaries/sync")
    @PreAuthorize("hasRole('ANALYST')")
    public MonthlyDownloadResult syncSalaries(@RequestParam(required = false) Integer year, @AuthenticationPrincipal AppPrincipal actor) {
        return belstat.saveMonthlySalaries(salaryClient.downloadYear(year), actor);
    }

    @GetMapping("/statistics/turnover")
    @PreAuthorize("hasRole('ANALYST')")
    public List<TurnoverMonth> turnover() {
        return belstat.turnoverMonths();
    }

    @PostMapping("/statistics/turnover/sync")
    @PreAuthorize("hasRole('ANALYST')")
    public MonthlyDownloadResult syncTurnover(@RequestParam(required = false) Integer year, @AuthenticationPrincipal AppPrincipal actor) {
        return belstat.saveTurnover(turnoverClient.downloadYear(year), actor);
    }

    @GetMapping("/statistics/non-food-cpi")
    @PreAuthorize("hasRole('ANALYST')")
    public List<CpiMonth> nonFoodCpi() {
        return belstat.nonFoodCpiMonths();
    }

    @GetMapping("/statistics/exchange-rates")
    @PreAuthorize("hasRole('ANALYST')")
    public NbrbExchangeRates.DailyRates exchangeRates(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return exchangeRates.onDate(date);
    }

    @PostMapping("/statistics/non-food-cpi/sync")
    @PreAuthorize("hasRole('ANALYST')")
    public MonthlyDownloadResult syncNonFoodCpi(@RequestParam(required = false) Integer year, @AuthenticationPrincipal AppPrincipal actor) {
        return belstat.saveNonFoodCpi(cpiClient.downloadYear(year), actor);
    }

    @PostMapping(value = "/statistics/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ANALYST')")
    public StatisticsImportResult importStatistics(@RequestParam MultipartFile salary,
                                                   @RequestParam MultipartFile turnover,
                                                   @RequestParam MultipartFile population,
                                                   @RequestParam TurnoverUnit turnoverUnit,
                                                   @RequestParam PopulationUnit populationUnit,
                                                   @AuthenticationPrincipal AppPrincipal actor) {
        return belstat.importLatest(salary, turnover, population, turnoverUnit, populationUnit, actor);
    }

    @GetMapping("/recommendations")
    public List<Publication> history() {
        return analytics.history();
    }

    @GetMapping("/recommendations/{planId}")
    public PublishedCalculation published(@PathVariable Long planId) {
        return analytics.published(planId);
    }
}
