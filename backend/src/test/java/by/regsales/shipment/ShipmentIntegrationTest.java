package by.regsales.shipment;

import by.regsales.domain.AppUser;
import by.regsales.domain.Product;
import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.repository.ShipmentRepository;
import by.regsales.security.AppPrincipal;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipmentIntegrationTest {
    private static final String PATH = "/api/shipments";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ShipmentRepository shipments;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RegionRepository regions;
    @Autowired RegionAssignmentRepository assignments;
    @Autowired ProductRepository products;

    @AfterEach void cleanShipments() {
        shipments.deleteAllInBatch();
    }

    @Test void combinesSearchFiltersAndSortingBeforePaginationWithinManagerRegions() throws Exception {
        var minsk = region("Минск");
        var brest = region("Брест");
        var manager = as(manager(minsk));
        var otherManager = as(manager(brest));
        var analyst = as(account(RoleCode.ANALYST));
        var product = product("Розетка");
        var another = product("Выключатель");

        register(manager, minsk, product, "2024-03-03", "DOC-M-03", "2.5", "12.35")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value("30.88"));
        register(manager, minsk, product, "2024-01-03", "DOC-M-01", "15", "2")
                .andExpect(status().isCreated());
        register(manager, minsk, another, "2024-03-03", "SWITCH-M", "10", "5")
                .andExpect(status().isCreated());
        register(otherManager, brest, product, "2024-02-03", "DOC-B-02", "100", "4.06")
                .andExpect(status().isCreated());
        register(manager, minsk, product, "2024-03-03", "doc-m-03", "3", "12.35")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.referenceNumber").exists());

        mvc.perform(get(PATH).with(manager).param("search", "DOC мИнСк").param("productId", product.getId().toString())
                        .param("sort", "TOTAL").param("direction", "ASC").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.shipments[0].referenceNumber").value("DOC-M-03"));
        mvc.perform(get(PATH).with(manager).param("search", product.getSku()).param("from", "2024-03-03").param("to", "2024-03-03")
                        .param("regionId", minsk.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.shipments[0].referenceNumber").value("DOC-M-03"));
        mvc.perform(get(PATH).with(manager).param("regionId", brest.getId().toString())).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(analyst).param("sort", "TOTAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.shipments[0].totalAmount").value("406.00"));
        mvc.perform(get(PATH + "/options").with(analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRecord").value(false));
        mvc.perform(get(PATH).with(manager).param("from", "2024-03-01").param("to", "2024-02-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
    }

    @Test void importsOnlyAssignedRegionsAndRecognizesIdenticalDocumentRows() throws Exception {
        var assigned = region("Минск");
        var foreign = region("Брест");
        var manager = as(manager(assigned));
        var product = product("Розетка");
        var file = workbook(new Object[][]{
                {"OTG-2024-0001-01", "2024-01-02", assigned.getCode(), product.getSku(), 100, "4,06", "406,00"},
                {"OTG-2024-0002-01", "2024-01-02", foreign.getCode(), "FOREIGN", 10, 2, 20},
        });
        mvc.perform(multipart(PATH + "/import").file(file).with(manager).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.skipped").value(1));
        mvc.perform(multipart(PATH + "/import").file(file).with(manager).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(1)).andExpect(jsonPath("$.created").value(0));
        mvc.perform(get(PATH).with(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.shipments[0].totalAmount").value("406.00"));

        var wrongAmount = workbook(new Object[][]{
                {"OTG-2024-0100-01", "2024-01-02", assigned.getCode(), product.getSku(), 100, 4.06, 406},
                {"OTG-2024-0101-01", "2024-01-02", assigned.getCode(), product.getSku(), 100, 4.06, 421},
        });
        mvc.perform(multipart(PATH + "/import").file(wrongAmount).with(manager).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_SHIPMENT_ROWS"));
        var conflict = workbook(new Object[][]{
                {"OTG-2024-0100-01", "2024-01-02", assigned.getCode(), product.getSku(), 100, 4.06, 406},
                {"OTG-2024-0001-01", "2024-01-02", assigned.getCode(), product.getSku(), 100, 4.21, 421},
        });
        mvc.perform(multipart(PATH + "/import").file(conflict).with(manager).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors['Строка 3']").exists());
        assertThat(shipments.count()).isOne();
    }

    @Test void rejectsUnauthorizedWritesAndAmountsThatExceedDatabasePrecision() throws Exception {
        var assigned = region("Минск");
        var foreign = region("Брест");
        var manager = as(manager(assigned));
        var product = product("Розетка");
        var unassigned = as(account(RoleCode.MANAGER));
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(as(account(RoleCode.ADMIN)))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(unassigned)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        register(as(account(RoleCode.ANALYST)), assigned, product, "2024-03-01", "DOC", "1", "4")
                .andExpect(status().isForbidden());
        register(unassigned, assigned, product, "2024-03-01", "DOC", "1", "4")
                .andExpect(status().isForbidden());
        register(manager, foreign, product, "2024-03-01", "DOC", "1", "4")
                .andExpect(status().isForbidden());
        register(manager, assigned, product, "2024-03-01", "DOC", "99999999999.999", "999999999999.99")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.unitPrice").exists());
        assertThat(shipments.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions register(RequestPostProcessor actor, Region region,
            Product product, String date, String reference, String quantity, String price) throws Exception {
        var input = Map.of("shipmentDate", date, "regionId", region.getId(), "productId", product.getId(),
                "referenceNumber", reference, "quantity", quantity, "unitPrice", price);
        return mvc.perform(post(PATH).with(actor).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(input)));
    }

    private Region region(String name) {
        var region = new Region();
        region.setCode("T-" + UUID.randomUUID().toString().substring(0, 8));
        region.setName(name + " " + region.getCode());
        return regions.saveAndFlush(region);
    }

    private Product product(String name) {
        var product = new Product();
        product.setSku("SHIP-" + UUID.randomUUID());
        product.setName(name);
        product.setCurrentPrice(new BigDecimal("9.10"));
        return products.saveAndFlush(product);
    }

    private AppUser manager(Region region) {
        var manager = account(RoleCode.MANAGER);
        var assignment = new RegionAssignment();
        assignment.setRegion(region);
        assignment.setManager(manager);
        assignment.setStartDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        assignments.saveAndFlush(assignment);
        return manager;
    }

    private AppUser account(RoleCode code) {
        var role = roles.findByCode(code).orElseGet(() -> {
            var entry = new Role();
            entry.setCode(code);
            entry.setName(code.name());
            return roles.saveAndFlush(entry);
        });
        var user = new AppUser();
        user.setLogin("ship-" + UUID.randomUUID());
        user.setEmail(user.getLogin() + "@example.test");
        user.setFullName("Сотрудник");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private RequestPostProcessor as(AppUser user) {
        var principal = new AppPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private MockMultipartFile workbook(Object[][] values) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Отгрузки");
            String[] headers = {"Ключ строки", "Дата отгрузки", "Код региона ISO", "Артикул", "Количество",
                    "Цена ед. без НДС, BYN", "Сумма без НДС, BYN"};
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
            for (int i = 0; i < values.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int column = 0; column < values[i].length; column++) {
                    var cell = row.createCell(column);
                    if (values[i][column] instanceof Number number) cell.setCellValue(number.doubleValue());
                    else cell.setCellValue((String) values[i][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "shipments.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
