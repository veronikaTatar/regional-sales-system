package by.regsales.analytics;

import by.regsales.common.ApiException;
import by.regsales.common.CalendarPeriod;
import by.regsales.domain.AnalyticalRecommendation;
import by.regsales.domain.AppUser;
import by.regsales.domain.PurchasingPowerCalculation;
import by.regsales.domain.RegionalPlan;
import by.regsales.repository.AnalyticalRecommendationRepository;
import by.regsales.repository.PurchasingPowerCalculationRepository;
import by.regsales.repository.RegionalPlanRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static by.regsales.analytics.AnalyticsDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {
    private final TrainingStatistics source;
    private final PurchasingPowerCalculationRepository calculations;
    private final AnalyticalRecommendationRepository recommendations;
    private final RegionalPlanRepository plans;
    private final ObjectMapper json;

    public Statistics statistics(CalendarPeriod period) {
        return source.read(period.start(), period.end());
    }

    public Statistics requireStatisticsFor(RegionalPlan plan) {
        var statistics = source.read(plan.getPeriodStart(), plan.getPeriodEnd());
        var regionId = plan.getRegion().getId();
        if (statistics.regions().stream().noneMatch(row -> row.regionId().equals(regionId))) {
            throw noStatistics();
        }
        return statistics;
    }

    public Estimate estimate(RegionalPlan plan, Weights weights) {
        return estimate(plan, PurchasingPowerCalculator.calculate(requireStatisticsFor(plan), weights));
    }

    @Transactional
    public AnalyticalRecommendation publish(RegionalPlan plan, PublishInput input, AppUser analyst) {
        var estimate = estimate(plan, input.weights());
        var calculation = new PurchasingPowerCalculation();
        calculation.setRegion(plan.getRegion());
        calculation.setPeriodStart(plan.getPeriodStart());
        calculation.setPeriodEnd(plan.getPeriodEnd());
        calculation.setMethodVersion(PurchasingPowerCalculator.METHOD_VERSION);
        calculation.setIndexValue(estimate.selected().index());
        calculation.setCalculatedByUser(analyst);
        calculation.setInputSnapshot(writeSnapshot(estimate.calculation()));

        var recommendation = new AnalyticalRecommendation();
        recommendation.setRegion(plan.getRegion());
        recommendation.setPurchasingPowerCalculation(calculations.save(calculation));
        recommendation.setAuthorUser(analyst);
        recommendation.setRecommendationText(input.recommendationText());
        return recommendations.save(recommendation);
    }

    public Recommendation recommendation(RegionalPlan plan) {
        return plan.getAnalyticalRecommendation() == null ? null : published(plan).recommendation();
    }

    public List<Publication> history() {
        return plans.findByAnalyticalRecommendationIsNotNullOrderByPeriodStartDescIdDesc().stream()
                .map(plan -> publication(plan, Source.TRAINING)).toList();
    }

    public PublishedCalculation published(Long planId) {
        return plans.findById(planId).filter(plan -> plan.getAnalyticalRecommendation() != null).map(this::published)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "Рекомендация не найдена"));
    }

    private PublishedCalculation published(RegionalPlan plan) {
        var calculation = plan.getAnalyticalRecommendation().getPurchasingPowerCalculation();
        var snapshot = readSnapshot(calculation.getInputSnapshot());
        var estimate = estimate(plan, snapshot);
        var view = new Recommendation(publication(plan, snapshot.source()), estimate.selected().sharePercent(), estimate.priceAdjustmentPercent());
        return new PublishedCalculation(view, snapshot, calculation.getMethodVersion());
    }

    private static Estimate estimate(RegionalPlan plan, Calculation calculation) {
        var regionId = plan.getRegion().getId();
        var score = calculation.ranking().stream().filter(row -> row.indicators().regionId().equals(regionId)).findFirst()
                .orElseThrow(AnalyticsService::noStatistics);
        return new Estimate(calculation, score,
                PurchasingPowerCalculator.priceAdjustment(score.index(), calculation.averageIndex(), plan.getMaxDiscountPercent()));
    }

    private static ApiException noStatistics() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_REGION_STATISTICS", "Для региона плана нет учебных показателей");
    }

    private static Publication publication(RegionalPlan plan, Source source) {
        var recommendation = plan.getAnalyticalRecommendation();
        return new Publication(plan.getId(), plan.getRegion().getName(), plan.getPeriodStart(), plan.getPeriodEnd(),
                recommendation.getCreatedAt(), recommendation.getAuthorUser().getFullName(), source,
                recommendation.getPurchasingPowerCalculation().getIndexValue(), recommendation.getRecommendationText());
    }

    private String writeSnapshot(Calculation calculation) {
        try {
            return json.writeValueAsString(calculation);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Не удалось сохранить входные данные расчёта", error);
        }
    }

    private Calculation readSnapshot(String snapshot) {
        try {
            return json.readValue(snapshot, Calculation.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Не удалось прочитать сохранённый расчёт", error);
        }
    }
}
