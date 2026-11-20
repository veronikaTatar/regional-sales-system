package by.regsales.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public enum Source { TRAINING }

    public record Weights(
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal salary,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal retail,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal population) {}

    public record PreviewInput(@NotNull @PositiveOrZero Long version, @NotNull @Valid Weights weights) {}

    public record PublishInput(@NotNull @PositiveOrZero Long version, @NotNull @Valid Weights weights,
                               @NotBlank(message = "Добавьте вывод для менеджера")
                               @Size(max = 2000, message = "Комментарий должен содержать до 2000 символов")
                               String recommendationText) {
        public PublishInput {
            if (recommendationText != null) recommendationText = recommendationText.strip();
        }
    }

    public record Indicators(Long regionId, String regionCode, String regionName, BigDecimal averageSalary,
                             BigDecimal incomePerCapita, BigDecimal retailPerCapita, long population) {}

    public record Statistics(Source source, LocalDate periodStart, LocalDate periodEnd,
                             BigDecimal refinancingRate, List<Indicators> regions) {}

    public record Score(Indicators indicators, BigDecimal index, BigDecimal sharePercent) {}

    public record Calculation(Source source, LocalDate periodStart, LocalDate periodEnd, BigDecimal refinancingRate,
                              Weights weights, BigDecimal averageIndex, List<Score> ranking) {}

    public record Estimate(Calculation calculation, Score selected, BigDecimal priceAdjustmentPercent) {}

    public record Publication(Long planId, String regionName, LocalDate periodStart, LocalDate periodEnd,
                              Instant publishedAt, String author, Source source, BigDecimal index, String text) {}

    public record Recommendation(Publication publication, BigDecimal sharePercent, BigDecimal priceAdjustmentPercent) {}

    public record PublishedCalculation(Recommendation recommendation, Calculation calculation, String methodVersion) {}
}
