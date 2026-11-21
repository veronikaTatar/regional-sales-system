package by.regsales.shipment;

import by.regsales.catalog.RegionScopeService;
import by.regsales.common.ApiException;
import by.regsales.common.CalendarPeriod;
import by.regsales.common.XlsxReader;
import by.regsales.domain.AppUser;
import by.regsales.domain.Product;
import by.regsales.domain.Region;
import by.regsales.domain.RoleCode;
import by.regsales.domain.Shipment;
import by.regsales.planning.PlanService;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.repository.ShipmentRepository;
import by.regsales.security.AppPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static by.regsales.shipment.ShipmentDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RegionRepository regions;
    private final RegionScopeService regionScope;
    private final ProductRepository products;
    private final PlanService plans;

    public Options options(AppPrincipal actor) {
        var user = account(actor);
        boolean manager = user.getRole().getCode() == RoleCode.MANAGER;
        var allowed = manager ? regionScope.accessibleRegions(user) : regions.findAll(Sort.by("name"));
        return new Options(manager && !allowed.isEmpty(), allowed.stream().map(RegionOption::from).toList(),
                products.findAllByOrderByNameAscIdAsc().stream().map(ProductOption::from).toList());
    }

    public ResultPage list(Filters filters, AppPrincipal actor) {
        var user = account(actor);
        validateDates(filters.from(), filters.to());
        if ((long) filters.page() * filters.size() > Integer.MAX_VALUE) {
            throw fieldError("page", "Номер страницы слишком большой");
        }
        boolean manager = user.getRole().getCode() == RoleCode.MANAGER;
        var allowed = manager ? regionIds(user) : Set.<Long>of();
        if (manager && filters.regionId() != null && !allowed.contains(filters.regionId())) {
            throw new AccessDeniedException("Нет доступа к данным выбранного региона");
        }
        if (manager && allowed.isEmpty()) return new ResultPage(List.of(), 0, 0, filters.size());
        var sort = Sort.by(filters.direction(), filters.sort().property, "id");
        var specification = matching(filters, manager, allowed);
        var result = shipments.findAll(specification, PageRequest.of(filters.page(), filters.size(), sort));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            result = shipments.findAll(specification, PageRequest.of(result.getTotalPages() - 1, filters.size(), sort));
        }
        return new ResultPage(result.stream().map(Entry::from).toList(), result.getTotalElements(),
                result.getTotalElements() == 0 ? 0 : result.getNumber(), filters.size());
    }

    public Summary summary(SummaryFilters filters, AppPrincipal actor) {
        if (account(actor).getRole().getCode() != RoleCode.ANALYST) {
            throw new AccessDeniedException("Сводка доступна региональному аналитику");
        }
        var from = filters.from();
        var to = filters.to();
        validateDates(from, to);
        var current = summarize(filters, from, to);
        if (from == null || to == null) return new Summary(current, null);

        var previousEnd = from.minusDays(1);
        var previousStart = CalendarPeriod.precedingStart(from, to);
        return new Summary(current, validDate(previousStart) ? summarize(filters, previousStart, previousEnd) : null);
    }

    private SummaryPeriod summarize(SummaryFilters filters, LocalDate from, LocalDate to) {
        var rows = shipments.summarize(filters.regionId(), filters.productId(),
                        from == null ? LocalDate.of(1000, 1, 1) : from,
                        to == null ? LocalDate.of(9999, 12, 31) : to).stream()
                .map(row -> new MonthlySummary(YearMonth.of(row.getShipmentYear(), row.getShipmentMonth()).toString(),
                        row.getRegionId(), row.getProductId(), row.getShipmentCount(), row.getQuantity(), row.getRevenue()))
                .toList();
        return new SummaryPeriod(from, to, rows);
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (!validDate(from)) throw fieldError("from", "Укажите год от 1000 до 9999");
        if (!validDate(to)) throw fieldError("to", "Укажите год от 1000 до 9999");
        if (from != null && to != null && to.isBefore(from)) {
            throw fieldError("to", "Дата окончания должна быть не раньше даты начала");
        }
    }

    @Transactional
    public Entry create(Input input, AppPrincipal actor) {
        var manager = lockManager(actor);
        regionScope.requireRegionAccess(manager, input.regionId());
        if (!validDate(input.shipmentDate())) throw fieldError("shipmentDate", "Укажите год от 1000 до 9999");
        if (amount(input.quantity(), input.unitPrice()).precision() > 18) {
            throw fieldError("unitPrice", "Сумма отгрузки превышает допустимое значение");
        }
        var product = products.findByIdAndActiveTrue(input.productId())
                .orElseThrow(() -> fieldError("productId", "Выберите активный товар"));
        if (input.referenceNumber() != null && shipments.findByReferenceNumberIgnoreCase(input.referenceNumber()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIPMENT_REFERENCE_EXISTS", "Ключ строки уже используется",
                    Map.of("referenceNumber", "Укажите уникальный ключ строки"));
        }
        var region = regions.getReferenceById(input.regionId());
        var shipment = shipment(manager, region, product, input.shipmentDate(), input.quantity(),
                input.unitPrice(), input.referenceNumber());
        shipments.saveAndFlush(shipment);
        plans.resumeWaitingPlans(List.of(input.regionId()));
        return Entry.from(shipment);
    }

    @Transactional
    public ImportResult importRows(List<ImportRow> rows, AppPrincipal actor) {
        var manager = lockManager(actor);
        var allowed = regionIds(manager);
        if (allowed.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "REGION_ASSIGNMENT_REQUIRED",
                "Для импорта отгрузок нужно действующее назначение региона");
        var regionsByCode = regions.findAll().stream()
                .collect(Collectors.toMap(region -> normalizedKey(region.getCode()), Function.identity()));
        var knownProducts = new HashMap<String, Optional<Product>>();
        var seen = new HashMap<String, Integer>();
        var errors = new LinkedHashMap<String, String>();
        var added = new ArrayList<Shipment>();
        int unchanged = 0;
        int skipped = 0;
        for (var row : rows) {
            String label = "Строка " + row.number();
            var region = regionsByCode.get(normalizedKey(row.regionCode()));
            if (region == null) {
                errors.put(label, "Регион «" + row.regionCode() + "» отсутствует в справочнике");
            } else if (!allowed.contains(region.getId())) {
                skipped++;
            } else {
                var first = seen.putIfAbsent(normalizedKey(row.reference()), row.number());
                var product = knownProducts.computeIfAbsent(normalizedKey(row.sku()), key -> products.findBySkuIgnoreCase(row.sku()));
                if (first != null) {
                    errors.put(label, "Этот ключ уже указан в строке " + first);
                } else if (product.isEmpty()) {
                    errors.put(label, "Артикул «" + row.sku() + "» отсутствует в каталоге продукции");
                } else {
                    var existing = shipments.findByReferenceNumberIgnoreCase(row.reference());
                    if (existing.isEmpty()) {
                        added.add(shipment(manager, region, product.get(), row.date(), row.quantity(), row.unitPrice(), row.reference()));
                    } else if (sameShipment(existing.get(), region, product.get(), row)) {
                        unchanged++;
                    } else {
                        errors.put(label, "По этому ключу уже зарегистрирована другая отгрузка");
                    }
                }
            }
            if (errors.size() >= XlsxReader.MAX_ERRORS) break;
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SHIPMENT_IMPORT_CONFLICT",
                "Проверьте отмеченные строки отгрузок", errors);
        shipments.saveAllAndFlush(added);
        plans.resumeWaitingPlans(added.stream().map(shipment -> shipment.getRegion().getId()).collect(Collectors.toSet()));
        return new ImportResult(rows.size(), added.size(), unchanged, skipped);
    }

    private static Specification<Shipment> matching(Filters filters, boolean manager, Set<Long> allowed) {
        return (root, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            var region = root.get("region");
            var product = root.get("product");
            if (manager) conditions.add(region.get("id").in(allowed));
            if (filters.regionId() != null) conditions.add(builder.equal(region.get("id"), filters.regionId()));
            if (filters.productId() != null) conditions.add(builder.equal(product.get("id"), filters.productId()));
            if (filters.from() != null) conditions.add(builder.greaterThanOrEqualTo(root.get("shipmentDate"), filters.from()));
            if (filters.to() != null) conditions.add(builder.lessThanOrEqualTo(root.get("shipmentDate"), filters.to()));
            var search = trim(filters.search());
            if (search != null) {
                for (var term : search.toLowerCase(Locale.ROOT).split("\\s+")) {
                    var pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                    conditions.add(builder.or(
                            builder.like(builder.lower(root.get("referenceNumber")), pattern, '!'),
                            builder.like(builder.lower(product.get("sku")), pattern, '!'),
                            builder.like(builder.lower(product.get("name")), pattern, '!'),
                            builder.like(builder.lower(region.get("name")), pattern, '!')));
                }
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }

    private AppUser account(AppPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Войдите в систему");
        var user = users.findById(actor.getId()).orElse(null);
        if (user == null || !user.isActive() || user.getSessionVersion() != actor.getSessionVersion()
                || user.getRole().getCode() == RoleCode.ADMIN) {
            throw new AccessDeniedException("Учётная запись недоступна");
        }
        return user;
    }

    private AppUser lockManager(AppPrincipal actor) {
        // Serialize registration and imports with catalog updates and other shipment writes.
        roles.lockByCode(RoleCode.MANAGER).orElseThrow();
        var user = account(actor);
        if (user.getRole().getCode() != RoleCode.MANAGER) {
            throw new AccessDeniedException("Требуется учётная запись менеджера");
        }
        return user;
    }

    private Set<Long> regionIds(AppUser manager) {
        return regionScope.accessibleRegions(manager).stream().map(Region::getId).collect(Collectors.toSet());
    }

    private static Shipment shipment(AppUser manager, Region region, Product product, LocalDate date,
                                     BigDecimal quantity, BigDecimal unitPrice, String reference) {
        var shipment = new Shipment();
        shipment.setRecordedByUser(manager);
        shipment.setRegion(region);
        shipment.setProduct(product);
        shipment.setShipmentDate(date);
        shipment.setQuantity(quantity);
        shipment.setUnitPrice(unitPrice);
        shipment.setReferenceNumber(reference);
        return shipment;
    }

    private static boolean sameShipment(Shipment shipment, Region region, Product product, ImportRow row) {
        return Objects.equals(shipment.getRegion().getId(), region.getId())
                && Objects.equals(shipment.getProduct().getId(), product.getId())
                && shipment.getShipmentDate().equals(row.date())
                && shipment.getQuantity().compareTo(row.quantity()) == 0
                && shipment.getUnitPrice().compareTo(row.unitPrice()) == 0;
    }

    private static String normalizedKey(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private static ApiException fieldError(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Проверьте введённые данные", Map.of(field, message));
    }
}
