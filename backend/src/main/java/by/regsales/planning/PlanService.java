package by.regsales.planning;

import by.regsales.catalog.RegionScopeService;
import by.regsales.common.ApiException;
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
        var user = account(actor);
        List<RegionalPlan> visible;
        if (user.getRole().getCode() == RoleCode.SALES_HEAD) {
            visible = attention ? plans.findByStatusOrderByPeriodStartDescIdDesc(PlanStatus.IN_REVIEW)
                    : plans.findAllByOrderByPeriodStartDescIdDesc();
        } else {
            var regionIds = regionScope.accessibleRegions(user).stream().map(Region::getId).toList();
            if (regionIds.isEmpty()) return List.of();
            visible = plans.findByAssignedManagerIdAndRegionIdInAndSentToManagerAtIsNotNullOrderByPeriodStartDescIdDesc(
                    user.getId(), regionIds);
            if (attention) visible = visible.stream().filter(PlanService::editable).toList();
        }
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
        var canEditItems = !head && editable(plan);
        var saved = items.findByRegionalPlanIdOrderByProductNameAscIdAsc(id);
        var previousEnd = plan.getPeriodStart().minusDays(1);
        var previous = facts(plan, previousStart(plan), previousEnd);
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
                head && editable(plan), canEditItems, head && plan.getStatus() == PlanStatus.IN_REVIEW,
                lines, canEditItems ? offers(plan.getPeriodStart()) : List.of(),
                previous.period(), actual == null ? null : actual.period());
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
        requireRole(actor, RoleCode.SALES_HEAD);
        var plan = findPlan(id, true);
        checkVersion(plan, input.version());
        requireEditable(plan);
        applyParameters(plan, input);
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    @Transactional
    public Summary saveItems(Long id, ItemsInput input, AppPrincipal actor) {
        var manager = requireRole(actor, RoleCode.MANAGER);
        var plan = findPlan(id, true);
        requireAccess(plan, manager);
        checkVersion(plan, input.version());
        requireEditable(plan);
        if (input.submit() && input.items().isEmpty()) {
            throw fieldError("items", "Добавьте хотя бы одну товарную позицию");
        }

        var stored = items.findByRegionalPlanIdOrderByProductNameAscIdAsc(id).stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        var productIds = input.items().stream().map(LineInput::productId).toList();
        var catalog = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        validateItems(input.items(), stored, catalog);

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
        if (input.submit()) plan.setStatus(PlanStatus.IN_REVIEW);
        plan.setUpdatedAt(Instant.now());
        items.saveAllAndFlush(changes);
        return summary(plan);
    }

    @Transactional
    public Summary review(Long id, ReviewInput input, AppPrincipal actor) {
        var head = requireRole(actor, RoleCode.SALES_HEAD);
        var plan = findPlan(id, true);
        checkVersion(plan, input.version());
        if (plan.getStatus() != PlanStatus.IN_REVIEW) throw invalidStage("План должен быть на проверке");
        var now = Instant.now();
        if (input.decision() == Decision.RETURN) {
            if (input.reviewComment() == null) throw fieldError("reviewComment", "Укажите, что нужно исправить");
            plan.setStatus(PlanStatus.REVISION);
        } else {
            if (!items.existsByRegionalPlanId(id)) throw fieldError("items", "Для утверждения нужны товарные позиции");
            plan.setApprovedAt(now);
            plan.setApprovedByUser(head);
            plan.setStatus(PlanStatus.APPROVED);
        }
        plan.setReviewComment(input.reviewComment());
        plan.setUpdatedAt(now);
        plans.saveAndFlush(plan);
        return summary(plan);
    }

    private void applyParameters(RegionalPlan plan, Input input) {
        boolean identityChanged = plan.getRegion() == null || !plan.getRegion().getId().equals(input.regionId())
                || !Objects.equals(plan.getPeriodStart(), input.periodStart()) || !Objects.equals(plan.getPeriodEnd(), input.periodEnd());
        if (identityChanged) {
            if (plan.getSentToManagerAt() != null) throw invalidStage("Регион и период уже переданного плана закреплены");
            validatePeriod(input.periodStart(), input.periodEnd());
            var region = regions.findById(input.regionId()).filter(Region::isActive)
                    .orElseThrow(() -> fieldError("regionId", "Выберите действующий регион"));
            if (plans.findByRegionIdAndPeriodStartAndPeriodEnd(region.getId(), input.periodStart(), input.periodEnd())
                    .filter(existing -> !existing.getId().equals(plan.getId())).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "PLAN_EXISTS", "План для этого региона и периода уже существует");
            }
            plan.setRegion(region);
            plan.setPeriodStart(input.periodStart());
            plan.setPeriodEnd(input.periodEnd());
        }

        var manager = input.managerId() == null ? null : users.findById(input.managerId())
                .filter(user -> user.isActive() && user.getRole().getCode() == RoleCode.MANAGER)
                .filter(user -> assignments.hasActiveAssignment(user.getId(), input.regionId(), LocalDate.now(MINSK)))
                .orElseThrow(() -> fieldError("managerId", "Выберите менеджера с действующим назначением на регион"));
        if (manager == null && (input.sendToManager() || plan.getSentToManagerAt() != null)) {
            throw fieldError("managerId", "Выберите ответственного менеджера");
        }
        var now = Instant.now();
        plan.setAssignedManager(manager);
        plan.setTargetRevenue(input.targetRevenue());
        plan.setBudgetLimit(input.budgetLimit());
        plan.setMaxDiscountPercent(input.maxDiscountPercent());
        plan.setPlanningComment(input.planningComment());
        plan.setUpdatedAt(now);
        if (input.sendToManager() && plan.getSentToManagerAt() == null) plan.setSentToManagerAt(now);
    }

    private static void validateItems(List<LineInput> lines, Map<Long, PlanItem> stored, Map<Long, Product> catalog) {
        var seen = new HashSet<Long>();
        var errors = new LinkedHashMap<String, String>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var field = "items[" + index + "]";
            var product = catalog.get(line.productId());
            if (!seen.add(line.productId())) {
                errors.put(field + ".productId", "Товар уже есть в плане");
            } else if (product == null || (!product.isActive() && !stored.containsKey(product.getId()))) {
                errors.put(field + ".productId", "Выберите товар из действующего каталога");
            }
            if (line.plannedQuantity().multiply(line.plannedUnitPrice()).setScale(2, RoundingMode.HALF_UP).precision() > 18) {
                errors.put(field + ".plannedUnitPrice", "Сумма строки превышает допустимое значение");
            }
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте позиции плана", errors);
    }

    private List<Offer> offers(LocalDate date) {
        var active = products.findAllByActiveTrueOrderByNameAsc();
        if (active.isEmpty()) return List.of();
        var byProduct = prices.intersecting(active.stream().map(Product::getId).toList(), date, date).stream()
                .collect(Collectors.toMap(price -> price.getProduct().getId(), Function.identity(),
                        (left, right) -> left.getValidFrom().isAfter(right.getValidFrom()) ? left : right));
        return active.stream().map(product -> {
            ProductPrice price = byProduct.get(product.getId());
            return new Offer(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                    price == null ? product.getCurrentPrice() : price.getUnitPrice(), price != null,
                    price == null ? null : price.getPriceListCode());
        }).toList();
    }

    private Facts facts(RegionalPlan plan, LocalDate from, LocalDate to) {
        var totals = shipments.factByProduct(plan.getRegion().getId(), from, to);
        var revenue = totals.stream().map(ShipmentRepository.ProductTotal::getRevenue).reduce(ZERO_REVENUE, BigDecimal::add);
        var byProduct = totals.stream().collect(Collectors.toMap(ShipmentRepository.ProductTotal::getProductId,
                total -> new Sales(total.getQuantity(), total.getRevenue())));
        return new Facts(new SalesPeriod(from, to, revenue), byProduct);
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
        if (role != RoleCode.MANAGER && role != RoleCode.SALES_HEAD) throw new AccessDeniedException("Доступ к планам ограничен");
        return user;
    }

    private AppUser requireRole(AppPrincipal actor, RoleCode expected) {
        var user = account(actor);
        if (user.getRole().getCode() != expected) throw new AccessDeniedException("Недостаточно прав для работы с планом");
        return user;
    }

    private void requireAccess(RegionalPlan plan, AppUser user) {
        if (user.getRole().getCode() == RoleCode.SALES_HEAD) return;
        if (plan.getSentToManagerAt() == null || plan.getAssignedManager() == null
                || !plan.getAssignedManager().getId().equals(user.getId())) throw notFound();
        regionScope.requireRegionAccess(user, plan.getRegion().getId());
    }

    private RegionalPlan findPlan(Long id, boolean lock) {
        return (lock ? plans.findForUpdate(id) : plans.findById(id)).orElseThrow(PlanService::notFound);
    }

    private static boolean editable(RegionalPlan plan) {
        return plan.getStatus() == PlanStatus.DRAFT || plan.getStatus() == PlanStatus.REVISION;
    }

    private static void requireEditable(RegionalPlan plan) {
        if (!editable(plan)) throw invalidStage("Изменять можно черновик или план на доработке");
    }

    private static void checkVersion(RegionalPlan plan, Long version) {
        if (version == null || version != plan.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_CHANGED", "План изменился. Обновите карточку и повторите действие");
        }
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from.getYear() < 1000 || to.getYear() > 9999 || from.getDayOfMonth() != 1) {
            throw fieldError("periodStart", "Выберите календарный месяц или квартал");
        }
        boolean month = to.equals(from.plusMonths(1).minusDays(1));
        boolean quarter = (from.getMonthValue() - 1) % 3 == 0 && to.equals(from.plusMonths(3).minusDays(1));
        if (!month && !quarter) throw fieldError("periodEnd", "Выберите календарный месяц или квартал");
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

    private record Facts(SalesPeriod period, Map<Long, Sales> byProduct) {}
}
