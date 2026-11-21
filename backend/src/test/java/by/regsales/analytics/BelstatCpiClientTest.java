package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BelstatCpiClientTest {
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final List<String> REGIONS = List.of("Брестская область", "Витебская", "Гомельская", "Гродненская",
            "г. Минск", "Минская", "Могилёвская обл.*");

    @Test void downloadsTheRegionalMonthlySeriesUsingThePublishedLinks() throws Exception {
        var page = BelstatCpiClient.PUBLICATIONS;
        var april = page.resolve("/upload/CPI(04_%202026)_previous.xls");
        var october = page.resolve("/upload/CPI(10_2026)_previous.xlsx");
        var html = """
                <p>ИНДЕКСЫ ПОТРЕБИТЕЛЬСКИХ ЦЕН</p>
                <p>(в процентах к предыдущему месяцу)</p>
                <table><tr><td>2027 год</td></tr><tr><td><a href="/upload/national.xls">за апрель</a></td></tr></table>
                <b><p>ИНДЕКСЫ ПОТРЕБИТЕЛЬСКИХ ЦЕН В&nbsp;РАЗРЕЗЕ ОБЛАСТЕЙ И&nbsp;Г.&nbsp;МИНСКА</p></b>
                <p>(в процентах к предыдущему месяцу)</p>
                <div class="table"><table><tr><td></td><td>2026 год</td><td>2025 год</td></tr>
                <tr><td></td><td><a href="/viewer/?doclink=https://www.belstat.gov.by/upload/CPI(04_ 2026)_previous.xls#1"></a>
                <a href="/upload/CPI(04_ 2026)_previous.xls">за апрель</a></td><td><a href="/upload/2025.xls">за апрель</a></td></tr>
                <tr><td></td><td><a href="https://outside.example/sep.xls">за сентябрь</a></td><td></td></tr>
                <tr><td></td><td><a href="/upload/CPI(10_2026)_previous.xlsx">за октябрь</a></td><td></td></tr>
                </table></div>
                <b><p>ИНДЕКСЫ ПОТРЕБИТЕЛЬСКИХ ЦЕН В РАЗРЕЗЕ ОБЛАСТЕЙ И Г. МИНСКА</p></b>
                <p>(в процентах к декабрю предыдущего года)</p>
                <table><tr><td>2026 год</td></tr><tr><td><a href="/upload/december-base.xls">за май</a></td></tr></table>
                """;
        var responses = Map.of(page, html.getBytes(StandardCharsets.UTF_8),
                april, regionalColumns(new HSSFWorkbook(), "Непродовольственные товары"),
                october, regionalRows(new XSSFWorkbook(), REGIONS));
        var requests = new ArrayList<URI>();

        var result = BelstatCpiClient.downloadYear(null, uri -> {
            requests.add(uri);
            assertThat(responses).containsKey(uri);
            return responses.get(uri);
        });

        assertThat(requests).containsExactly(page, april, october);
        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.issues()).isEmpty();
        assertThat(result.releases()).extracting(BelstatCpiClient.Release::period).containsExactly(APRIL, OCTOBER);
        var aprilValues = result.releases().getFirst().indices();
        assertThat(aprilValues.keySet()).isEqualTo(BelstatWorkbook.regionCodes());
        assertThat(aprilValues.get("BY-BR")).isEqualByComparingTo("100.4");
        assertThat(aprilValues.get("BY-VI")).isEqualByComparingTo("99.9");
        assertThat(aprilValues.get("BY-HM")).isEqualByComparingTo("100.1");
        assertThat(result.releases().getLast().indices().get("BY-BR")).isEqualByComparingTo("99.6");
    }

    @Test void rejectsTheGeneralIndexAndIncompleteRegionalReleases() throws Exception {
        var general = regionalColumns(new HSSFWorkbook(), "Товары и услуги — всего");
        var insufficient = regionalRows(new XSSFWorkbook(), REGIONS.subList(0, 6));
        var narrowCategory = regionalColumns(new HSSFWorkbook(), "Непродовольственные товары без топлива");

        for (var workbook : List.of(general, insufficient, narrowCategory)) {
            assertThatThrownBy(() -> BelstatCpiWorkbook.read(workbook, OCTOBER))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Непродовольственные товары");
        }
    }

    @Test void refusesToSaveAnAprilWorkbookUnderAnotherMonth() throws Exception {
        var workbook = regionalColumns(new HSSFWorkbook(), "Непродовольственные товары");
        assertThatThrownBy(() -> BelstatCpiWorkbook.read(workbook, OCTOBER))
                .isInstanceOf(ApiException.class).hasMessageContaining("2026-04 вместо 2026-10");
    }

    @Test void keepsOtherMonthsWhenAWorkbookCannotBeRead() throws Exception {
        var html = """
                <p>ИНДЕКСЫ ПОТРЕБИТЕЛЬСКИХ ЦЕН В РАЗРЕЗЕ ОБЛАСТЕЙ И Г. МИНСКА</p>
                <p>(в процентах к предыдущему месяцу)</p>
                <table><tr><th>2026 год</th></tr>
                <tr><td><a href="/apr.xls">за апрель</a></td></tr>
                <tr><td><a href="/oct.xls">за октябрь</a></td></tr></table>
                """;
        var page = BelstatCpiClient.PUBLICATIONS;
        var responses = Map.of(page, html.getBytes(StandardCharsets.UTF_8),
                page.resolve("/apr.xls"), regionalColumns(new HSSFWorkbook(), "Непродовольственные"),
                page.resolve("/oct.xls"), "<html>Ошибка</html>".getBytes(StandardCharsets.UTF_8));

        var result = BelstatCpiClient.downloadYear(2026, responses::get);

        assertThat(result.releases()).extracting(BelstatCpiClient.Release::period).containsExactly(APRIL);
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.period()).isEqualTo("2026-10");
            assertThat(issue.message()).contains("XLS");
        });
    }

    private static byte[] regionalColumns(Workbook workbook, String label) throws IOException {
        try (workbook; var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("ИПЦ");
            sheet.createRow(0).createCell(0).setCellValue("ИНДЕКСЫ ПОТРЕБИТЕЛЬСКИХ ЦЕН В РАЗРЕЗЕ ОБЛАСТЕЙ и г.МИНСКА за апрель 2026 года");
            sheet.createRow(1).createCell(0).setCellValue("(предыдущий месяц = 100)");
            var heading = sheet.createRow(3);
            heading.createCell(1).setCellValue("Республика Беларусь");
            var general = sheet.createRow(4);
            general.createCell(0).setCellValue("ИПЦ всего");
            var indices = sheet.createRow(7);
            indices.createCell(0).setCellValue(label);
            for (int i = 0; i < REGIONS.size(); i++) {
                heading.createCell(i + 2).setCellValue(REGIONS.get(i));
                general.createCell(i + 2).setCellValue(103.3);
                indices.createCell(i + 2).setCellValue(100.4);
            }
            var percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
            indices.getCell(3).setCellValue(0.999);
            indices.getCell(3).setCellStyle(percent);
            indices.getCell(6).setCellValue("100,1 %");
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static byte[] regionalRows(Workbook workbook, List<String> regions) throws IOException {
        try (workbook; var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("ИПЦ");
            var heading = sheet.createRow(2);
            heading.createCell(1).setCellValue("Товары и услуги");
            heading.createCell(2).setCellValue("Непродовольственные товары (1)");
            var country = sheet.createRow(3);
            country.createCell(0).setCellValue("Республика Беларусь");
            country.createCell(2).setCellValue(100.8);
            for (int i = 0; i < regions.size(); i++) {
                var row = sheet.createRow(4 + i);
                row.createCell(0).setCellValue(regions.get(i));
                row.createCell(1).setCellValue(101.5);
                row.createCell(2).setCellValue(99.6);
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }
}
