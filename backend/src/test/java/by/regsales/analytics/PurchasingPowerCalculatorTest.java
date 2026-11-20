package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static by.regsales.analytics.AnalyticsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchasingPowerCalculatorTest {
    @Test void normalizesMetricsRanksRegionsAndKeepsTotalShareAtOneHundred() {
        var weights = new Weights(decimal("40"), decimal("40"), decimal("20"));
        var result = PurchasingPowerCalculator.calculate(statistics(), weights);
        var ranking = result.ranking();
        assertThat(ranking).extracting(row -> row.indicators().regionId()).containsExactly(3L, 2L, 1L);
        assertThat(ranking.get(0).index()).isEqualByComparingTo("0.9");
        assertThat(ranking.get(1).index()).isEqualByComparingTo("0.5");
        assertThat(ranking.get(2).index()).isEqualByComparingTo("0.1");
        assertThat(result.averageIndex()).isEqualByComparingTo("0.5");
        assertThat(ranking.stream().map(Score::sharePercent).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test void rejectsWeightsThatDoNotAddUpAndSupportsSingleRegion() {
        assertThatThrownBy(() -> PurchasingPowerCalculator.calculate(statistics(),
                new Weights(decimal("40"), decimal("30"), decimal("20")))).isInstanceOf(ApiException.class);
        var data = statistics();
        var one = new Statistics(data.source(), data.periodStart(), data.periodEnd(), data.refinancingRate(), List.of(data.regions().getFirst()));
        var result = PurchasingPowerCalculator.calculate(one, new Weights(decimal("33.33"), decimal("33.33"), decimal("33.34")));
        assertThat(result.ranking().getFirst().index()).isEqualByComparingTo("0.5");
        assertThat(result.ranking().getFirst().sharePercent()).isEqualByComparingTo("100");
    }

    @Test void capsPriceAdviceAndKeepsRoundedPriceWithinDiscountLimit() {
        var adjustment = PurchasingPowerCalculator.priceAdjustment(decimal("0.2"), decimal("0.7"), decimal("4.25"));
        assertThat(adjustment).isEqualByComparingTo("-4.25");
        assertThat(PurchasingPowerCalculator.suggestedPrice(decimal("4.21"), adjustment, decimal("4.25"))).isEqualByComparingTo("4.04");
        assertThat(PurchasingPowerCalculator.priceAdjustment(decimal("1"), decimal("0.1"), null)).isEqualByComparingTo("5");
    }

    private static Statistics statistics() {
        return new Statistics(Source.TRAINING, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), decimal("9.75"), List.of(
                new Indicators(1L, "BY-MA", "Могилёвская область", decimal("1000"), decimal("900"), decimal("100"), 100_000),
                new Indicators(2L, "BY-HO", "Гомельская область", decimal("1500"), decimal("1100"), decimal("500"), 100_000),
                new Indicators(3L, "BY-HM", "г. Минск", decimal("2000"), decimal("1700"), decimal("900"), 100_000)));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
