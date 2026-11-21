package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import static by.regsales.analytics.BelstatWebsite.normalized;

@Component
public class BelstatCpiClient {
    static final URI PUBLICATIONS = URI.create("https://belstat.gov.by/ofitsialnaya-statistika/realny-sector-ekonomiki/tseny/"
            + "potrebitelskie-tseny/operativnye-dannye/indeksy-potrebitelskikh-tsen-po-respublike-belarus/");
    private static final String REGIONAL_HEADING = "индексы потребительских цен в разрезе областей и г. минска";
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s+год");
    private static final List<String> MONTHS = List.of("январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь");

    record Release(YearMonth period, URI source, Map<String, BigDecimal> indices) {}
    record Download(int year, List<Release> releases, List<AnalyticsDtos.MonthlyDownloadIssue> issues) {}

    Download downloadYear(Integer requestedYear) {
        return downloadYear(requestedYear, BelstatWebsite::fetch);
    }

    static Download downloadYear(Integer requestedYear, Function<URI, byte[]> fetch) {
        var published = publicationLinks(BelstatWebsite.text(fetch.apply(PUBLICATIONS)));
        int year = requestedYear == null ? published.lastKey().getYear() : requestedYear;
        var releases = new ArrayList<Release>();
        var issues = new ArrayList<AnalyticsDtos.MonthlyDownloadIssue>();
        published.forEach((period, source) -> {
            if (period.getYear() != year) return;
            try {
                releases.add(new Release(period, source, BelstatCpiWorkbook.read(fetch.apply(source), period)));
            } catch (ApiException error) {
                issues.add(new AnalyticsDtos.MonthlyDownloadIssue(period.toString(), error.getMessage()));
            }
        });
        if (releases.isEmpty() && issues.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BELSTAT_CPI_YEAR_NOT_FOUND",
                    "На странице Белстата нет региональных выпусков ИПЦ за " + year + " год");
        }
        return new Download(year, List.copyOf(releases), List.copyOf(issues));
    }

    static TreeMap<YearMonth, URI> publicationLinks(String html) {
        var result = new TreeMap<YearMonth, URI>();
        for (var heading : Jsoup.parse(html).select("p, h2, h3")) {
            if (!normalized(heading.text()).replace("г.минска", "г. минска").equals(REGIONAL_HEADING)) continue;
            var basis = nextContent(heading.parent().is("b, strong") ? heading.parent() : heading);
            if (basis == null || !normalized(basis.text()).contains("к предыдущему месяцу")) continue;
            var container = nextContent(basis);
            if (container == null) continue;
            var table = container.is("table") ? container : container.selectFirst("table");
            if (table != null) readTable(table, result);
        }
        if (result.isEmpty()) {
            throw BelstatWebsite.unavailable("На странице Белстата не найдены региональные выпуски ИПЦ к предыдущему месяцу");
        }
        return result;
    }

    private static Element nextContent(Element element) {
        var sibling = element.nextElementSibling();
        while (sibling != null && sibling.is("br")) sibling = sibling.nextElementSibling();
        return sibling;
    }

    private static void readTable(Element table, Map<YearMonth, URI> result) {
        var rows = table.select("tr");
        if (rows.isEmpty()) return;
        var years = rows.getFirst().children().stream().map(cell -> {
            var match = YEAR.matcher(normalized(cell.text()));
            return match.matches() ? Integer.parseInt(match.group(1)) : 0;
        }).toList();
        for (var row : rows.subList(1, rows.size())) {
            var cells = row.children();
            for (int column = 0; column < Math.min(cells.size(), years.size()); column++) {
                int year = years.get(column);
                if (year == 0) continue;
                for (var link : cells.get(column).select("a[href]")) {
                    int month = monthNumber(normalized(link.text()).replaceFirst("^за ", ""));
                    if (month == 0) continue;
                    var source = BelstatWebsite.excelLink(PUBLICATIONS, link.attr("href"));
                    if (source != null) result.putIfAbsent(YearMonth.of(year, month), source);
                }
            }
        }
    }

    static int monthNumber(String label) {
        return MONTHS.indexOf(label) + 1;
    }
}
