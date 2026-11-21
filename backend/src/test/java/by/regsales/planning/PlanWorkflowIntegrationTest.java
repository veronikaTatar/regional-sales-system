package by.regsales.planning;

import by.regsales.domain.AppUser;
import by.regsales.domain.PlanItem;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.domain.Shipment;
import by.regsales.domain.StatisticalIndicator;
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
import by.regsales.repository.RegionalStatisticRepository;
import by.regsales.repository.StatisticalIndicatorRepository;
import by.regsales.repository.StatisticsImportRepository;
import by.regsales.security.AppPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
    @Autowired RegionalStatisticRepository regionalStatistics;
    @Autowired StatisticsImportRepository statisticsImports;
    @Autowired StatisticalIndicatorRepository indicators;

    @AfterEach void cleanPlanData() {
        planItems.deleteAllInBatch();
        plans.deleteAllInBatch();
        recommendations.deleteAllInBatch();
        calculations.deleteAllInBatch();
        prices.deleteAllInBatch();
        shipments.deleteAllInBatch();
        regionalStatistics.deleteAllInBatch();
        statisticsImports.deleteAllInBatch();
    }

    @Test void managerCompletesMissingSalesAndBuildsTheProjectAfterAnalysis() throws Exception {
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
                .andExpect(jsonPath("$.status").value("SALES_REVISION")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmitSales").value(true)).andExpect(jsonPath("$.canEditItems").value(false));
        mvc.perform(get(path).with(analyst)).andExpect(status().isNotFound());
        submitSales(path, assigned, otherManager).andExpect(status().isNotFound());
        write(put(path + "/items"), manager, Map.of("version", version(assigned), "items", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        write(post(path + "/review"), head, Map.of("version", version(assigned), "decision", "APPROVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        submitSales(path, assigned, manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SALES_REVISION"))
                .andExpect(jsonPath("$.validationNote", containsString("отгрузок нет")));
        write(post("/api/shipments"), manager, Map.of("regionId", region.getId(), "productId", product.getId(),
                "shipmentDate", "2026-04-01", "quantity", 10, "unitPrice", "4.10", "referenceNumber", "PLAN-OTHER-PERIOD"))
                .andExpect(status().isCreated());
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("SALES_REVISION"));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        write(post("/api/shipments"), manager, Map.of("regionId", region.getId(), "productId", product.getId(),
                "shipmentDate", "2025-12-31", "quantity", 3, "unitPrice", "4.10"))
                .andExpect(status().isCreated());
        var recorded = read(mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("AWAITING_ANALYSIS"))
                .andExpect(jsonPath("$.plan.validationNote").doesNotExist())
                .andExpect(jsonPath("$.canSubmitSales").value(false))).get("plan");
        write(put(path + "/items"), manager, Map.of("version", version(recorded), "items", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        mvc.perform(get(path).with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAnalyze").value(true)).andExpect(jsonPath("$.canEditItems").value(false))
                .andExpect(jsonPath("$.salesSubmission.shipmentCount").value(1))
                .andExpect(jsonPath("$.salesSubmission.period.revenue").value("12.30"));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.get("id").asLong()));

        var statistics = get("/api/analytics/statistics").param("periodStart", "2026-01-01").param("periodEnd", "2026-03-31");
        mvc.perform(statistics.with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TRAINING")).andExpect(jsonPath("$.regions[*].regionCode", hasItem("BY-HM")));
        var weights = Map.of("salary", 100, "retail", 0, "population", 0);
        var preview = path + "/analysis/preview";
        write(post(preview), head, Map.of("version", version(recorded), "weights", weights)).andExpect(status().isForbidden());
        write(post(preview), analyst, Map.of("version", version(recorded), "weights", Map.of("salary", 99, "retail", 0, "population", 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.weights").exists());
        write(post(preview), analyst, Map.of("version", version(recorded), "weights", weights)).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.ranking[*].indicators.regionCode", hasItem("BY-HM")))
                .andExpect(jsonPath("$.priceAdjustmentPercent").value(5.0));

        var conclusion = Map.of("version", version(recorded), "weights", weights, "recommendationText", "Увеличить план по розеткам");
        write(put(path + "/analysis"), manager, conclusion).andExpect(status().isForbidden());
        write(put(path + "/analysis"), analyst, Map.of("version", version(recorded), "weights", weights, "recommendationText", " "))
                .andExpect(status().isBadRequest());
        assignment.setEndDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        assignments.saveAndFlush(assignment);
        write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANAGER_UNAVAILABLE"));
        assignment.setEndDate(null);
        assignments.saveAndFlush(assignment);

        var recommended = read(write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sentToManagerAt").isNotEmpty()));
        write(put(path + "/analysis"), analyst, conclusion).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/analytics/recommendations/" + draft.get("id").asLong()).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.weights.salary").value(100))
                .andExpect(jsonPath("$.recommendation.publication.source").value("TRAINING"));
        mvc.perform(get(path).with(otherManager)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).param("attention", "true").with(head)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.get("id").asLong()));
        mvc.perform(get(path).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(false));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true))
                .andExpect(jsonPath("$.previous.from").value("2025-10-01"))
                .andExpect(jsonPath("$.previous.revenue").value("12.30"))
                .andExpect(jsonPath("$.recommendation.publication.text").value("Увеличить план по розеткам"))
                .andExpect(jsonPath("$.offers[?(@.productId == " + product.getId() + ")].unitPrice", hasItem("4.21")))
                .andExpect(jsonPath("$.offers[?(@.productId == " + product.getId() + ")].suggestedUnitPrice", hasItem("4.42")))
                .andExpect(jsonPath("$.offers[?(@.productId == " + currentPrice.getId() + ")].unitPrice", hasItem("9.10")));

        var line = Map.of("productId", product.getId(), "plannedQuantity", "2.5", "plannedUnitPrice", "4.21");
        write(put(path + "/items"), head, Map.of("version", version(recommended), "items", List.of(line)))
                .andExpect(status().isForbidden());
        write(put(path + "/items"), otherManager, Map.of("version", version(recommended), "items", List.of(line)))
                .andExpect(status().isNotFound());
        var saved = read(write(put(path + "/items"), manager,
                Map.of("version", version(recommended), "items", List.of(line))).andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedRevenue").value("10.53")));
        write(put(path + "/items"), manager, Map.of("version", version(recommended), "items", List.of()))
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
                .andExpect(jsonPath("$.canWithdraw").value(true)).andExpect(jsonPath("$.canEditItems").value(false))
                .andExpect(jsonPath("$.planningCheck", containsString("Выручка ниже цели")))
                .andExpect(jsonPath("$.planningCheck", containsString("Предельная скидка соблюдена")))
                .andExpect(jsonPath("$.planningCheck", containsString("себестоимости")));
        mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(firstSubmission))).with(head).with(csrf()))
                .andExpect(status().isForbidden());
        var withdrawn = read(mvc.perform(post(path + "/withdraw").param("version", Long.toString(version(firstSubmission))).with(manager).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.plannedRevenue").value("10.53")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true)).andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.items[0].plannedUnitPrice").value("4.21"))
                .andExpect(jsonPath("$.recommendation.publication.text").value("Увеличить план по розеткам"));
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
                .andExpect(jsonPath("$.reviewComment").value("Уточните количество"))
                .andExpect(jsonPath("$.canEditItems").value(true))
                .andExpect(jsonPath("$.canSubmitSales").value(false));
        mvc.perform(get(PATH).param("attention", "true").with(analyst)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        var revisedLine = Map.of("productId", product.getId(), "plannedQuantity", "3", "plannedUnitPrice", "4.21");
        var revised = read(write(put(path + "/items"), manager,
                Map.of("version", version(returned), "items", List.of(revisedLine), "submit", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW")));
        var analysisReturned = read(write(post(path + "/review"), head, Map.of("version", version(revised),
                "decision", "RETURN_ANALYSIS", "reviewComment", "Уточните расчёт")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_ANALYSIS")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.canEditItems").value(false));
        var freshRecommendation = read(write(put(path + "/analysis"), analyst, Map.of("version", version(analysisReturned),
                "weights", weights, "recommendationText", "Рекомендация обновлена")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.plannedRevenue").value("12.63")));
        var ready = read(write(put(path + "/items"), manager,
                Map.of("version", version(freshRecommendation), "items", List.of(revisedLine), "submit", true)).andExpect(status().isOk()));
        var approved = read(write(post(path + "/review"), head, Map.of("version", version(ready), "decision", "APPROVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(false)).andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.actual.revenue").value("10.25"))
                .andExpect(jsonPath("$.items[0].plannedAmount").value("12.63"));
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
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

    @Test void refusalEndsPlanningAndRequiresAReason() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var analyst = as(account(RoleCode.ANALYST));
        var region = region("BY-HM");
        var responsible = account(RoleCode.MANAGER);
        assign(responsible, region);
        var manager = as(responsible);
        var product = product();
        shipment(responsible, region, product, "2025-12-31", "3", "4.10");
        var parameters = parameters(region);
        parameters.put("managerId", responsible.getId());
        parameters.put("requestAnalysis", true);
        var recorded = read(write(post(PATH), head, parameters).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_ANALYSIS")));
        var path = PATH + "/" + recorded.get("id").asLong();
        mvc.perform(get(path).with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAnalyze").value(true)).andExpect(jsonPath("$.salesSubmission.shipmentCount").value(1));
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        var recommended = read(write(put(path + "/analysis"), analyst, Map.of("version", version(recorded), "weights",
                Map.of("salary", 40, "retail", 40, "population", 20), "recommendationText", "Оценить возможности производства"))
                .andExpect(status().isOk()));
        var line = Map.of("productId", product.getId(), "plannedQuantity", "50", "plannedUnitPrice", "8.00");
        var submitted = read(write(put(path + "/items"), manager, Map.of("version", version(recommended), "items", List.of(line), "submit", true))
                .andExpect(status().isOk()));
        mvc.perform(get(path).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.planningCheck", containsString("превышением предельной скидки — 1")));
        write(post(path + "/review"), head, Map.of("version", version(submitted), "decision", "REJECT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reviewComment").exists());
        var rejected = read(write(post(path + "/review"), head, Map.of("version", version(submitted), "decision", "REJECT",
                "reviewComment", "Недостаточно производственных ресурсов")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED")));
        submitSales(path, rejected, manager).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        mvc.perform(get(path).with(head)).andExpect(status().isOk()).andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.reviewComment").value("Недостаточно производственных ресурсов"));
        mvc.perform(get(PATH + "/results").with(head)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void headCanCancelAndDeleteAPlanWithItemsAndReuseItsPeriod() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var region = region();
        var managerAccount = account(RoleCode.MANAGER);
        assign(managerAccount, region);
        var manager = as(managerAccount);
        var parameters = parameters(region);
        parameters.put("managerId", managerAccount.getId());
        parameters.put("requestAnalysis", true);
        var original = read(write(post(PATH), head, parameters).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SALES_REVISION")));
        var id = original.get("id").asLong();
        var path = PATH + "/" + id;
        var item = new PlanItem();
        item.setRegionalPlan(plans.findById(id).orElseThrow());
        item.setProduct(product());
        item.setPlannedQuantity(BigDecimal.ONE);
        item.setPlannedUnitPrice(new BigDecimal("4.50"));
        planItems.saveAndFlush(item);
        var incomplete = read(submitSales(path, original, manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SALES_REVISION")));

        mvc.perform(delete(path).param("version", Long.toString(version(incomplete))).with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/cancel").param("version", Long.toString(version(original))).with(head).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        var cancelled = read(mvc.perform(post(path + "/cancel").param("version", Long.toString(version(incomplete))).with(head).with(csrf()))
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

    @Test void importsTheLatestCompleteBelstatYearAndUsesItInPublishedAnalysis() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var analyst = as(account(RoleCode.ANALYST));
        var responsible = account(RoleCode.MANAGER);
        var manager = as(responsible);
        var region = region("BY-VI");
        assign(responsible, region);
        for (String code : List.of("BY-BR", "BY-HO", "BY-HR", "BY-MI", "BY-MA", "BY-HM")) region(code);
        for (String code : List.of("ANNUAL_AVERAGE_SALARY", "ANNUAL_RETAIL_TURNOVER", "POPULATION")) {
            var indicator = new StatisticalIndicator();
            indicator.setCode(code);
            indicator.setName(code);
            indicator.setUnit("тест");
            indicator.setPeriodicity("YEAR");
            indicators.saveAndFlush(indicator);
        }
        int year = LocalDate.now(ZoneId.of("Europe/Minsk")).getYear() - 1;
        var salary = annualWorkbook("salary", year, false, "2 540,5");
        var turnover = annualWorkbook("turnover", year, true, "6000");
        var population = annualWorkbook("population", year, false, "1000");
        String endpoint = "/api/analytics/statistics";
        var importRequest = multipart(endpoint + "/import").file(salary).file(turnover).file(population)
                .param("turnoverUnit", "MILLION_BYN").param("populationUnit", "THOUSAND_PEOPLE");
        mvc.perform(multipart(endpoint + "/import").file(salary).file(turnover).file(population)
                .param("turnoverUnit", "MILLION_BYN").param("populationUnit", "THOUSAND_PEOPLE").with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(importRequest.with(analyst).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year)).andExpect(jsonPath("$.regions").value(7));
        mvc.perform(multipart(endpoint + "/import").file(salary).file(turnover).file(population)
                .param("turnoverUnit", "MILLION_BYN").param("populationUnit", "THOUSAND_PEOPLE").with(analyst).with(csrf()))
                .andExpect(status().isOk());
        assertThat(regionalStatistics.count()).isEqualTo(21);
        assertThat(statisticsImports.count()).isEqualTo(2);
        mvc.perform(get(endpoint + "/latest").with(analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BELSTAT"))
                .andExpect(jsonPath("$.regions[0].averageSalary").value(2540.5))
                .andExpect(jsonPath("$.regions[0].retailPerCapita").value(6000))
                .andExpect(jsonPath("$.regions[0].population").value(1000000));

        shipment(responsible, region, product(), year + "-12-31", "2", "4.10");
        var parameters = parameters(region);
        parameters.put("periodStart", (year + 1) + "-01-01");
        parameters.put("periodEnd", (year + 1) + "-01-31");
        parameters.put("managerId", responsible.getId());
        parameters.put("requestAnalysis", true);
        var plan = read(write(post(PATH), head, parameters).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_ANALYSIS")));
        var path = PATH + "/" + plan.get("id").asLong();
        var weights = Map.of("salary", 40, "retail", 40, "population", 20);
        write(post(path + "/analysis/preview"), analyst, Map.of("version", version(plan), "weights", weights))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calculation.source").value("BELSTAT"))
                .andExpect(jsonPath("$.calculation.periodStart").value(year + "-01-01"));
        write(put(path + "/analysis"), analyst, Map.of("version", version(plan), "weights", weights,
                "recommendationText", "Рекомендация по годовым данным")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/recommendations").with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("BELSTAT"));
        mvc.perform(get("/api/analytics/recommendations/" + plan.get("id").asLong()).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.source").value("BELSTAT"));
    }

    private static MockMultipartFile annualWorkbook(String field, int year, boolean transposed, String value) throws Exception {
        var names = List.of("Брестская область", "Витебская", "Гомельская", "Гродненская область",
                "Минская область", "Могилёвская", "г.Минск");
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Годовые показатели");
            sheet.createRow(0).createCell(0).setCellValue("Тестовая таблица");
            var header = sheet.createRow(1);
            if (transposed) {
                var values = sheet.createRow(2);
                values.createCell(0).setCellValue(year);
                for (int index = 0; index < names.size(); index++) {
                    header.createCell(index + 1).setCellValue(names.get(index));
                    values.createCell(index + 1).setCellValue(value);
                }
            } else {
                header.createCell(1).setCellValue((year - 1) + " г.");
                header.createCell(2).setCellValue(year);
                header.createCell(3).setCellValue(year + 1);
                for (int index = 0; index < names.size(); index++) {
                    var row = sheet.createRow(index + 2);
                    row.createCell(0).setCellValue(names.get(index));
                    row.createCell(1).setCellValue("999");
                    row.createCell(2).setCellValue(value);
                    if (index == 0) row.createCell(3).setCellValue("123");
                }
            }
            workbook.write(output);
            return new MockMultipartFile(field, field + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private static Map<String, Object> parameters(Region region) {
        return new HashMap<>(Map.of("regionId", region.getId(), "periodStart", "2026-01-01", "periodEnd", "2026-03-31",
                "targetRevenue", "600", "budgetLimit", "500.25", "maxDiscountPercent", "4.25", "planningComment", "План на квартал"));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, RequestPostProcessor actor, Map<String, ?> body) throws Exception {
        return mvc.perform(request.with(actor).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    private ResultActions submitSales(String path, JsonNode plan, RequestPostProcessor manager) throws Exception {
        return mvc.perform(post(path + "/sales").param("version", Long.toString(version(plan))).with(manager).with(csrf()));
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
        return regions.findAllByActiveTrueOrderByNameAsc().stream().filter(region -> region.getCode().equals(code)).findFirst().orElseGet(() -> {
            var region = new Region();
            region.setCode(code);
            region.setName("Область " + region.getCode());
            return regions.saveAndFlush(region);
        });
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
