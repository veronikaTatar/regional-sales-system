package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static by.regsales.analytics.AnalyticsDtos.*;

public final class PurchasingPowerCalculator {
    public static final String METHOD_VERSION = "min-max-v1";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal NEUTRAL = new BigDecimal("0.5");
    private static final BigDecimal PRICE_LIMIT = new BigDecimal("5.00");
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("999999999999.99");

    private PurchasingPowerCalculator() {}

    public static Calculation calculate(Statistics statistics, Weights weights) {
        if (weights.salary().add(weights.retail()).add(weights.population()).compareTo(ONE_HUNDRED) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте веса показателей",
                    Map.of("weights", "Сумма весов должна быть 100 %"));
        }
        var rows = statistics.regions();
        if (rows.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_STATISTICS", "За период нет показателей");

        var salary = Range.of(rows, Indicators::averageSalary);
        var retail = Range.of(rows, Indicators::retailPerCapita);
        var population = Range.of(rows, row -> BigDecimal.valueOf(row.population()));
        var indices = rows.stream().map(row -> new Index(row, salary.normalize(row.averageSalary()).multiply(weights.salary())
                        .add(retail.normalize(row.retailPerCapita()).multiply(weights.retail()))
                        .add(population.normalize(BigDecimal.valueOf(row.population())).multiply(weights.population()))
                        .movePointLeft(2).setScale(6, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(Index::value).reversed().thenComparing(result -> result.indicators().regionCode()))
                .toList();

        var total = indices.stream().map(Index::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        var mean = total.divide(BigDecimal.valueOf(indices.size()), 6, RoundingMode.HALF_UP);
        var ranking = new ArrayList<Score>(indices.size());
        var cumulativeIndex = BigDecimal.ZERO;
        var allocatedShare = BigDecimal.ZERO;
        for (var index : indices) {
            cumulativeIndex = cumulativeIndex.add(index.value());
            // Round the cumulative shares so the published percentages add up to 100.00.
            var cumulativeShare = cumulativeIndex.movePointRight(2).divide(total, 2, RoundingMode.HALF_UP);
            ranking.add(new Score(index.indicators(), index.value(), cumulativeShare.subtract(allocatedShare)));
            allocatedShare = cumulativeShare;
        }
        return new Calculation(statistics.source(), statistics.periodStart(), statistics.periodEnd(),
                statistics.refinancingRate(), weights, mean, List.copyOf(ranking));
    }

    public static BigDecimal priceAdjustment(BigDecimal index, BigDecimal mean, BigDecimal discountLimit) {
        var reductionLimit = discountLimit == null ? PRICE_LIMIT : discountLimit.min(PRICE_LIMIT);
        return index.subtract(mean).multiply(BigDecimal.TEN)
                .min(PRICE_LIMIT).max(reductionLimit.negate()).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal suggestedPrice(BigDecimal base, BigDecimal adjustment, BigDecimal discountLimit) {
        var result = base.multiply(BigDecimal.ONE.add(adjustment.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        if (adjustment.signum() < 0 && discountLimit != null) {
            var floor = base.multiply(BigDecimal.ONE.subtract(discountLimit.movePointLeft(2))).setScale(2, RoundingMode.CEILING);
            result = result.max(floor);
        }
        return result.min(MAX_UNIT_PRICE);
    }

    private record Index(Indicators indicators, BigDecimal value) {}

    private record Range(BigDecimal min, BigDecimal max) {
        static Range of(List<Indicators> rows, Function<Indicators, BigDecimal> indicator) {
            var values = rows.stream().map(indicator).toList();
            return new Range(values.stream().min(BigDecimal::compareTo).orElseThrow(),
                    values.stream().max(BigDecimal::compareTo).orElseThrow());
        }

        BigDecimal normalize(BigDecimal value) {
            return min.compareTo(max) == 0 ? NEUTRAL : value.subtract(min).divide(max.subtract(min), 12, RoundingMode.HALF_UP);
        }
    }
}
