package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class BelstatTurnoverClient {
    static final URI PUBLICATIONS = URI.create("https://belstat.gov.by/ofitsialnaya-statistika/realny-sector-ekonomiki/"
            + "vnytrennia-torgovlya/operativnye-dannye/");
    private static final Pattern FILE = Pattern.compile("/osep_obl_(\\d{2})(0[1-9]|1[0-2])\\.xlsx$", Pattern.CASE_INSENSITIVE);
    private static final URI REPORTS_2026 = PUBLICATIONS.resolve("/upload-belstat/upload-belstat-excel/Oficial_statistika/2026/");
    // Keep verified releases available when the page replaces its links with a newer edition.
    private static final Map<YearMonth, URI> CONFIRMED_RELEASES = Map.of(
            YearMonth.of(2026, 9), REPORTS_2026.resolve("osep_obl_2609.xlsx"),
            YearMonth.of(2026, 10), REPORTS_2026.resolve("osep_obl_2610.xlsx"));

    record Release(YearMonth period, URI source, Map<String, BigDecimal> totals) {}
    record Failure(YearMonth period, String message) {}
    record Download(int year, List<Release> releases, List<Failure> failures) {}

    Download downloadYear(Integer requestedYear) {
        return downloadYear(requestedYear, BelstatWebsite::fetch);
    }

    static Download downloadYear(Integer requestedYear, Function<URI, byte[]> fetch) {
        var published = publicationLinks(BelstatWebsite.text(fetch.apply(PUBLICATIONS)));
        CONFIRMED_RELEASES.forEach(published::putIfAbsent);
        int year = requestedYear == null ? published.keySet().stream().mapToInt(YearMonth::getYear).max().orElseThrow() : requestedYear;
        var sources = published.entrySet().stream().filter(entry -> entry.getKey().getYear() == year)
                .sorted(Map.Entry.comparingByKey()).toList();
        if (sources.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELSTAT_TURNOVER_YEAR_NOT_FOUND",
                    "Нет ссылок на региональные сводки Белстата за " + year + " год");
        }

        var releases = new ArrayList<Release>();
        var failures = new ArrayList<Failure>();
        for (var entry : sources) {
            var period = entry.getKey();
            var source = entry.getValue();
            try {
                releases.add(new Release(period, source, BelstatTurnoverWorkbook.read(fetch.apply(source), period)));
            } catch (ApiException error) {
                failures.add(new Failure(period, error.getMessage()));
            }
        }
        return new Download(year, List.copyOf(releases), List.copyOf(failures));
    }

    static Map<YearMonth, URI> publicationLinks(String html) {
        var published = new LinkedHashMap<YearMonth, URI>();
        for (var link : Jsoup.parse(html).select("a[href]")) {
            var target = BelstatWebsite.workbookLink(PUBLICATIONS, link.attr("href"));
            if (target == null) continue;
            var match = FILE.matcher(target.getPath());
            if (!match.find()) continue;
            var period = YearMonth.of(2000 + Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
            published.putIfAbsent(period, target);
        }
        return published;
    }
}
