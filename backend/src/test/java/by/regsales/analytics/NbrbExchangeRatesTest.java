package by.regsales.analytics;

import by.regsales.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NbrbExchangeRatesTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
    private static final String URL = "https://api.nbrb.by/exrates/rates?ondate=2026-01-15&periodicity=0";

    private MockRestServiceServer server;
    private NbrbExchangeRates rates;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.nbrb.by");
        server = MockRestServiceServer.bindTo(builder).build();
        rates = new NbrbExchangeRates(builder.build());
    }

    @Test
    void returnsOnlySelectedCurrenciesAndPreservesOfficialUnits() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                [{"Cur_ID":456,"Date":"2026-01-15T00:00:00","Cur_Abbreviation":"RUB",
                  "Cur_Scale":100,"Cur_Name":"Российских рублей","Cur_OfficialRate":3.6254},
                 {"Date":"2026-01-15T00:00:00","Cur_Abbreviation":"USD",
                  "Cur_Scale":1,"Cur_Name":"Доллар США","Cur_OfficialRate":3.1},
                 {"Date":"2026-01-15T00:00:00","Cur_Abbreviation":"GBP",
                  "Cur_Scale":1,"Cur_Name":"Фунт стерлингов","Cur_OfficialRate":4.1},
                 {"Date":"2026-01-15T00:00:00","Cur_Abbreviation":"CNY",
                  "Cur_Scale":10,"Cur_Name":"Китайских юаней","Cur_OfficialRate":4.3},
                 {"Date":"2026-01-15T00:00:00","Cur_Abbreviation":"EUR",
                  "Cur_Scale":1,"Cur_Name":"Евро","Cur_OfficialRate":3.5}]
                """, MediaType.APPLICATION_JSON));

        var result = rates.onDate(DATE);

        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.rates()).extracting(NbrbExchangeRates.ExchangeRate::currencyCode)
                .containsExactly("CNY", "EUR", "RUB", "USD");
        assertThat(result.rates()).filteredOn(rate -> rate.currencyCode().equals("RUB")).singleElement().satisfies(rate -> {
            assertThat(rate.units()).isEqualTo(100);
            assertThat(rate.rateByn()).isEqualByComparingTo("3.6254");
        });
        server.verify();
    }

    @Test
    void rejectsDatesOutside2026BeforeCallingNbrb() {
        assertThatThrownBy(() -> rates.onDate(LocalDate.of(2025, 12, 31)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        server.verify();
    }

    @Test
    void reportsUpstreamFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> rates.onDate(DATE))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("NBRB_RATES_UNAVAILABLE"));
        server.verify();
    }
}
