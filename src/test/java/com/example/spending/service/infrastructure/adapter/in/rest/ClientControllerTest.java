package com.example.spending.service.infrastructure.adapter.in.rest;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExceededTransactionResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;
import com.example.spending.service.application.port.in.ExpenseLimitService;
import com.example.spending.service.application.port.in.TransactionOperationService;
import com.example.spending.service.infrastructure.adapter.in.rest.impl.ClientControllerImpl;
import com.example.spending.service.infrastructure.adapter.in.rest.impl.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    @Mock
    private ExpenseLimitService expenseLimitService;

    @Mock
    private TransactionOperationService transactionOperationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClientControllerImpl controller = new ClientControllerImpl(expenseLimitService, transactionOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should successfully establish a new expense limit and return 201 Created")
    void shouldCreateLimitSuccessfully() throws Exception {
        OffsetDateTime dt = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);
        ExpenseLimitResponseDto responseDto = new ExpenseLimitResponseDto(
                1L,
                "0000000123",
                "product",
                new BigDecimal("2000.00"),
                "USD",
                dt
        );

        when(expenseLimitService.createLimit(any(CreateExpenseLimitRequestDto.class)))
                .thenReturn(responseDto);

        String json = """
                {
                    "account_from": "0000000123",
                    "expense_category": "product",
                    "limit_sum": 2000.00,
                    "limit_currency_shortname": "USD"
                }
                """;

        mockMvc.perform(post("/api/client/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.account_from").value("0000000123"))
                .andExpect(jsonPath("$.limit_sum").value(2000.00));
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when creating limit with invalid parameters")
    void shouldReturnBadRequestWhenLimitValidationFails() throws Exception {
        String invalidJson = """
                {
                    "account_from": "invalid",
                    "expense_category": "",
                    "limit_sum": -100.00
                }
                """;

        mockMvc.perform(post("/api/client/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalid_params.account_from").exists())
                .andExpect(jsonPath("$.invalid_params.limit_sum").exists());
    }

    @Test
    @DisplayName("Should retrieve historical limits for client account via query param")
    void shouldGetLimitsSuccessfully() throws Exception {
        OffsetDateTime dt = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);
        ExpenseLimitResponseDto limitDto = new ExpenseLimitResponseDto(
                1L,
                "0000000123",
                "product",
                new BigDecimal("1000.00"),
                "USD",
                dt
        );

        when(expenseLimitService.getLimits("0000000123"))
                .thenReturn(List.of(limitDto));

        mockMvc.perform(get("/api/client/limits")
                        .param("account_from", "0000000123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].limit_sum").value(1000.00));
    }

    @Test
    @DisplayName("Should retrieve exceeded transactions for client account (Requirement 6)")
    void shouldGetExceededTransactionsSuccessfully() throws Exception {
        OffsetDateTime txDt = OffsetDateTime.of(2022, 1, 3, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime limitDt = OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        ExceededTransactionResponseDto exceededDto = new ExceededTransactionResponseDto(
                "0000000123",
                "9999999999",
                "USD",
                new BigDecimal("600.00"),
                "product",
                txDt,
                new BigDecimal("1000.00"),
                limitDt,
                "USD"
        );

        when(transactionOperationService.getExceededTransactions("0000000123"))
                .thenReturn(List.of(exceededDto));

        mockMvc.perform(get("/api/client/transactions/exceeded")
                        .param("account_from", "0000000123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account_from").value("0000000123"))
                .andExpect(jsonPath("$[0].sum").value(600.00))
                .andExpect(jsonPath("$[0].limit_sum").value(1000.00))
                .andExpect(jsonPath("$[0].limit_currency_shortname").value("USD"));
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when account_from is missing")
    void shouldReturnBadRequestWhenAccountFromIsMissing() throws Exception {
        mockMvc.perform(get("/api/client/limits"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Query parameter 'account_from' is required"));
    }
}
