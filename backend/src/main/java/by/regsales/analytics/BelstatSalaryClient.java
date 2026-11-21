package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class BelstatSalaryClient {
    static final URI ARCHIVE = URI.create("https://belstat.gov.by/ofitsialnaya-statistika/realny-sector-ekonomiki/"
            + "stoimost-rabochey-sily/operativnye-dannye/o-nachislennoy-sredney-zarabotnoy-plate-rabotnikov/");
    private static final Set<String> HOSTS = Set.of("belstat.gov.by", "www.belstat.gov.by");
    private static final List<String> MONTHS = List.of("январе", "феврале", "марте", "апреле", "мае", "июне",
            "июле", "августе", "сентябре", "октябре", "ноябре", "декабре");
    private static final Pattern PERIOD = Pattern.compile("^в ([а-я]+) (20\\d{2}) г\\.?$");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    record Release(YearMonth period, URI source, Map<String, BigDecimal> salaries) {}
    record Failure(YearMonth period, String message) {}
    record Download(int year, List<Release> releases, List<Failure> failures) {}

    Download downloadYear(Integer requestedYear) {
        return downloadYear(requestedYear, BelstatSalaryClient::fetch);
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
            var target = officialLink(page, link.attr("href"));
            if (target == null) continue;
            if (target.getPath().startsWith("/viewer/") && target.getQuery() != null && target.getQuery().startsWith("doclink=")) {
                target = officialLink(page, target.getQuery().substring("doclink=".length()));
                if (target == null) continue;
            }
            var path = target.getPath().toLowerCase(Locale.ROOT);
            if (!path.endsWith(".xlsx")) continue;
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

    private static byte[] fetch(URI url) {
        try {
            for (int redirects = 0; redirects <= 4; redirects++) {
                var response = Jsoup.connect(url.toASCIIString()).followRedirects(false).ignoreContentType(true)
                        .ignoreHttpErrors(true).timeout(10_000).maxBodySize(MAX_RESPONSE_BYTES + 1).execute();
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    url = officialLink(url, response.header("Location"));
                    if (url == null) throw unavailable("Белстат перенаправил запрос за пределы официального сайта");
                    continue;
                }
                if (response.statusCode() != 200) throw unavailable("Белстат вернул HTTP " + response.statusCode());
                var bytes = response.bodyAsBytes();
                if (bytes.length > MAX_RESPONSE_BYTES) throw unavailable("Ответ Белстата превышает 2 МБ");
                return bytes;
            }
        } catch (IOException error) {
            throw unavailable("Сервер приложения не смог подключиться к Белстату. Проверьте доступ к belstat.gov.by с этой машины");
        }
        throw unavailable("Слишком много перенаправлений на сайте Белстата");
    }

    private static URI officialLink(URI base, String path) {
        if (path == null || path.isBlank()) return null;
        try {
            var url = base.resolve(path.strip()).normalize();
            var host = url.getHost();
            return "https".equalsIgnoreCase(url.getScheme()) && host != null && HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    && url.getUserInfo() == null && (url.getPort() == -1 || url.getPort() == 443) ? url : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "BELSTAT_DOWNLOAD_FAILED", message);
    }
}
