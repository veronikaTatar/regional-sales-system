package by.regsales.analytics;

import by.regsales.common.CalendarPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static by.regsales.analytics.AnalyticsDtos.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST', 'SALES_HEAD')")
public class AnalyticsController {
    private final AnalyticsService analytics;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ANALYST')")
    public Statistics statistics(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return analytics.statistics(new CalendarPeriod(periodStart, periodEnd));
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
