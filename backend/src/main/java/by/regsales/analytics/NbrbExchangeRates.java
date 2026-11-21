package by.regsales.analytics;

import by.regsales.common.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class NbrbExchangeRates {
    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final Set<String> SELECTED_CURRENCIES = Set.of("USD", "EUR", "CNY", "RUB");
    private final RestClient client;

    @Autowired
    public NbrbExchangeRates(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        client = builder.baseUrl("https://api.nbrb.by").requestFactory(factory).build();
    }

    NbrbExchangeRates(RestClient client) {
        this.client = client;
    }

    public DailyRates onDate(LocalDate date) {
        if (date.getYear() != 2026 || date.isAfter(LocalDate.now(MINSK))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NBRB_RATE_DATE", "Выберите прошедшую или сегодняшнюю дату в 2026 году.");
        }

        RemoteRate[] rates;
        try {
            rates = client.get().uri(uri -> uri.path("/exrates/rates")
                            .queryParam("ondate", date).queryParam("periodicity", 0).build())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(RemoteRate[].class);
        } catch (RestClientException error) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "NBRB_RATES_UNAVAILABLE",
                    "Не удалось получить курсы Нацбанка. Повторите попытку позже.");
        }

        if (rates == null || Arrays.stream(rates).anyMatch(rate -> !valid(rate, date))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "NBRB_RATES_INVALID", "Нацбанк вернул некорректные курсы за выбранную дату.");
        }

        var currencies = Arrays.stream(rates)
                .filter(rate -> SELECTED_CURRENCIES.contains(rate.code()))
                .map(rate -> new ExchangeRate(rate.code(), rate.name(), rate.scale(), rate.rate()))
                .sorted(Comparator.comparing(ExchangeRate::currencyCode))
                .toList();
        return new DailyRates(date, currencies);
    }

    private static boolean valid(RemoteRate rate, LocalDate date) {
        return rate != null && rate.date() != null && rate.date().toLocalDate().equals(date)
                && rate.code() != null && rate.code().matches("[A-Z]{3}")
                && rate.name() != null && !rate.name().isBlank() && rate.scale() > 0
                && rate.rate() != null && rate.rate().signum() > 0;
    }

    public record DailyRates(LocalDate date, List<ExchangeRate> rates) {}

    public record ExchangeRate(String currencyCode, String currencyName, int units, BigDecimal rateByn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteRate(@JsonProperty("Date") LocalDateTime date,
                              @JsonProperty("Cur_Abbreviation") String code,
                              @JsonProperty("Cur_Name") String name,
                              @JsonProperty("Cur_Scale") int scale,
                              @JsonProperty("Cur_OfficialRate") BigDecimal rate) {}
}
