package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class BelstatTurnoverClient {
    static final URI PUBLICATIONS = URI.create("https://belstat.gov.by/ofitsialnaya-statistika/realny-sector-ekonomiki/"
            + "vnytrennia-torgovlya/operativnye-dannye/");
    private static final Pattern FILE = Pattern.compile("/osep_obl_(\\d{2})(0[1-9]|1[0-2])\\.xlsx$", Pattern.CASE_INSENSITIVE);

    record Release(YearMonth period, URI source, Map<String, BigDecimal> totals) {}
    record Failure(YearMonth period, String message) {}
    record Download(int year, List<Release> releases, List<Failure> failures) {}

    Download downloadYear(Integer requestedYear) {
        return downloadYear(requestedYear, BelstatWebsite::fetch);
    }

    static Download downloadYear(Integer requestedYear, Function<URI, byte[]> fetch) {
        var published = publicationLinks(BelstatWebsite.text(fetch.apply(PUBLICATIONS)));
        int year = requestedYear == null ? published.keySet().stream().mapToInt(YearMonth::getYear).max().orElseThrow() : requestedYear;
        var latest = published.keySet().stream().filter(period -> period.getYear() == year).max(Comparator.naturalOrder())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELSTAT_TURNOVER_YEAR_NOT_FOUND",
                        "На странице Белстата нет региональных сводок за " + year + " год"));

        var releases = new ArrayList<Release>();
        var failures = new ArrayList<Failure>();
        for (int month = 1; month <= latest.getMonthValue(); month++) {
            var period = YearMonth.of(year, month);
            var source = published.getOrDefault(period, published.get(latest).resolve(fileName(period)));
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
        if (published.isEmpty()) throw BelstatWebsite.unavailable("На странице Белстата не найдены региональные сводки osep_obl в формате XLSX");
        return published;
    }

    private static String fileName(YearMonth period) {
        // Older editions are requested using the naming convention of the edition found on the page.
        return String.format(Locale.ROOT, "osep_obl_%02d%02d.xlsx", period.getYear() % 100, period.getMonthValue());
    }
}
