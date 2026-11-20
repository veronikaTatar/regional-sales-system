package by.regsales.planning;

import by.regsales.analytics.AnalyticsDtos.Estimate;
import by.regsales.analytics.AnalyticsDtos.PreviewInput;
import by.regsales.analytics.AnalyticsDtos.PublishInput;
import by.regsales.analytics.AnalyticsDtos.Recommendation;
import by.regsales.analytics.AnalyticsService;
import by.regsales.analytics.PurchasingPowerCalculator;
import by.regsales.catalog.RegionScopeService;
import by.regsales.common.ApiException;
import by.regsales.common.CalendarPeriod;
import by.regsales.domain.AppUser;
import by.regsales.domain.PlanItem;
import by.regsales.domain.PlanStatus;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.domain.Region;
import by.regsales.domain.RegionalPlan;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.PlanItemRepository;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RegionalPlanRepository;
import by.regsales.repository.ShipmentRepository;
import by.regsales.security.AppPrincipal;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static by.regsales.planning.PlanDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {
    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final BigDecimal ZERO_REVENUE = BigDecimal.ZERO.setScale(2);
    private static final Sales NO_SALES = new Sales(BigDecimal.ZERO, ZERO_REVENUE);
    private final RegionalPlanRepository plans;
    private final PlanItemRepository items;
    private final AppUserRepository users;
    private final RegionRepository regions;
    private final RegionAssignmentRepository assignments;
    private final RegionScopeService regionScope;
    private final ProductRepository products;
    private final ProductPriceRepository prices;
    private final ShipmentRepository shipments;
    private final AnalyticsService analytics;
    private final Validator validator;

    public List<RegionChoice> options(AppPrincipal actor) {
        requireRole(actor, RoleCode.SALES_HEAD);
        var byRegion = assignments.findActiveAssignments(LocalDate.now(MINSK), RoleCode.MANAGER).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getRegion().getId(),
                        Collectors.mapping(assignment -> ManagerChoice.from(assignment.getManager()), Collectors.toList())));
        return regions.findAllByActiveTrueOrderByNameAsc().stream()
                .map(region -> new RegionChoice(region.getId(), region.getName(), byRegion.getOrDefault(region.getId(), List.of())))
                .toList();
    }

    public List<Summary> list(AppPrincipal actor, boolean attention) {
        return summarize(visiblePlans(account(actor), attention));
    }

    public List<Result> results(AppPrincipal actor) {
        var user = account(actor);
        var approved = user.getRole().getCode() == RoleCode.MANAGER
                ? visiblePlans(user, false).stream().filter(plan -> plan.getStatus() == PlanStatus.APPROVED).toList()
                : plans.findByStatusOrderByPeriodStartDescIdDesc(PlanStatus.APPROVED);
        if (approved.isEmpty()) return List.of();
        var facts = shipments.factByPlan(approved.stream().map(RegionalPlan::getId).toList()).stream()
                .collect(Collectors.toMap(ShipmentRepository.PlanTotal::getPlanId, ShipmentRepository.PlanTotal::getRevenue));
        return summarize(approved).stream().map(plan -> new Result(plan, facts.getOrDefault(plan.id(), ZERO_REVENUE))).toList();
    }

    private List<RegionalPlan> visiblePlans(AppUser user, boolean attention) {
        return switch (user.getRole().getCode()) {
            case SALES_HEAD -> attention ? plans.findByStatusOrderByPeriodStartDescIdDesc(PlanStatus.IN_REVIEW)
                    : plans.findAllByOrderByPeriodStartDescIdDesc();
            case ANALYST -> plans.findByStatusOrderByPeriodStartDescIdDesc(PlanStatus.AWAITING_ANALYSIS);
            case MANAGER -> {
                var regionIds = regionScope.accessibleRegions(user).stream().map(Region::getId).toList();
                if (regionIds.isEmpty()) yield List.of();
                var visible = plans.findByAssignedManagerIdAndRegionIdInAndSentToManagerAtIsNotNullOrderByPeriodStartDescIdDesc(
                        user.getId(), regionIds);
                yield attention ? visible.stream()
                        .filter(plan -> awaitingSales(plan) || editable(plan) || plan.getStatus() == PlanStatus.APPROVED)
                        .sorted(Comparator.comparing(plan -> plan.getStatus() == PlanStatus.APPROVED))
                        .toList() : visible;
            }
            default -> throw new AccessDeniedException("Доступ к планам ограничен");
        };
    }

    private List<Summary> summarize(List<RegionalPlan> visible) {
        if (visible.isEmpty()) return List.of();
        var revenue = items.revenueByPlan(visible.stream().map(RegionalPlan::getId).toList()).stream()
                .collect(Collectors.toMap(PlanItemRepository.RevenueTotal::getPlanId, PlanItemRepository.RevenueTotal::getRevenue));
        return visible.stream().map(plan -> Summary.from(plan, revenue.getOrDefault(plan.getId(), BigDecimal.ZERO))).toList();
    }

    public Detail detail(Long id, AppPrincipal actor) {
        var user = account(actor);
        var plan = findPlan(id, false);
        requireAccess(plan, user);
        var head = user.getRole().getCode() == RoleCode.SALES_HEAD;
        var manager = user.getRole().getCode() == RoleCode.MANAGER;
        var onReview = plan.getStatus() == PlanStatus.IN_REVIEW;
        var canEditItems = manager && editable(plan);
        var canAnalyze = user.getRole().getCode() == RoleCode.ANALYST && plan.getStatus() == PlanStatus.AWAITING_ANALYSIS;
        var recommendation = analytics.recommendation(plan);
        var saved = items.findByRegionalPlanIdOrderByProductNameAscIdAsc(id);
        var previous = previousFacts(plan);
        var actual = plan.getStatus() == PlanStatus.APPROVED
                ? facts(plan, plan.getPeriodStart(), plan.getPeriodEnd()) : null;
        var lines = saved.stream().map(item -> {
            var product = item.getProduct();
            return new Item(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                    item.getPlannedQuantity(), item.getPlannedUnitPrice(), item.getPlannedAmount(),
                    previous.byProduct().getOrDefault(product.getId(), NO_SALES),
                    actual == null ? null : actual.byProduct().getOrDefault(product.getId(), NO_SALES));
        }).toList();
        var revenue = saved.stream().map(PlanItem::getPlannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Detail(Summary.from(plan, revenue), plan.getBudgetLimit(), plan.getMaxDiscountPercent(),
                plan.getPlanningComment(), plan.getReviewComment(),
                plan.getApprovedByUser() == null ? null : plan.getApprovedByUser().getFullName(), plan.getApprovedAt(),
                head && canEditParameters(plan), canEditItems, head && onReview, canAnalyze, manager && onReview, manager && awaitingSales(plan),
                recommendation, lines, canEditItems ? offers(plan, recommendation) : List.of(),
                previous.period(), actual == null ? null : actual.period(), salesSubmission(plan, previous), plan.getPlanningCheck());
    }

    @Transactional
    public Summary create(Input input, AppPrincipal actor) {
        var head = requireRole(actor, RoleCode.SALES_HEAD);
        var plan = new RegionalPlan();
        plan.setCreatedByUser(head);
        applyParameters(plan, input);
        return Summary.from(plans.saveAndFlush(plan), BigDecimal.ZERO);
    }

    @Transactional
    public Summary update(Long id, Input input, AppPrincipal actor) {
        var plan = headPlanForUpdate(id, input.version(), actor);
        if (!canEditParameters(plan)) throw invalidStage("Параметры плана недоступны для редактирования");
        applyParameters(plan, input);
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    @Transactional
    public Summary cancel(Long id, Long version, AppPrincipal actor) {
        var plan = headPlanForUpdate(id, version, actor);
        if (List.of(PlanStatus.APPROVED, PlanStatus.REJECTED, PlanStatus.CANCELLED).contains(plan.getStatus())) {
            throw invalidStage("Отменить можно только активный неутверждённый план");
        }
        plan.setStatus(PlanStatus.CANCELLED);
        plan.setUpdatedAt(Instant.now());
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    @Transactional
    public void delete(Long id, Long version, AppPrincipal actor) {
        var plan = headPlanForUpdate(id, version, actor);
        if (plan.getStatus() == PlanStatus.APPROVED) throw invalidStage("Утверждённый план защищён от удаления");
        items.deleteForPlan(id);
        plans.delete(plan);
    }

    @Transactional
    public Summary submitSales(Long id, Long version, AppPrincipal actor) {
        var plan = managerPlanForUpdate(id, version, actor);
        if (!awaitingSales(plan)) throw invalidStage("План должен ожидать сведения о продажах");

        checkSalesForAnalysis(plan, Instant.now());
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    private void checkSalesForAnalysis(RegionalPlan plan, Instant now) {
        var reported = previousFacts(plan);
        if (reported.shipmentCount() == 0) {
            plan.setStatus(PlanStatus.SALES_REVISION);
            plan.setValidationNote("Добавьте или импортируйте отгрузки по региону за указанный отчётный период.");
            plan.setSalesSubmittedAt(null);
            plan.setSalesShipmentCount(null);
            plan.setSalesRevenue(null);
        } else {
            plan.setStatus(PlanStatus.AWAITING_ANALYSIS);
            plan.setSalesSubmittedAt(now);
            plan.setSalesShipmentCount(reported.shipmentCount());
            plan.setSalesRevenue(reported.period().revenue());
            plan.setValidationNote(null);
        }
        plan.setPlanningCheck(null);
        plan.setUpdatedAt(now);
    }

    public Estimate previewAnalysis(Long id, PreviewInput input, AppPrincipal actor) {
        var analyst = requireRole(actor, RoleCode.ANALYST);
        var plan = findPlan(id, false);
        requirePendingAnalysis(plan, analyst, input.version());
        return analytics.estimate(plan, input.weights());
    }

    @Transactional
    public Summary publishAnalysis(Long id, PublishInput input, AppPrincipal actor) {
        var analyst = requireRole(actor, RoleCode.ANALYST);
        var plan = findPlan(id, true);
        requirePendingAnalysis(plan, analyst, input.version());
        if (!eligibleOnRegion(plan.getAssignedManager(), plan.getRegion().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MANAGER_UNAVAILABLE",
                    "Руководителю нужно выбрать менеджера с действующим назначением на регион");
        }
        var recommendation = analytics.publish(plan, input, analyst);
        plan.setAnalyticalRecommendation(recommendation);
        plan.setPurchasingPowerCalculation(recommendation.getPurchasingPowerCalculation());
        var now = Instant.now();
        plan.setStatus(PlanStatus.IN_PROGRESS);
        plan.setUpdatedAt(now);
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    @Transactional
    public Summary saveItems(Long id, ItemsInput input, AppPrincipal actor) {
        var plan = managerPlanForUpdate(id, input.version(), actor);
        requireEditable(plan);

        var stored = items.findByRegionalPlanIdOrderByProductNameAscIdAsc(id).stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        var productIds = input.items().stream().map(LineInput::productId).filter(Objects::nonNull).distinct().toList();
        var catalog = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        var errors = itemErrors(input.items(), stored, catalog);
        if (input.submit() && input.items().isEmpty()) errors.put("items", "Добавьте хотя бы одну товарную позицию");
        if (!errors.isEmpty()) {
            if (!input.submit()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте позиции плана", errors);
            plan.setStatus(PlanStatus.NEEDS_CORRECTION);
            plan.setValidationNote(String.join("\n", errors.values()));
            plan.setPlanningCheck(null);
            plan.setUpdatedAt(Instant.now());
            plans.saveAndFlush(plan);
            return summary(plan);
        }

        var changes = input.items().stream().map(line -> {
            var item = stored.remove(line.productId());
            if (item == null) {
                item = new PlanItem();
                item.setRegionalPlan(plan);
                item.setProduct(catalog.get(line.productId()));
            }
            item.setPlannedQuantity(line.plannedQuantity());
            item.setPlannedUnitPrice(line.plannedUnitPrice());
            return item;
        }).toList();
        items.deleteAll(stored.values());
        plan.setStatus(input.submit() ? PlanStatus.IN_REVIEW : PlanStatus.IN_PROGRESS);
        plan.setValidationNote(null);
        plan.setPlanningCheck(input.submit() ? checkPlanningParameters(plan, input.items(), catalog) : null);
        plan.setUpdatedAt(Instant.now());
        items.saveAllAndFlush(changes);
        return summary(plan);
    }

    @Transactional
    public Summary withdraw(Long id, Long version, AppPrincipal actor) {
        var plan = managerPlanForUpdate(id, version, actor);
        if (plan.getStatus() != PlanStatus.IN_REVIEW) throw invalidStage("Отозвать можно план на согласовании");
        plan.setStatus(PlanStatus.IN_PROGRESS);
        plan.setPlanningCheck(null);
        plan.setUpdatedAt(Instant.now());
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    @Transactional
    public Summary review(Long id, ReviewInput input, AppPrincipal actor) {
        var head = requireRole(actor, RoleCode.SALES_HEAD);
        var plan = findPlan(id, true);
        checkVersion(plan, input.version());
        if (plan.getStatus() != PlanStatus.IN_REVIEW) throw invalidStage("План должен быть на согласовании");
        requireAnalysis(plan);
        var now = Instant.now();
        if (input.decision() == Decision.APPROVE) {
            if (!items.existsByRegionalPlanId(id)) throw fieldError("items", "Для утверждения нужны товарные позиции");
            plan.setApprovedAt(now);
            plan.setApprovedByUser(head);
            plan.setStatus(PlanStatus.APPROVED);
        } else {
            if (input.reviewComment() == null) throw fieldError("reviewComment", "Укажите причину решения");
            switch (input.decision()) {
                case RETURN -> plan.setStatus(PlanStatus.REVISION);
                case RETURN_ANALYSIS -> plan.setStatus(PlanStatus.AWAITING_ANALYSIS);
                case REJECT -> plan.setStatus(PlanStatus.REJECTED);
                default -> throw new IllegalStateException("Недопустимое решение о возврате");
            }
            if (input.decision() != Decision.REJECT) plan.setPlanningCheck(null);
        }
        plan.setReviewComment(input.reviewComment());
        plan.setUpdatedAt(now);
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    private void applyParameters(RegionalPlan plan, Input input) {
        var assigned = plan.getSentToManagerAt() != null;
        boolean identityChanged = plan.getRegion() == null || !plan.getRegion().getId().equals(input.regionId())
                || !Objects.equals(plan.getPeriodStart(), input.periodStart()) || !Objects.equals(plan.getPeriodEnd(), input.periodEnd());
        if (identityChanged) {
            if (assigned) throw invalidStage("Регион и период уже переданного плана закреплены");
            var period = new CalendarPeriod(input.periodStart(), input.periodEnd());
            var region = regions.findById(input.regionId()).filter(Region::isActive)
                    .orElseThrow(() -> fieldError("regionId", "Выберите действующий регион"));
            if (plans.existsByRegionIdAndPeriodStartAndPeriodEndAndStatusNot(region.getId(), period.start(), period.end(), PlanStatus.CANCELLED)) {
                throw new ApiException(HttpStatus.CONFLICT, "PLAN_EXISTS", "План для этого региона и периода уже существует");
            }
            plan.setRegion(region);
            plan.setPeriodStart(period.start());
            plan.setPeriodEnd(period.end());
        }

        var manager = input.managerId() == null ? null : users.findById(input.managerId())
                .filter(user -> eligibleOnRegion(user, input.regionId()))
                .orElseThrow(() -> fieldError("managerId", "Выберите менеджера с действующим назначением на регион"));
        if (manager == null && (input.requestAnalysis() || assigned)) {
            throw fieldError("managerId", "Выберите ответственного менеджера");
        }
        var now = Instant.now();
        plan.setAssignedManager(manager);
        plan.setTargetRevenue(input.targetRevenue());
        plan.setBudgetLimit(input.budgetLimit());
        plan.setMaxDiscountPercent(input.maxDiscountPercent());
        plan.setPlanningComment(input.planningComment());
        plan.setUpdatedAt(now);
        if (input.requestAnalysis() && (!assigned || awaitingSales(plan))) {
            if (!assigned) plan.setSentToManagerAt(now);
            checkSalesForAnalysis(plan, now);
        }
    }

    private boolean eligibleOnRegion(AppUser manager, Long regionId) {
        return manager != null && manager.isActive() && manager.getRole().getCode() == RoleCode.MANAGER
                && assignments.hasActiveAssignment(manager.getId(), regionId, LocalDate.now(MINSK));
    }

    private Map<String, String> itemErrors(List<LineInput> lines, Map<Long, PlanItem> stored, Map<Long, Product> catalog) {
        var seen = new HashSet<Long>();
        var errors = new LinkedHashMap<String, String>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var field = "items[" + index + "]";
            var row = "Строка " + (index + 1) + ". ";
            var violations = validator.validate(line);
            violations.forEach(violation -> errors.putIfAbsent(field + "." + violation.getPropertyPath(), row + violation.getMessage()));
            if (!errors.containsKey(field + ".productId")) {
                var product = catalog.get(line.productId());
                if (!seen.add(line.productId())) {
                    errors.put(field + ".productId", row + "Товар уже есть в плане");
                } else if (product == null || (!product.isActive() && !stored.containsKey(product.getId()))) {
                    errors.put(field + ".productId", row + "Выберите товар из действующего каталога");
                }
            }
            if (violations.isEmpty() && line.plannedQuantity().multiply(line.plannedUnitPrice()).setScale(2, RoundingMode.HALF_UP).precision() > 18) {
                errors.put(field + ".plannedUnitPrice", row + "Сумма строки превышает допустимое значение");
            }
        }
        return errors;
    }

    private List<Offer> offers(RegionalPlan plan, Recommendation recommendation) {
        var active = products.findAllByActiveTrueOrderByNameAsc();
        if (active.isEmpty()) return List.of();
        var byProduct = applicablePrices(active.stream().map(Product::getId).toList(), plan.getPeriodStart());
        return active.stream().map(product -> {
            ProductPrice price = byProduct.get(product.getId());
            var base = price == null ? product.getCurrentPrice() : price.getUnitPrice();
            var suggested = recommendation == null ? null : PurchasingPowerCalculator.suggestedPrice(
                    base, recommendation.priceAdjustmentPercent(), plan.getMaxDiscountPercent());
            return new Offer(product.getId(), product.getSku(), product.getName(), product.getUnit(), base,
                    price != null, price == null ? null : price.getPriceListCode(), suggested);
        }).toList();
    }

    private Map<Long, ProductPrice> applicablePrices(Collection<Long> productIds, LocalDate date) {
        return prices.intersecting(productIds, date, date).stream()
                .collect(Collectors.toMap(price -> price.getProduct().getId(), Function.identity(),
                        (left, right) -> left.getValidFrom().isAfter(right.getValidFrom()) ? left : right));
    }

    private String checkPlanningParameters(RegionalPlan plan, List<LineInput> lines, Map<Long, Product> catalog) {
        var notes = new ArrayList<String>();
        var revenue = lines.stream().map(line -> line.plannedQuantity().multiply(line.plannedUnitPrice()).setScale(2, RoundingMode.HALF_UP))
                .reduce(ZERO_REVENUE, BigDecimal::add);
        if (plan.getTargetRevenue() != null) {
            var gap = plan.getTargetRevenue().subtract(revenue);
            notes.add(gap.signum() > 0 ? "Выручка ниже цели на " + gap.toPlainString() + " BYN." : "Цель по выручке достигнута.");
        }
        if (plan.getMaxDiscountPercent() != null) {
            var prices = applicablePrices(catalog.keySet(), plan.getPeriodStart());
            var minimumMultiplier = BigDecimal.ONE.subtract(plan.getMaxDiscountPercent().movePointLeft(2));
            long beyondLimit = lines.stream().filter(line -> {
                var price = prices.get(line.productId());
                var base = price == null ? catalog.get(line.productId()).getCurrentPrice() : price.getUnitPrice();
                return line.plannedUnitPrice().compareTo(base.multiply(minimumMultiplier)) < 0;
            }).count();
            notes.add(beyondLimit == 0 ? "Предельная скидка соблюдена."
                    : "Число позиций с превышением предельной скидки — " + beyondLimit + ".");
        }
        if (plan.getBudgetLimit() != null) notes.add("Проверка лимита затрат требует данных о себестоимости.");
        notes.add("Проверка ресурсов требует данных об остатках.");
        return String.join("\n", notes);
    }

    private Facts previousFacts(RegionalPlan plan) {
        return facts(plan, previousStart(plan), plan.getPeriodStart().minusDays(1));
    }

    private static SalesSubmission salesSubmission(RegionalPlan plan, Facts previous) {
        if (plan.getSentToManagerAt() == null) return null;
        if (plan.getSalesSubmittedAt() == null || awaitingSales(plan)) {
            return new SalesSubmission(previous.period(), previous.shipmentCount(), null);
        }
        var period = new SalesPeriod(previous.period().from(), previous.period().to(), plan.getSalesRevenue());
        return new SalesSubmission(period, plan.getSalesShipmentCount(), plan.getSalesSubmittedAt());
    }

    private Facts facts(RegionalPlan plan, LocalDate from, LocalDate to) {
        var totals = shipments.factByProduct(plan.getRegion().getId(), from, to);
        var revenue = totals.stream().map(ShipmentRepository.ProductTotal::getRevenue).reduce(ZERO_REVENUE, BigDecimal::add);
        var byProduct = totals.stream().collect(Collectors.toMap(ShipmentRepository.ProductTotal::getProductId,
                total -> new Sales(total.getQuantity(), total.getRevenue())));
        var shipmentCount = totals.stream().mapToLong(ShipmentRepository.ProductTotal::getShipmentCount).sum();
        return new Facts(new SalesPeriod(from, to, revenue), byProduct, shipmentCount);
    }

    private static LocalDate previousStart(RegionalPlan plan) {
        var start = plan.getPeriodStart();
        var end = plan.getPeriodEnd();
        if (start.getDayOfMonth() == 1 && end.getDayOfMonth() == end.lengthOfMonth()) {
            return start.minusMonths(ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1);
        }
        return start.minusDays(ChronoUnit.DAYS.between(start, end) + 1);
    }

    private Summary summary(RegionalPlan plan) {
        var totals = items.revenueByPlan(List.of(plan.getId()));
        return Summary.from(plan, totals.isEmpty() ? BigDecimal.ZERO : totals.getFirst().getRevenue());
    }

    private AppUser account(AppPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Войдите в систему");
        var user = users.findById(actor.getId()).orElse(null);
        if (user == null || !user.isActive() || user.getSessionVersion() != actor.getSessionVersion()) {
            throw new AccessDeniedException("Учётная запись недоступна");
        }
        var role = user.getRole().getCode();
        if (role == RoleCode.ADMIN) throw new AccessDeniedException("Доступ к планам ограничен");
        return user;
    }

    private AppUser requireRole(AppPrincipal actor, RoleCode expected) {
        var user = account(actor);
        if (user.getRole().getCode() != expected) throw new AccessDeniedException("Недостаточно прав для работы с планом");
        return user;
    }

    private void requireAccess(RegionalPlan plan, AppUser user) {
        if (user.getRole().getCode() == RoleCode.SALES_HEAD) return;
        if (user.getRole().getCode() == RoleCode.ANALYST) {
            if (plan.getStatus() == PlanStatus.AWAITING_ANALYSIS || plan.getStatus() == PlanStatus.APPROVED
                    || plan.getAnalyticalRecommendation() != null) return;
            throw notFound();
        }
        if (plan.getSentToManagerAt() == null || plan.getAssignedManager() == null
                || !plan.getAssignedManager().getId().equals(user.getId())) throw notFound();
        regionScope.requireRegionAccess(user, plan.getRegion().getId());
    }

    private RegionalPlan findPlan(Long id, boolean lock) {
        return (lock ? plans.findForUpdate(id) : plans.findById(id)).orElseThrow(PlanService::notFound);
    }

    private RegionalPlan headPlanForUpdate(Long id, Long version, AppPrincipal actor) {
        requireRole(actor, RoleCode.SALES_HEAD);
        var plan = findPlan(id, true);
        checkVersion(plan, version);
        return plan;
    }

    private RegionalPlan managerPlanForUpdate(Long id, Long version, AppPrincipal actor) {
        var manager = requireRole(actor, RoleCode.MANAGER);
        var plan = findPlan(id, true);
        requireAccess(plan, manager);
        checkVersion(plan, version);
        return plan;
    }

    private static boolean editable(RegionalPlan plan) {
        return switch (plan.getStatus()) {
            case IN_PROGRESS, NEEDS_CORRECTION, REVISION -> true;
            default -> false;
        };
    }

    private static boolean awaitingSales(RegionalPlan plan) {
        return switch (plan.getStatus()) {
            case AWAITING_SALES_DATA, SALES_REVISION -> true;
            default -> false;
        };
    }

    private static boolean canEditParameters(RegionalPlan plan) {
        return editable(plan) || awaitingSales(plan) || plan.getStatus() == PlanStatus.DRAFT || plan.getStatus() == PlanStatus.AWAITING_ANALYSIS;
    }

    private void requirePendingAnalysis(RegionalPlan plan, AppUser analyst, Long version) {
        requireAccess(plan, analyst);
        checkVersion(plan, version);
        if (plan.getStatus() != PlanStatus.AWAITING_ANALYSIS || plan.getSalesSubmittedAt() == null) {
            throw invalidStage("Анализ доступен после проверки сведений о продажах");
        }
    }

    private static void requireEditable(RegionalPlan plan) {
        if (!editable(plan)) throw invalidStage("Позиции плана недоступны для редактирования");
        requireAnalysis(plan);
    }

    private static void requireAnalysis(RegionalPlan plan) {
        if (plan.getSalesSubmittedAt() == null || plan.getAnalyticalRecommendation() == null) {
            throw invalidStage("Для работы с проектом нужны проверенные сведения о продажах и рекомендация аналитика");
        }
    }

    private static void checkVersion(RegionalPlan plan, Long version) {
        if (version == null || version != plan.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_CHANGED", "План изменился. Обновите карточку и повторите действие");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "План не найден");
    }

    private static ApiException invalidStage(String message) {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_STAGE", message);
    }

    private static ApiException fieldError(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте заполнение плана", Map.of(field, message));
    }

    private record Facts(SalesPeriod period, Map<Long, Sales> byProduct, long shipmentCount) {}
}
