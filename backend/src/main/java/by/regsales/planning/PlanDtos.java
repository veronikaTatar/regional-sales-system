package by.regsales.planning;

import by.regsales.analytics.AnalyticsDtos.Recommendation;
import by.regsales.domain.AppUser;
import by.regsales.domain.PlanStatus;
import by.regsales.domain.RegionalPlan;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {
    private PlanDtos() {}

    public record Input(
            @NotNull(message = "Выберите регион") @Positive Long regionId,
            @NotNull(message = "Укажите начало периода") LocalDate periodStart,
            @NotNull(message = "Укажите конец периода") LocalDate periodEnd,
            @Positive Long managerId,
            @NotNull(message = "Укажите целевую выручку") @Positive(message = "Целевая выручка должна быть больше нуля")
            @Digits(integer = 16, fraction = 2, message = "Укажите сумму с точностью до копейки") BigDecimal targetRevenue,
            @PositiveOrZero(message = "Лимит должен быть неотрицательным")
            @Digits(integer = 16, fraction = 2, message = "Укажите сумму с точностью до копейки") BigDecimal budgetLimit,
            @DecimalMin(value = "0", message = "Укажите процент от 0 до 100") @DecimalMax(value = "100", message = "Укажите процент от 0 до 100")
            @Digits(integer = 3, fraction = 2, message = "Укажите процент с точностью до двух знаков") BigDecimal maxDiscountPercent,
            @Size(max = 2000, message = "Комментарий должен содержать до 2000 символов") String planningComment,
            @JsonAlias({"sendToManager", "requestSales"}) boolean requestAnalysis,
            @PositiveOrZero Long version) {
        public Input {
            targetRevenue = normalize(targetRevenue);
            budgetLimit = normalize(budgetLimit);
            maxDiscountPercent = normalize(maxDiscountPercent);
            planningComment = trim(planningComment);
        }
    }

    public record LineInput(
            @NotNull(message = "Выберите товар") @Positive Long productId,
            @NotNull(message = "Укажите количество") @Positive(message = "Количество должно быть больше нуля")
            @Digits(integer = 11, fraction = 3, message = "Допустимо до 11 цифр до разделителя и до 3 после") BigDecimal plannedQuantity,
            @NotNull(message = "Укажите цену") @PositiveOrZero(message = "Цена должна быть неотрицательной")
            @Digits(integer = 12, fraction = 2, message = "Допустимо до 12 цифр до разделителя и до 2 после") BigDecimal plannedUnitPrice) {
        public LineInput {
            plannedQuantity = normalize(plannedQuantity);
            plannedUnitPrice = normalize(plannedUnitPrice);
        }
    }

    public record ItemsInput(@NotNull @PositiveOrZero Long version,
                             @NotNull @Size(max = 5000) List<@NotNull LineInput> items,
                             boolean submit) {}

    public enum Decision { APPROVE, RETURN, RETURN_ANALYSIS, REJECT }

    public record ReviewInput(@NotNull @PositiveOrZero Long version, @NotNull Decision decision,
                              @Size(max = 2000, message = "Комментарий должен содержать до 2000 символов") String reviewComment) {
        public ReviewInput { reviewComment = trim(reviewComment); }
    }

    public record ManagerChoice(Long id, String name) {
        static ManagerChoice from(AppUser manager) {
            return new ManagerChoice(manager.getId(), manager.getFullName());
        }
    }

    public record RegionChoice(Long id, String name, List<ManagerChoice> managers) {}

    public record Summary(Long id, long version, Long regionId, String regionName, ManagerChoice manager,
                          LocalDate periodStart, LocalDate periodEnd, PlanStatus status, Instant sentToManagerAt,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal targetRevenue,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal plannedRevenue,
                          String validationNote) {
        static Summary from(RegionalPlan plan, BigDecimal revenue) {
            var manager = plan.getAssignedManager();
            var target = plan.getTargetRevenue();
            return new Summary(plan.getId(), plan.getVersion(), plan.getRegion().getId(), plan.getRegion().getName(),
                    manager == null ? null : ManagerChoice.from(manager), plan.getPeriodStart(), plan.getPeriodEnd(),
                    plan.getStatus(), plan.getSentToManagerAt(), target == null ? null : target.setScale(2), revenue.setScale(2),
                    plan.getValidationNote());
        }
    }

    public record Offer(Long productId, String sku, String name, String unit,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
                        boolean fromHistory, String priceListCode,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal suggestedUnitPrice) {}

    public record Sales(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal revenue) {}

    public record SalesPeriod(LocalDate from, LocalDate to, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal revenue) {}

    public record SalesSubmission(SalesPeriod period, long shipmentCount, Instant submittedAt) {}

    public record Item(Long productId, String sku, String name, String unit,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal plannedQuantity,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal plannedUnitPrice,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal plannedAmount,
                       Sales previous, Sales actual) {}

    public record Detail(Summary plan,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal budgetLimit,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal maxDiscountPercent,
                         String planningComment, String reviewComment, String approvedBy, Instant approvedAt,
                         boolean canEditParameters, boolean canEditItems, boolean canReview, boolean canAnalyze,
                         boolean canWithdraw, boolean canSubmitSales,
                         Recommendation recommendation,
                         List<Item> items, List<Offer> offers, SalesPeriod previous, SalesPeriod actual,
                         SalesSubmission salesSubmission, String planningCheck) {}

    public record Result(Summary plan, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal actualRevenue) {}

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
