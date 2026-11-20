package by.regsales.analytics;

import by.regsales.domain.Region;
import by.regsales.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static by.regsales.analytics.AnalyticsDtos.*;

@Component
@RequiredArgsConstructor
public class TrainingStatistics {
    private static final Map<String, MonthlyInput> BASE = Map.of(
            "BY-BR", new MonthlyInput(2040, 1460, 725, 1_310_000),
            "BY-VI", new MonthlyInput(1980, 1420, 685, 1_070_000),
            "BY-HO", new MonthlyInput(2115, 1510, 720, 1_340_000),
            "BY-HR", new MonthlyInput(2080, 1490, 745, 970_000),
            "BY-MI", new MonthlyInput(2395, 1730, 835, 1_450_000),
            "BY-MA", new MonthlyInput(1950, 1385, 660, 985_000),
            "BY-HM", new MonthlyInput(3025, 2230, 1150, 2_000_000));
    private static final BigDecimal REFINANCING_RATE = new BigDecimal("9.75");
    private final RegionRepository regions;

    public Statistics read(LocalDate start, LocalDate end) {
        var months = (int) ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1;
        var month = (start.getMonthValue() + end.getMonthValue()) / 2;
        var rows = regions.findAllByActiveTrueOrderByNameAsc().stream()
                .filter(region -> BASE.containsKey(region.getCode()))
                .map(region -> BASE.get(region.getCode()).forPeriod(region, month, months))
                .toList();
        return new Statistics(Source.TRAINING, start, end, REFINANCING_RATE, rows);
    }

    private record MonthlyInput(int salary, int income, int retailPerCapita, long population) {
        Indicators forPeriod(Region region, int month, int months) {
            return new Indicators(region.getId(), region.getCode(), region.getName(),
                    BigDecimal.valueOf(salary + month * 12L), BigDecimal.valueOf(income + month * 8L),
                    BigDecimal.valueOf((retailPerCapita + month * 5L) * months), population);
        }
    }
}
