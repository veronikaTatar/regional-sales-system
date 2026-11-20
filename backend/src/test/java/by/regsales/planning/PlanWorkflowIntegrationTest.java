package by.regsales.planning;

import by.regsales.domain.AppUser;
import by.regsales.domain.Product;
import by.regsales.domain.ProductPrice;
import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.domain.Shipment;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.PlanItemRepository;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @AfterEach void cleanPlanData() {
        planItems.deleteAllInBatch();
        plans.deleteAllInBatch();
        prices.deleteAllInBatch();
        shipments.deleteAllInBatch();
    }

    @Test void assignedManagerCompletesPlanAndHeadReturnsThenApprovesIt() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var region = region();
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

        parameters.put("version", version(draft));
        parameters.put("sendToManager", true);
        write(put(path), head, parameters).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.managerId").exists());
        parameters.put("managerId", responsible.getId());
        var dispatched = read(write(put(path), head, parameters).andExpect(status().isOk())
                .andExpect(jsonPath("$.sentToManagerAt").isNotEmpty()));
        mvc.perform(get(path).with(otherManager)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).param("attention", "true").with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.get("id").asLong()));
        mvc.perform(get(path).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEditItems").value(true))
                .andExpect(jsonPath("$.previous.from").value("2025-10-01"))
                .andExpect(jsonPath("$.previous.revenue").value("12.30"))
                .andExpect(jsonPath("$.offers[?(@.productId == " + product.getId() + ")].unitPrice", hasItem("4.21")))
                .andExpect(jsonPath("$.offers[?(@.productId == " + currentPrice.getId() + ")].unitPrice", hasItem("9.10")));

        var line = Map.of("productId", product.getId(), "plannedQuantity", "2.5", "plannedUnitPrice", "4.21");
        var saved = read(write(put(path + "/items"), manager,
                Map.of("version", version(dispatched), "items", List.of(line))).andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedRevenue").value("10.53")));
        write(put(path + "/items"), manager, Map.of("version", version(dispatched), "items", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_CHANGED"));
        write(put(path + "/items"), manager, Map.of("version", version(saved), "items", List.of(line, line)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors['items[1].productId']").exists());
        var submitted = read(write(put(path + "/items"), manager,
                Map.of("version", version(saved), "items", List.of(line), "submit", true)).andExpect(status().isOk())
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
        write(put(path + "/items"), manager, Map.of("version", version(approved), "items", List.of(line)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));
        parameters.put("version", version(approved));
        write(put(path), head, parameters).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_STAGE"));

        assignment.setEndDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        assignments.saveAndFlush(assignment);
        mvc.perform(get(PATH).with(manager)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path).with(manager)).andExpect(status().isForbidden());
    }

    @Test void restrictsPlanningRolesAndRejectsDuplicateRegionsAndPeriods() throws Exception {
        var head = as(account(RoleCode.SALES_HEAD));
        var parameters = parameters(region());
        mvc.perform(get(PATH).with(as(account(RoleCode.ANALYST)))).andExpect(status().isForbidden());
        write(post(PATH), as(account(RoleCode.MANAGER)), parameters).andExpect(status().isForbidden());
        var created = read(write(post(PATH), head, parameters).andExpect(status().isCreated()));
        mvc.perform(get(PATH + "/" + created.get("id").asLong()).with(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.revenue").value("0.00"));
        write(post(PATH), head, parameters).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_EXISTS"));
        parameters.put("periodEnd", "2026-02-28");
        write(post(PATH), head, parameters).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.periodEnd").exists());
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
        var region = new Region();
        region.setCode("P-" + UUID.randomUUID().toString().substring(0, 8));
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
