package by.regsales.catalog;

import by.regsales.domain.AppUser;
import by.regsales.domain.Region;
import by.regsales.domain.RegionAssignment;
import by.regsales.domain.Role;
import by.regsales.domain.RoleCode;
import by.regsales.repository.AppUserRepository;
import by.regsales.repository.ProductPriceRepository;
import by.regsales.repository.ProductRepository;
import by.regsales.repository.RegionAssignmentRepository;
import by.regsales.repository.RegionRepository;
import by.regsales.repository.RoleRepository;
import by.regsales.security.AppPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
class ProductManagementIntegrationTest {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"Артикул", "Наименование", "Товарная группа", "Ед. изм.",
            "Характеристики", "Отпускная цена без НДС, BYN", "Цена на дату", "Активен"};
    private static final String HISTORY = "/api/products/price-history";
    private static final String[] PRICE_HEADERS = {"Код прайс-листа", "Действует с", "Действует по", "Артикул",
            "Валюта", "Отпускная без НДС, BYN", "Ставка НДС, %", "Отпускная с НДС, BYN", "Розничный ориентир с НДС, BYN"};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;
    @Autowired ProductPriceRepository prices;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RegionRepository regions;
    @Autowired RegionAssignmentRepository assignments;

    @BeforeEach
    void setUp() {
        prices.deleteAllInBatch();
        products.deleteAllInBatch();
    }

    @Test
    void savesActiveCatalogPricesOncePerQuarter() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        byte[] catalog = book(HEADERS, new Object[][]{
                {"SP-001", "Розетка", "Розетки", "шт.", null, 4.21, null, true},
                {"SP-002", "Выключатель", "Выключатели", "шт.", null, 6.31, null, false},
        });
        mvc.perform(multipart("/api/products/import").file(xlsx(catalog)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
        var firstQuarter = Map.of("year", 2024, "quarter", 1);
        mvc.perform(write(post(HISTORY + "/snapshot").with(actor), firstQuarter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        var product = products.findBySkuIgnoreCase("SP-001").orElseThrow();
        mvc.perform(write(put("/api/products/" + product.getId()).with(actor), product("SP-001", "Розетка", "4.45")))
                .andExpect(status().isOk());
        mvc.perform(write(post(HISTORY + "/snapshot").with(actor), firstQuarter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(1));
        mvc.perform(write(post(HISTORY + "/snapshot").with(actor), Map.of("year", 2024, "quarter", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));

        mvc.perform(get(HISTORY).with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].validFrom").value("2024-04-01"))
                .andExpect(jsonPath("$[0].validTo").value("2024-06-30"))
                .andExpect(jsonPath("$[0].unitPrice").value(4.45))
                .andExpect(jsonPath("$[1].validFrom").value("2024-01-01"))
                .andExpect(jsonPath("$[1].unitPrice").value(4.21));
    }

    @Test
    void importsPriceListWithAnExactPeriodCorrectionAndRejectsIntersectingRows() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        mvc.perform(write(post("/api/products").with(actor), product("SP-001", "Розетка", "9.10")))
                .andExpect(status().isCreated());
        var january = LocalDate.of(2024, 1, 1);
        var march = LocalDate.of(2024, 3, 31);
        byte[] original = book("Прайс", PRICE_HEADERS, new Object[][]{
                {"PL-2024-Q1", january, march, "sp-001", "BYN", "4,21", 20, 5.05, 6.60}
        });
        mvc.perform(multipart(HISTORY + "/import").file(xlsx(original)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        var repeatToken = replacementToken(multipart(HISTORY + "/import").file(xlsx(original)).with(actor).with(csrf()));
        mvc.perform(multipart(HISTORY + "/import").file(xlsx(original)).param("replacementToken", repeatToken)
                        .with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(1));

        byte[] correction = book("Прайс", PRICE_HEADERS, new Object[][]{
                {"PL-2024-Q1", january, march, "SP-001", "BYN", 4.25, 20, 5.10, 6.65}
        });
        var correctionToken = replacementToken(multipart(HISTORY + "/import").file(xlsx(correction)).with(actor).with(csrf()));
        mvc.perform(multipart(HISTORY + "/import").file(xlsx(correction)).param("replacementToken", correctionToken)
                        .with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        byte[] overlap = book("Прайс", PRICE_HEADERS, new Object[][]{
                {"PL-2024-Q3", "2024-07-01", "2024-09-30", "SP-001", "BYN", 4.50},
                {"PL-2024-Q2", march, "2024-06-30", "SP-001", "BYN", 4.35}
        });
        mvc.perform(multipart(HISTORY + "/import").file(xlsx(overlap)).with(actor).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PRICE_PERIOD_OVERLAP"))
                .andExpect(jsonPath("$.fieldErrors['nomenklatura.xlsx · строка 3']").exists());
        byte[] unknown = book("Прайс", PRICE_HEADERS, new Object[][]{
                {"PL-2024-Q2", "01.04.2024", "30.06.2024", "UNKNOWN", "BYN", 4.35}
        });
        mvc.perform(multipart(HISTORY + "/import").file(xlsx(unknown)).with(actor).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PRICE_PRODUCT_NOT_FOUND"));

        mvc.perform(get(HISTORY).with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unitPrice").value(4.25))
                .andExpect(jsonPath("$[0].vatRate").value(20.0))
                .andExpect(jsonPath("$[0].priceWithVat").value(5.10))
                .andExpect(jsonPath("$[0].retailPrice").value(6.65))
                .andExpect(jsonPath("$[0].priceListCode").value("PL-2024-Q1"));
        assertThat(products.findBySkuIgnoreCase("SP-001").orElseThrow().getCurrentPrice()).isEqualByComparingTo("9.10");
    }

    @Test
    void importsPriceListsAsOneBatchAndRechecksReplacementAfterAnotherImport() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        mvc.perform(write(post("/api/products").with(actor), product("SP-001", "Розетка", "9.10")))
                .andExpect(status().isCreated());
        var first = priceFile("price_list_2024-Q1.xlsx", "2024-01-01", "2024-03-31", 4.21);
        var second = priceFile("price_list_2024-Q2.xlsx", "2024-04-01", "2024-06-30", 4.45);
        var third = priceFile("price_list_2024-Q3.xlsx", "2024-07-01", "2024-09-30", 4.55);
        mvc.perform(multipart(HISTORY + "/import").file(first).file(second).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));

        var correction = priceFile("corrected.xlsx", "2024-01-01", "2024-03-31", 4.25);
        var token = replacementToken(multipart(HISTORY + "/import").file(correction).file(third).with(actor).with(csrf()));
        assertThat(prices.count()).isEqualTo(2);
        mvc.perform(get(HISTORY).with(actor)).andExpect(jsonPath("$[1].unitPrice").value(4.21));

        var intervening = priceFile("other-correction.xlsx", "2024-01-01", "2024-03-31", 4.30);
        var otherToken = replacementToken(multipart(HISTORY + "/import").file(intervening).with(actor).with(csrf()));
        mvc.perform(multipart(HISTORY + "/import").file(intervening).param("replacementToken", otherToken).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        var refreshedToken = replacementToken(multipart(HISTORY + "/import").file(correction).file(third)
                .param("replacementToken", token).with(actor).with(csrf()));
        assertThat(refreshedToken).isNotEqualTo(token);
        assertThat(prices.count()).isEqualTo(2);
        mvc.perform(get(HISTORY).with(actor)).andExpect(jsonPath("$[1].unitPrice").value(4.30));

        mvc.perform(multipart(HISTORY + "/import").file(correction).file(third)
                        .param("replacementToken", refreshedToken).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(multipart(HISTORY + "/import").file(first).file(correction).with(actor).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors['corrected.xlsx · строка 2']").exists());
        assertThat(prices.count()).isEqualTo(3);
    }

    @Test
    void restrictsPriceHistoryToManagersWithAnAssignment() throws Exception {
        var analyst = as(account(RoleCode.ANALYST));
        var manager = account(RoleCode.MANAGER);
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
        mvc.perform(get(HISTORY).with(analyst)).andExpect(status().isForbidden());
        mvc.perform(get(HISTORY).with(as(manager))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGION_ASSIGNMENT_REQUIRED"));
        mvc.perform(write(post(HISTORY + "/snapshot").with(as(manager)), Map.of("year", 2024, "quarter", 1)))
                .andExpect(status().isForbidden());
        assignRegion(manager, null);
        mvc.perform(write(post(HISTORY + "/snapshot").with(as(manager)), Map.of("year", 2024, "quarter", 5)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.quarter").exists());
        mvc.perform(get(HISTORY).with(as(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void assignedManagerCreatesEditsArchivesAndRestoresProduct() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        var response = mvc.perform(write(post("/api/products").with(actor), product("TEST-001", "Розетка", "5.45")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.description").value("16 А, 250 В"))
                .andExpect(jsonPath("$.priceAsOf").value("2026-12-31"))
                .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        var path = "/api/products/" + id;

        mvc.perform(write(post("/api/products").with(actor), product("test-001", "Дубликат", "1.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.sku").exists());
        mvc.perform(write(put(path).with(actor), product("TEST-001", "Розетка двойная", "8.11")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentPrice").value(8.11));
        mvc.perform(get(path).with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Розетка двойная"));

        mvc.perform(write(patch(path + "/status").with(actor), Map.of("active", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/products").with(actor)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(path).with(actor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/manage").with(actor))
                .andExpect(jsonPath("$.products[0].active").value(false));
        mvc.perform(write(patch(path + "/status").with(actor), Map.of("active", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(products.findById(id).orElseThrow().getCurrentPrice()).isEqualByComparingTo("8.11");
        assertThat(products.count()).isOne();
    }

    @Test
    void writesRequireManagerWithCurrentRegionAndCsrf() throws Exception {
        var analyst = as(account(RoleCode.ANALYST));
        var manager = account(RoleCode.MANAGER);
        var actor = as(manager);

        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").with(analyst)).andExpect(status().isOk());
        mvc.perform(write(post("/api/products").with(analyst), product("TEST-001", "Розетка", "5.45")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/export").with(analyst)).andExpect(status().isForbidden());

        var assignment = assignRegion(manager, LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(1));
        mvc.perform(get("/api/products/manage").with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(write(post("/api/products").with(actor), product("TEST-001", "Розетка", "5.45")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REGION_ASSIGNMENT_REQUIRED"));
        mvc.perform(multipart("/api/products/import").file(xlsx(new byte[]{1})).with(actor).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REGION_ASSIGNMENT_REQUIRED"));

        assignment.setEndDate(null);
        assignments.saveAndFlush(assignment);
        mvc.perform(post("/api/products").with(actor).contentType("application/json")
                        .content(json.writeValueAsString(product("TEST-001", "Розетка", "5.45"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(write(post("/api/products").with(actor), product("TEST-001", "Розетка", "5.45")))
                .andExpect(status().isCreated());
    }

    @Test
    void importsNamedSheetUpdatesBySkuAndExportsReimportableWorkbook() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        byte[] source = book(HEADERS, new Object[][]{
                {"SP-001", "Розетка", "Розетки", "шт.", "16 А, 250 В", 5.45, LocalDate.of(2026, 12, 31), true},
                {"SP-002", "Выключатель", "Выключатели", "шт.", null, "4,10", null, false},
        });
        mvc.perform(multipart("/api/products/import").file(xlsx(source)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
        mvc.perform(multipart("/api/products/import").file(xlsx(source)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(2));

        byte[] updated = book(new String[]{"Артикул", "Наименование", "Ед. изм.", "Отпускная цена без НДС, BYN"},
                new Object[][]{{"sp-001", "Розетка с заземлением", "шт.", 6.45}});
        mvc.perform(multipart("/api/products/import").file(xlsx(updated)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        var saved = products.findBySkuIgnoreCase("SP-001").orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("16 А, 250 В");
        assertThat(saved.getCategory()).isEqualTo("Розетки");
        assertThat(saved.getPriceAsOf()).isNull();
        assertThat(saved.getSku()).isEqualTo("SP-001");
        assertThat(products.findBySkuIgnoreCase("SP-002").orElseThrow().isActive()).isFalse();

        var result = mvc.perform(get("/api/products/export").with(actor))
                .andExpect(status().isOk()).andExpect(content().contentType(XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"products.xlsx\""))
                .andReturn();
        var download = result.getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(download))) {
            assertThat(workbook.getSheet("Товары").getPhysicalNumberOfRows()).isEqualTo(3);
        }
        mvc.perform(multipart("/api/products/import").file(xlsx(download)).with(actor).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(2));
    }

    @Test
    void rowErrorsRejectTheWholeFileAndIdentifyDuplicateAndFormulaCells() throws Exception {
        var manager = account(RoleCode.MANAGER);
        assignRegion(manager, null);
        var actor = as(manager);
        byte[] source = book(HEADERS, new Object[][]{
                {"SP-001", "Розетка", "Розетки", "шт.", null, 5.45, null, true},
                {"SP-002", "Выключатель", "Выключатели", "шт.", null, -4.10, null, true},
                {"sp-001", "Дубль", "Розетки", "шт.", null, 6.45, null, true},
                {"SP-003", "Рамка", "Рамки", "шт.", null, new Formula("1+2"), null, true},
        });
        mvc.perform(multipart("/api/products/import").file(xlsx(source)).with(actor).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors['Строка 3 · Отпускная цена без НДС, BYN']").exists())
                .andExpect(jsonPath("$.fieldErrors['Строка 4 · Артикул']").exists())
                .andExpect(jsonPath("$.fieldErrors['Строка 5 · Отпускная цена без НДС, BYN']")
                        .value("Замените формулу её значением"));
        assertThat(products.count()).isZero();

        mvc.perform(multipart("/api/products/import").file(xlsx(new byte[]{1, 2, 3})).with(actor).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORKBOOK"));
    }

    private Map<String, String> product(String sku, String name, String price) {
        return Map.of("sku", sku, "name", name, "category", "Розетки", "unit", "шт.", "description", "16 А, 250 В",
                "currentPrice", price, "priceAsOf", "2026-12-31");
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, Object data) throws Exception {
        return request.with(csrf()).contentType("application/json").content(json.writeValueAsString(data));
    }

    private AppUser account(RoleCode code) {
        var role = roles.findByCode(code).orElseGet(() -> {
            var entry = new Role();
            entry.setCode(code);
            entry.setName(code.name());
            return roles.saveAndFlush(entry);
        });
        var login = "catalog-" + UUID.randomUUID();
        var user = new AppUser();
        user.setLogin(login);
        user.setEmail(login + "@example.test");
        user.setFullName("Сотрудник");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private RegionAssignment assignRegion(AppUser manager, LocalDate until) {
        var region = new Region();
        region.setCode("T-" + UUID.randomUUID().toString().substring(0, 8));
        region.setName("Регион " + region.getCode());
        regions.saveAndFlush(region);
        var assignment = new RegionAssignment();
        assignment.setManager(manager);
        assignment.setRegion(region);
        assignment.setStartDate(LocalDate.now(ZoneId.of("Europe/Minsk")).minusDays(3));
        assignment.setEndDate(until);
        return assignments.saveAndFlush(assignment);
    }

    private RequestPostProcessor as(AppUser account) {
        var principal = new AppPrincipal(account);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private MockMultipartFile xlsx(byte[] contents) {
        return new MockMultipartFile("file", "nomenklatura.xlsx", XLSX, contents);
    }

    private MockMultipartFile priceFile(String filename, String from, String to, double price) throws Exception {
        return new MockMultipartFile("files", filename, XLSX, book("Прайс", PRICE_HEADERS,
                new Object[][]{{"PL", from, to, "SP-001", "BYN", price}}));
    }

    private String replacementToken(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_PERIOD_EXISTS"))
                .andExpect(jsonPath("$.replacementToken").isString()).andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray()).get("replacementToken").asText();
    }

    private byte[] book(String[] headers, Object[][] values) throws Exception {
        return book("Товары", headers, values);
    }

    private byte[] book(String sheetName, String[] headers, Object[][] values) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("Комплекты");
            var sheet = workbook.createSheet(sheetName);
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
            for (int i = 0; i < values.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < values[i].length; j++) writeCell(row, j, values[i][j], dateStyle);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeCell(Row row, int column, Object value, org.apache.poi.ss.usermodel.CellStyle dateStyle) {
        var cell = row.createCell(column);
        switch (value) {
            case String text -> cell.setCellValue(text);
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean flag -> cell.setCellValue(flag);
            case Formula formula -> cell.setCellFormula(formula.text());
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            }
            case null -> { }
            default -> throw new IllegalArgumentException("Unsupported test cell");
        }
    }

    private record Formula(String text) {}
}
