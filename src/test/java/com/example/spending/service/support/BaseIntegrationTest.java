package com.example.spending.service.support;

import com.example.spending.service.TestcontainersConfiguration;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExceededTransactionResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.RegisterTransactionRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.example.spending.service.infrastructure.jooq.Tables.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.Mockito.when;

/**
 * Base class for full-stack integration tests.
 * Spins up real PostgreSQL via Testcontainers, stubs TwelveData API via WireMock,
 * enables time control via @MockitoBean Clock, and isolates data between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class BaseIntegrationTest {

    @RegisterExtension
    public static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    protected int port;

    protected RestClient restClient;

    @Autowired
    protected DSLContext dsl;

    @MockitoBean
    protected Clock clock;

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.currency.twelvedata.base-url", wireMock::baseUrl);
        registry.add("app.currency.twelvedata.api-key", () -> "test-key");
        registry.add("app.currency.sync.run-on-startup", () -> "false");
        registry.add("app.currency.sync.enabled", () -> "false");
        registry.add("app.dlq.worker.enabled", () -> "true");
        registry.add("app.dlq.worker.fixed-delay", () -> "1h");
        registry.add("app.dlq.worker.retry-backoff", () -> "0ms");
    }

    @BeforeEach
    void setupBase() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        // Default clock time to current UTC instant
        setClockTime("2026-10-09T12:00:00Z");

        // Clean database tables to guarantee isolation
        dsl.deleteFrom(TRANSACTION_RECORDS).execute();
        dsl.deleteFrom(EXPENSE_LIMITS).execute();
        dsl.deleteFrom(ACCOUNT_MONTHLY_SPENDING).execute();
        dsl.deleteFrom(UNPROCESSED_TRANSACTIONS).execute();
        dsl.deleteFrom(EXCHANGE_RATES).execute();
    }

    protected void setClockTime(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        when(clock.instant()).thenReturn(instant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    protected void stubExchangeRate(String currencyPair, String date, BigDecimal close, BigDecimal prevClose) {
        String json = String.format("""
                {
                  "symbol": "%s",
                  "datetime": "%s",
                  "close": %s,
                  "previous_close": %s,
                  "status": "ok"
                }
                """,
                currencyPair,
                date != null ? date : "2022-01-01",
                close != null ? close.toString() : "null",
                prevClose != null ? prevClose.toString() : "null"
        );

        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo(currencyPair))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withBody(json)));
    }

    protected void stubExchangeRateError(String currencyPair, int status, String message) {
        String json = String.format("""
                {
                  "code": %d,
                  "message": "%s",
                  "status": "error"
                }
                """, status, message);

        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo(currencyPair))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(status)
                        .withBody(json)));
    }

    protected ResponseEntity<ExpenseLimitResponseDto> postLimit(String accountFrom, String category, BigDecimal limitSum) {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                accountFrom,
                category,
                limitSum,
                "USD"
        );

        return restClient.post()
                .uri("/api/client/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ExpenseLimitResponseDto.class);
    }

    protected ResponseEntity<TransactionResponseDto> postTransaction(
            String accountFrom,
            String accountTo,
            String currency,
            BigDecimal sum,
            String category,
            OffsetDateTime dateTime
    ) {
        RegisterTransactionRequestDto request = new RegisterTransactionRequestDto(
                accountFrom,
                accountTo,
                currency,
                sum,
                category,
                dateTime
        );

        return restClient.post()
                .uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TransactionResponseDto.class);
    }

    protected List<ExceededTransactionResponseDto> getExceededTransactions(String accountFrom) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/client/transactions/exceeded")
                        .queryParam("account_from", accountFrom)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExceededTransactionResponseDto>>() {});
    }

    protected List<ExpenseLimitResponseDto> getLimits(String accountFrom) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/client/limits")
                        .queryParam("account_from", accountFrom)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExpenseLimitResponseDto>>() {});
    }
}
