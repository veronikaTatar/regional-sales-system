package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BelstatSalaryClientTest {
    private static final String REGIONAL = "/upload-belstat/nach_sr_zarplata_obl-2601.xlsx";

    @Test void followsTheArchiveAndDownloadsOnlyTheRegionalFile() throws Exception {
        var archive = BelstatSalaryClient.ARCHIVE;
        var january = archive.resolve("january-2026/");
        var february = archive.resolve("february-2026/");
        var workbook = archive.resolve(REGIONAL);
        var files = Map.of(
                archive, bytes("""
                        <a href="https://external.invalid/">в марте 2027 г.</a>
                        <a href="december-2025/">в декабре 2025 г.</a>
                        <a href="february-2026/">в феврале 2026 г.</a>
                        <a href="january-2026/">в январе 2026 г.</a>
                        """),
                january, bytes("""
                        <a href="/upload-belstat/nach_sr_zarplata_obl-1991-2015.xlsx">Средняя заработная плата по областям с 1991 по 2015 год</a>
                        <a href="/upload-belstat/nach_sr_zarplata-2601.xlsx">Заработная плата по видам экономической деятельности</a>
                        <a href="https://example.invalid/nach_sr_zarplata_obl-2601.xlsx">Заработная плата по областям</a>
                        <a href="%s">Заработная плата по областям и г.Минску</a>
                        """.formatted(REGIONAL)),
                february, bytes("<p>Выпуск без файла</p>"),
                workbook, salaryWorkbook());

        var downloaded = BelstatSalaryClient.downloadYear(null, url -> {
            assertThat(files).containsKey(url);
            return files.get(url);
        });

        assertThat(downloaded.year()).isEqualTo(2026);
        assertThat(downloaded.releases()).singleElement().satisfies(release -> {
            assertThat(release.period()).isEqualTo(YearMonth.of(2026, 1));
            assertThat(release.source()).isEqualTo(workbook);
            assertThat(release.salaries()).hasSize(7);
            assertThat(release.salaries().get("BY-HM")).isEqualByComparingTo("3747.8");
        });
        assertThat(downloaded.failures()).extracting(BelstatSalaryClient.Failure::period)
                .containsExactly(YearMonth.of(2026, 2));
    }

    @Test void validatesTheMonthAndRecognizesOfficialViewerLinks() throws Exception {
        var html = """
                <a href="/viewer/?doclink=https://www.belstat.gov.by%s#1">просмотр</a>
                """.formatted(REGIONAL);
        assertThat(BelstatSalaryClient.workbookLink(BelstatSalaryClient.ARCHIVE, YearMonth.of(2026, 1), html))
                .isEqualTo(URI.create("https://www.belstat.gov.by" + REGIONAL));
        assertThatThrownBy(() -> BelstatSalaryWorkbook.read(salaryWorkbook(), YearMonth.of(2025, 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("2025-01");
    }

    static byte[] salaryWorkbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("По областям");
            var heading = sheet.createRow(0);
            heading.createCell(1).setCellValue("Номинальная начисленная среднемесячная заработная плата, рублей");
            heading.createCell(3).setCellValue("Реальная заработная плата, %");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));
            var months = sheet.createRow(1);
            months.createCell(1).setCellValue("январь–декабрь");
            months.createCell(2).setCellValue("январь");
            months.createCell(3).setCellValue("январь");
            sheet.createRow(2).createCell(1).setCellValue("2026 г.");
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 3));
            var regions = List.of("Республика Беларусь", "Брестская область", "Витебская область", "Гомельская область",
                    "Гродненская область", "Минская область", "Могилевская область", "г.Минск");
            for (int index = 0; index < regions.size(); index++) {
                var row = sheet.createRow(index + 3);
                row.createCell(0).setCellValue(regions.get(index));
                row.createCell(1).setCellValue(1200);
                row.createCell(2).setCellValue("3\u00a0747,8");
                row.createCell(3).setCellValue(109.3);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] bytes(String html) { return html.getBytes(StandardCharsets.UTF_8); }
}
