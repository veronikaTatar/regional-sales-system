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

import static by.regsales.analytics.BelstatWebsite.normalized;
import static by.regsales.analytics.BelstatWebsite.officialLink;
import static by.regsales.analytics.BelstatWebsite.text;
import static by.regsales.analytics.BelstatWebsite.unavailable;

@Component
public class BelstatSalaryClient {
    static final URI ARCHIVE = URI.create("https://belstat.gov.by/ofitsialnaya-statistika/realny-sector-ekonomiki/"
            + "stoimost-rabochey-sily/operativnye-dannye/o-nachislennoy-sredney-zarabotnoy-plate-rabotnikov/");
    private static final List<String> MONTHS = List.of("январе", "феврале", "марте", "апреле", "мае", "июне",
            "июле", "августе", "сентябре", "октябре", "ноябре", "декабре");
    private static final Pattern PERIOD = Pattern.compile("^в ([а-я]+) (20\\d{2}) г\\.?$");

    record Release(YearMonth period, URI source, Map<String, BigDecimal> salaries) {}
    record Failure(YearMonth period, String message) {}
    record Download(int year, List<Release> releases, List<Failure> failures) {}

    Download downloadYear(Integer requestedYear) {
        return downloadYear(requestedYear, BelstatWebsite::fetch);
    }

    static Download downloadYear(Integer requestedYear, Function<URI, byte[]> fetch) {
        var archive = archiveLinks(text(fetch.apply(ARCHIVE)));
        int year = requestedYear == null ? archive.keySet().stream().mapToInt(YearMonth::getYear).max().orElseThrow() : requestedYear;
        var months = archive.keySet().stream().filter(period -> period.getYear() == year)
                .sorted(Comparator.reverseOrder()).toList();
        if (months.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELSTAT_YEAR_NOT_FOUND", "В архиве Белстата нет выпусков зарплат за " + year + " год");
        }

        var releases = new ArrayList<Release>();
        var failures = new ArrayList<Failure>();
        for (var month : months) {
            try {
                var page = archive.get(month);
                var xlsx = workbookLink(page, month, text(fetch.apply(page)));
                releases.add(new Release(month, xlsx, BelstatSalaryWorkbook.read(fetch.apply(xlsx), month)));
            } catch (ApiException error) {
                failures.add(new Failure(month, error.getMessage()));
            }
        }
        return new Download(year, List.copyOf(releases), List.copyOf(failures));
    }

    static Map<YearMonth, URI> archiveLinks(String html) {
        var editions = new LinkedHashMap<YearMonth, URI>();
        for (var link : Jsoup.parse(html).select("a[href]")) {
            var match = PERIOD.matcher(normalized(link.text()));
            if (!match.matches()) continue;
            int month = MONTHS.indexOf(match.group(1)) + 1;
            var page = officialLink(ARCHIVE, link.attr("href"));
            if (month == 0 || page == null || !page.getPath().startsWith(ARCHIVE.getPath())
                    || page.getPath().equals(ARCHIVE.getPath())) continue;
            editions.putIfAbsent(YearMonth.of(Integer.parseInt(match.group(2)), month), page);
        }
        if (editions.isEmpty()) throw unavailable("На странице Белстата не найден список месячных выпусков зарплат");
        return editions;
    }

    static URI workbookLink(URI page, YearMonth period, String html) {
        String monthToken = String.format(Locale.ROOT, "%02d%02d", period.getYear() % 100, period.getMonthValue());
        URI regionalLink = null;
        for (var link : Jsoup.parse(html).select("a[href]")) {
            var target = BelstatWebsite.workbookLink(page, link.attr("href"));
            if (target == null) continue;
            var path = target.getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith("nach_sr_zarplata_obl-" + monthToken + ".xlsx")
                    || path.endsWith("nach_sr_zarplata_obl_" + monthToken + ".xlsx")) return target;
            var label = normalized(link.text());
            if (regionalLink == null && label.contains("реальная заработная плата") && label.contains("по областям")) {
                regionalLink = target;
            }
        }
        if (regionalLink != null) return regionalLink;
        throw unavailable("В выпуске Белстата не найдена книга Excel с зарплатами по областям и г. Минску");
    }

}
