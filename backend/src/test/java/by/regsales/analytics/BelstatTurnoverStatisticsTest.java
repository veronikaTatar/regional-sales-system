package by.regsales.analytics;

import by.regsales.domain.Region;
import by.regsales.domain.RegionalStatistic;
import by.regsales.repository.RegionalStatisticRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BelstatTurnoverStatisticsTest {
    @Test void derivesMonthsOnlyFromAdjacentTotalsAndStartsOverInJanuary() {
        var repository = mock(RegionalStatisticRepository.class);
        var service = new BelstatStatistics(repository, null, null, null, null, null);
        var values = new ArrayList<RegionalStatistic>();
        values.addAll(release("2024-12", "20000000000"));
        values.addAll(release("2025-01", "1000000000"));
        values.addAll(release("2025-02", "2300000000"));
        values.addAll(release("2025-04", "4700000000"));
        values.addAll(release("2025-05", "4600000000"));
        when(repository.findByIndicatorCodeInAndPeriodEndLessThanEqualAndStatisticsImportSourceNameOrderByPeriodEndDesc(
                eq(Set.of("RETAIL_TURNOVER_YTD")), any(), eq("Белстат"))).thenReturn(values);

        var months = service.turnoverMonths();

        assertThat(months).extracting(AnalyticsDtos.TurnoverMonth::period)
                .containsExactly("2025-05", "2025-04", "2025-02", "2025-01", "2024-12");
        assertThat(months.get(0).regions()).allSatisfy(row -> assertThat(row.monthlyTurnover()).isNull());
        assertThat(months.get(1).regions()).allSatisfy(row -> assertThat(row.monthlyTurnover()).isNull());
        assertThat(months.get(2).regions()).allSatisfy(row -> assertThat(row.monthlyTurnover()).isEqualByComparingTo("1300000000"));
        assertThat(months.get(3).regions()).allSatisfy(row -> assertThat(row.monthlyTurnover()).isEqualByComparingTo(row.cumulativeTurnover()));
    }

    private static List<RegionalStatistic> release(String month, String total) {
        var period = YearMonth.parse(month);
        return BelstatWorkbook.regionCodes().stream().map(code -> {
            var region = new Region();
            region.setCode(code);
            region.setName(code);
            var row = new RegionalStatistic();
            row.setRegion(region);
            row.setPeriodStart(period.atDay(1).withDayOfYear(1));
            row.setPeriodEnd(period.atEndOfMonth());
            row.setValue(new BigDecimal(total));
            return row;
        }).toList();
    }
}
