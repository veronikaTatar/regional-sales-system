package by.regsales.planning;

import by.regsales.domain.AppUser;
import by.regsales.domain.PlanStatus;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.domain.Shipment;
import by.regsales.repository.AnalyticalRecommendationRepository;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.PlanItemRepository;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.PurchasingPowerCalculationRepository;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RegionalPlanRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.repository.ShipmentRepository;
import by.regsales.security.AppPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanWorkflowIntegrationTest {
    private static final String PATH = "/api/plans";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegionalPlanRepository plans;
    @Autowired PlanItemRepository planItems;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RegionRepository regions;
    @Autowired RegionAssignmentRepository assignments;
    @Autowired ProductRepository products;
    @Autowired ProductPriceRepository prices;
    @Autowired ShipmentRepository shipments;
    @Autowired AnalyticalRecommendationRepository recommendations;
    @Autowired PurchasingPowerCalculationRepository calculations;

    @AfterEach void cleanPlanData() {
        planItems.deleteAllInBatch();
        plans.deleteAllInBatch();
        recommendations.deleteAllInBatch();
        calculations.deleteAllInBatch();
        prices.deleteAllInBatch();
        shipments.deleteAllInBatch();
    }

    @Test void analystPublishesPlanManagerCompletesItAndHeadReturnsThenApprovesIt() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var analyst = as(account(RoleCode.ANALYST));
        var region = region("BY-HM");
        region("BY-MA");
        var responsible = account(RoleCode.MANAGER);
        var assignment = assign(responsible, region);
        var manager = as(responsible);
        var colleague = assign(account(RoleCode.MANAGER), region);
        var otherManager = as(colleague.getManager());
        var product = product();
        var currentPrice = product();
        var history = new ProductPrice();
        history.setProduct(product);
        history.setValidFrom(LocalDate.parse("2026-01-01"));
        history.setValidTo(LocalDate.parse("2026-03-31"));
        history.setPriceListCode("PL-2026-Q1");
        history.setUnitPrice(new BigDecimal("4.21"));
        prices.saveAndFlush(history);
        shipment(responsible, region, product, "2025-12-31", "3", "4.10");
        shipment(responsible, region, product, "2026-02-14", "2.5", "4.10");
        shipment(responsible, region(), product, "2025-12-31", "99", "4.10");

        var parameters = parameters(region);
        var draft = read(write(post(PATH), head, parameters).andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetRevenue").value("600.00"))
                .andExpect(jsonPath("$.status").value("DRAFT")));
        var path = PATH + "/" + draft.get("id").asLong();
        mvc.perform(get(path).with(manager)).andExpect(status().isNotFound());
        mvc.perform(get(path).with(analyst)).andExpect(status().isNotFound());

        parameters.put("version", version(draft));
        parameters.put("requestAnalysis", true);
        write(put(path), head, parameters).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.managerId").exists());
        parameters.put("managerId", responsible.getId());
        var assigned = read(write(put(path), head, parameters).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_ANALYSIS")));
        mvc.perform(get(path).with(manager)).andExpect(status().isNotFound());
        mvc.perform(get(path).with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAnalyze").value(true)).andExpect(jsonPath("$.canEditItems").value(false));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.get("id").asLong()));

        var statistics = get("/api/analytics/statistics").param("periodStart", "2026-01-01").param("periodEnd", "2026-03-31");
        mvc.perform(statistics.with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TRAINING")).andExpect(jsonPath("$.regions.length()").value(2));
        var weights = Map.of("salary", 100, "retail", 0, "population", 0);
        var preview = path + "/analysis/preview";
        write(post(preview), head, Map.of("version", version(assigned), "weights", weights)).andExpect(status().isForbidden());
        write(post(preview), analyst, Map.of("version", version(assigned), "weights", Map.of("salary", 99, "retail", 0, "population", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.weights").exists());
        write(post(preview), analyst, Map.of("version", version(assigned), "weights", weights)).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.ranking.length()").value(2))
                .andExpect(jsonPath("$.priceAdjustmentPercent").value(5.0));

        var conclusion = Map.of("version", version(assigned), "weights", weights, "recommendationText", "Увеличить план по розеткам");
        write(put(path + "/analysis"), manager, conclusion).andExpect(status().isForbidden());
        write(put(path + "/analysis"), analyst, Map.of("version", version(assigned), "weights", weights, "recommendationText", " "))
                .andExpect(status().isBadRequest());
        assignment.setEndDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        assignments.saveAndFlush(assignment);
        write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANAGER_UNAVAILABLE"));
        assignment.setEndDate(null);
        assignments.saveAndFlush(assignment);

        var dispatched = read(write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sentToManagerAt").isNotEmpty()));
        write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/analytics/recommendations/" + draft.get("id").asLong()).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.weights.salary").value(100))
                .andExpect(jsonPath("$.recommendation.publication.source").value("TRAINING"));
        mvc.perform(get(path).with(otherManager)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.get("id").asLong()));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true))
                .andExpect(jsonPath("$.previous.from").value("2025-10-01"))
                .andExpect(jsonPath("$.previous.revenue").value("12.30"))
                .andExpect(jsonPath("$.recommendation.publication.text").value("Увеличить план по розеткам"))
                .andExpect(jsonPath("$.offers[?(@.productId == " + product.getId() + ")].unitPrice", hasItem("4.21")))
                .andExpect(jsonPath("$.offers[?(@.productId == " + product.getId() + ")].suggestedUnitPrice", hasItem("4.42")))
                .andExpect(jsonPath("$.offers[?(@.productId == " + currentPrice.getId() + ")].unitPrice", hasItem("9.10")));

        var line = Map.of("productId", product.getId(), "plannedQuantity", "2.5", "plannedUnitPrice", "4.21");
        var saved = read(write(put(path + "/items"), manager,
                Map.of("version", version(dispatched), "items", List.of(line))).andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedRevenue").value("10.53")));
        write(put(path + "/items"), manager, Map.of("version", version(dispatched), "items", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        write(put(path + "/items"), manager, Map.of("version", version(saved), "items", List.of(line, line)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors['items[1].productId']").exists());
        var invalidLine = Map.of("productId", product.getId(), "plannedUnitPrice", "-1");
        var needsCorrection = read(write(put(path + "/items"), manager,
                Map.of("version", version(saved), "items", List.of(invalidLine), "submit", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CORRECTION"))
                .andExpect(jsonPath("$.validationNote", containsString("Укажите количество")))
                .andExpect(jsonPath("$.validationNote", containsString("Цена должна быть неотрицательной"))));
        mvc.perform(get(PATH).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NEEDS_CORRECTION"));
        mvc.perform(get(path).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.validationNote", containsString("Укажите количество")))
                .andExpect(jsonPath("$.items[0].plannedAmount").value("10.53"));
        var corrected = read(write(put(path + "/items"), manager,
                Map.of("version", version(needsCorrection), "items", List.of(line))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.validationNote").doesNotExist()));
        var firstSubmission = read(write(put(path + "/items"), manager,
                Map.of("version", version(corrected), "items", List.of(line), "submit", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canWithdraw").value(true)).andExpect(jsonPath("$.canEditItems").value(false));
        mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(firstSubmission))).with(otherManager).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(firstSubmission))).with(head).with(csrf()))
                .andExpect(status().isForbidden());
        var withdrawn = read(mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(firstSubmission))).with(manager).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.plannedRevenue").value("10.53")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true)).andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.items[0].plannedUnitPrice").value("4.21"))
                .andExpect(jsonPath("$.recommendation.publication.text").value("Увеличить план по розеткам"));
        mvc.perform(get(PATH).param("attention", "true").with(head)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        write(post(path + "/review"), head, Map.of("version", version(firstSubmission), "decision", "APPROVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        var submitted = read(write(put(path + "/items"), manager,
                Map.of("version", version(withdrawn), "items", List.of(line), "submit", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW")));

        var review = Map.of("version", version(submitted), "decision", "RETURN", "reviewComment", "Уточните количество");
        write(post(path + "/review"), manager, review).andExpect(status().isForbidden());
        write(post(path + "/review"), head, Map.of("version", version(submitted), "decision", "RETURN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reviewComment").exists());
        var returned = read(write(post(path + "/review"), head, review).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVISION")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewComment").value("Уточните количество"));
        var revisedLine = Map.of("productId", product.getId(), "plannedQuantity", "3", "plannedUnitPrice", "4.21");
        var revised = read(write(put(path + "/items"), manager,
                Map.of("version", version(returned), "items", List.of(revisedLine), "submit", true)).andExpect(status().isOk()));
        var approved = read(write(post(path + "/review"), head, Map.of("version", version(revised), "decision", "APPROVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(false)).andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.actual.revenue").value("10.25"))
                .andExpect(jsonPath("$.items[0].plannedAmount").value("12.63"));
        mvc.perform(get(PATH + "/results").with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actualRevenue").value("10.25"));
        mvc.perform(get(PATH + "/results").with(otherManager)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        write(put(path + "/items"), manager, Map.of("version", version(approved), "items", List.of(line)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(approved))).with(manager).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        parameters.put("version", version(approved));
        write(put(path), head, parameters).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        mvc.perform(delete(path).param("version", Long.toString(version(approved))).with(head).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        mvc.perform(post(path + "/cancel").param("version", Long.toString(version(approved))).with(head).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));

        assignment.setEndDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        assignments.saveAndFlush(assignment);
        mvc.perform(get(PATH).with(manager)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path).with(manager)).andExpect(status().isForbidden());
    }

    @Test void restrictsPlanningRolesAndRejectsDuplicateRegionsAndPeriods() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var parameters = parameters(region());
        mvc.perform(get(PATH).with(as(account(RoleCode.ADMIN)))).andExpect(status().isForbidden());
        write(post(PATH), as(account(RoleCode.MANAGER)), parameters).andExpect(status().isForbidden());
        var created = read(write(post(PATH), head, parameters).andExpect(status().isCreated()));
        mvc.perform(get(PATH + "/" + created.get("id").asLong()).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.revenue").value("0.00"));
        write(post(PATH), head, parameters).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_EXISTS"));
        parameters.put("periodEnd", "2026-02-28");
        write(post(PATH), head, parameters).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.periodEnd").exists());
    }

    @Test void managerCanContinuePlanDispatchedBeforeAnalysisWasAdded() throws Exception {
        var region = region();
        var manager = account(RoleCode.MANAGER);
        assign(manager, region);
        var draft = read(write(post(PATH), as(account(RoleCode.SALES_HEAD)), parameters(region)).andExpect(status().isCreated()));
        var plan = plans.findById(draft.get("id").asLong()).orElseThrow();
        plan.setAssignedManager(manager);
        plan.setSentToManagerAt(Instant.now());
        plans.saveAndFlush(plan);
        mvc.perform(get(PATH + "/" + plan.getId()).with(as(manager))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true)).andExpect(jsonPath("$.recommendation").doesNotExist());
    }

    @Test void headCanCancelAndDeleteAPlanWithItemsAndReuseItsPeriod() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var region = region();
        var managerAccount = account(RoleCode.MANAGER);
        assign(managerAccount, region);
        var manager = as(managerAccount);
        var parameters = parameters(region);
        var original = read(write(post(PATH), head, parameters).andExpect(status().isCreated()));
        var id = original.get("id").asLong();
        var path = PATH + "/" + id;
        var plan = plans.findById(id).orElseThrow();
        plan.setAssignedManager(managerAccount);
        plan.setSentToManagerAt(Instant.now());
        plan.setStatus(PlanStatus.IN_PROGRESS);
        var assignedVersion = plans.saveAndFlush(plan).getVersion();

        var empty = read(write(put(path + "/items"), manager, Map.of("version", assignedVersion, "items", List.of(), "submit", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_CORRECTION")));
        var line = Map.of("productId", product().getId(), "plannedQuantity", "1", "plannedUnitPrice", "4.50");
        var withItems = read(write(put(path + "/items"), manager, Map.of("version", version(empty), "items", List.of(line)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS")));

        mvc.perform(delete(path).param("version", Long.toString(version(withItems))).with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/cancel").param("version", Long.toString(version(empty))).with(head).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        var cancelled = read(mvc.perform(post(path + "/cancel").param("version", Long.toString(version(withItems))).with(head).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")));
        mvc.perform(get(path).with(head)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].plannedAmount").value("4.50"));
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        var replacement = read(write(post(PATH), head, parameters).andExpect(status().isCreated()));

        mvc.perform(delete(path).param("version", Long.toString(version(cancelled))).with(head).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path).with(head)).andExpect(status().isNotFound());
        assertThat(planItems.existsByRegionalPlanId(id)).isFalse();
        mvc.perform(get(PATH + "/" + replacement.get("id").asLong()).with(head)).andExpect(status().isOk());
    }

    private static Map<String, Object> parameters(Region region) {
        return new HashMap<>(Map.of("regionId", region.getId(), "periodStart", "2026-01-01", "periodEnd", "2026-03-31",
                "targetRevenue", "600", "budgetLimit", "500.25", "maxDiscountPercent", "4.25", "planningComment", "План на квартал"));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, RequestPostProcessor actor, Map<String, ?> body) throws Exception {
        return mvc.perform(request.with(actor).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private static long version(JsonNode plan) {
        return plan.get("version").asLong();
    }

    private AppUser account(RoleCode code) {
        var role = roles.findByCode(code).orElseGet(() -> {
            var entry = new Role();
            entry.setCode(code);
            entry.setName(code.name());
            return roles.saveAndFlush(entry);
        });
        var user = new AppUser();
        user.setLogin("plan-" + UUID.randomUUID());
        user.setEmail(user.getLogin() + "@example.test");
        user.setFullName("Сотрудник сбыта");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private static RequestPostProcessor as(AppUser user) {
        var principal = new AppPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private Region region() {
        return region("P-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Region region(String code) {
        var region = new Region();
        region.setCode(code);
        region.setName("Область " + region.getCode());
        return regions.saveAndFlush(region);
    }

    private RegionAssignment assign(AppUser user, Region region) {
        var assignment = new RegionAssignment();
        assignment.setRegion(region);
        assignment.setManager(user);
        assignment.setStartDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(2));
        return assignments.saveAndFlush(assignment);
    }

    private Product product() {
        var product = new Product();
        product.setSku("PLAN-" + UUID.randomUUID());
        product.setName("Розетка");
        product.setCurrentPrice(new BigDecimal("9.10"));
        return products.saveAndFlush(product);
    }

    private void shipment(AppUser user, Region region, Product product, String date, String quantity, String price) {
        var row = new Shipment();
        row.setRegion(region);
        row.setRecordedByUser(user);
        row.setProduct(product);
        row.setShipmentDate(LocalDate.parse(date));
        row.setQuantity(new BigDecimal(quantity));
        row.setUnitPrice(new BigDecimal(price));
        shipments.saveAndFlush(row);
    }
}
