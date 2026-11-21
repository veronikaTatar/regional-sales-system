package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BelstatTurnoverClientTest {
    private static final String OFFICIAL_PATH = "/upload-belstat/upload-belstat-excel/Oficial_statistika/2026/";
    private static final YearMonth JANUARY = YearMonth.of(2026, 1);
    private static final List<String> REGIONS = List.of("Брестская область", "Витебская", "Гомельская", "Гродненская",
            "г.Минск", "Минская", "Могилёвская область");

    @Test void downloadsConfirmedAndPublishedEditionsWithoutGuessingEarlierMonths() throws Exception {
        var page = BelstatTurnoverClient.PUBLICATIONS;
        var september = page.resolve(OFFICIAL_PATH + "osep_obl_2609.xlsx");
        var october = URI.create("https://www.belstat.gov.by" + OFFICIAL_PATH + "osep_obl_2610.xlsx");
        var files = Map.of(page, """
                <a href="https://other.invalid/osep_obl_2701.xlsx">Страница другого сайта</a>
                <a href="%sdom_sale-non_food-2026q1.xlsx">Отдельные товары</a>
                <a href="/viewer/?doclink=%s#1">Региональная сводка</a>
                """.formatted(OFFICIAL_PATH, october).getBytes(StandardCharsets.UTF_8),
                september, workbook("Январь – сентябрь 2026 г.", "млн руб.", true),
                october, workbook("Январь – октябрь 2026 г.", "млн. рублей", true));
        var requests = new ArrayList<URI>();

        var download = BelstatTurnoverClient.downloadYear(null, url -> {
            requests.add(url);
            assertThat(files).containsKey(url);
            return files.get(url);
        });

        assertThat(requests).containsExactly(page, september, october);
        assertThat(download.year()).isEqualTo(2026);
        assertThat(download.releases()).extracting(BelstatTurnoverClient.Release::period)
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(download.releases().getLast().source()).isEqualTo(october);
        var totals = download.releases().getLast().totals();
        assertThat(totals.keySet()).isEqualTo(BelstatWorkbook.regionCodes());
        assertThat(totals.get("BY-BR")).isEqualByComparingTo("3747800000");
        assertThat(download.failures()).isEmpty();
    }

    @Test void selectsNewlyPublishedYearsAndReportsFailuresForTheirListedEditions() throws Exception {
        var page = BelstatTurnoverClient.PUBLICATIONS;
        var february = page.resolve("/published/2027/osep_obl_2702.xlsx");
        var march = page.resolve("/published/2027/osep_obl_2703.xlsx");
        var files = Map.of(page, """
                <a href="%s">Региональная сводка за февраль</a>
                <a href="%s">Региональная сводка за март</a>
                """.formatted(february, march).getBytes(StandardCharsets.UTF_8),
                march, workbook("Январь – март 2027 г.", "млн. рублей", true));
        var requests = new ArrayList<URI>();

        var download = BelstatTurnoverClient.downloadYear(null, url -> {
            requests.add(url);
            if (!files.containsKey(url)) throw BelstatWebsite.unavailable("Белстат вернул HTTP 404");
            return files.get(url);
        });

        assertThat(requests).containsExactly(page, february, march);
        assertThat(download.year()).isEqualTo(2027);
        assertThat(download.releases()).extracting(BelstatTurnoverClient.Release::period).containsExactly(YearMonth.of(2027, 3));
        assertThat(download.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.period()).isEqualTo(YearMonth.of(2027, 2));
            assertThat(failure.message()).contains("404");
        });
    }

    @Test void rejectsMismatchedPeriodWrongUnitsAndIncompleteAmounts() throws Exception {
        var march = workbook("Январь — март 2026 г.", "млн. рублей", true);
        var wrongUnits = workbook("Январь 2026 г.", "тыс. рублей", true);
        var missingAmount = workbook("Январь 2026 г.", "млн. рублей", false);

        for (var file : List.of(march, wrongUnits, missingAmount)) {
            assertThatThrownBy(() -> BelstatTurnoverWorkbook.read(file, JANUARY))
                    .isInstanceOf(ApiException.class).hasMessageContaining("2026-01");
        }
        assertThatThrownBy(() -> BelstatTurnoverWorkbook.read(march, YearMonth.of(2026, 5)))
                .isInstanceOf(ApiException.class).hasMessageContaining("2026-05");
    }

    private static byte[] workbook(String period, String units, boolean amountPresent) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Таблица1");
            sheet.createRow(1).createCell(1).setCellValue(period);
            var header = sheet.createRow(4);
            header.createCell(1).setCellValue(units);
            header.createCell(2).setCellValue("в % к прошлому году");
            var earlier = sheet.createRow(5);
            earlier.createCell(0).setCellValue("Промышленность");
            earlier.createCell(1).setCellValue(20000);
            sheet.createRow(14).createCell(0).setCellValue("Розничный товарооборот");
            var country = sheet.createRow(15);
            country.createCell(0).setCellValue("Республика Беларусь");
            country.createCell(1).setCellValue(30000);
            for (int index = 0; index < REGIONS.size(); index++) {
                var row = sheet.createRow(16 + index);
                row.createCell(0).setCellValue(REGIONS.get(index));
                if (index > 0) row.createCell(1).setCellValue(4019.5 + index);
                else if (amountPresent) row.createCell(1).setCellValue("3\u00a0747,8");
                row.createCell(2).setCellValue(106.2);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
