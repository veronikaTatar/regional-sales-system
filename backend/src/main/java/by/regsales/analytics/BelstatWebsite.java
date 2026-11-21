package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

final class BelstatWebsite {
    private static final Set<String> HOSTS = Set.of("belstat.gov.by", "www.belstat.gov.by");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private BelstatWebsite() {}

    static byte[] fetch(URI url) {
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

    static URI officialLink(URI base, String path) {
        if (path == null || path.isBlank()) return null;
        try {
            var url = base.resolve(path.strip().replace(" ", "%20")).normalize();
            var host = url.getHost();
            return "https".equalsIgnoreCase(url.getScheme()) && host != null && HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    && url.getUserInfo() == null && (url.getPort() == -1 || url.getPort() == 443) ? url : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static URI workbookLink(URI page, String href) {
        var target = excelLink(page, href);
        return target != null && target.getPath().toLowerCase(Locale.ROOT).endsWith(".xlsx") ? target : null;
    }

    static URI excelLink(URI page, String href) {
        var target = officialLink(page, href);
        if (target == null) return null;
        if (target.getPath().startsWith("/viewer/") && target.getQuery() != null && target.getQuery().startsWith("doclink=")) {
            target = officialLink(page, target.getQuery().substring("doclink=".length()));
        }
        if (target == null) return null;
        var path = target.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".xls") || path.endsWith(".xlsx") ? target : null;
    }

    static String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "BELSTAT_DOWNLOAD_FAILED", message);
    }
}
