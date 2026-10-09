package com.example.spending.service.infrastructure.adapter.in.rest;

import com.example.spending.service.application.exception.TransactionParkedException;
import com.example.spending.service.application.port.in.dto.RegisterTransactionRequestDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;
import com.example.spending.service.application.service.TransactionOperationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionOperationService transactionOperationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TransactionControllerImpl controller = new TransactionControllerImpl(transactionOperationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should successfully receive and process valid transaction operation")
    void shouldReceiveTransactionSuccessfully() throws Exception {
        OffsetDateTime dt = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);
        TransactionResponseDto responseDto = new TransactionResponseDto(
                1L,
                "0000000123",
                "9999999999",
                "USD",
                new BigDecimal("150.00"),
                "product",
                dt,
                false
        );

        when(transactionOperationService.saveTransactionOperation(any(RegisterTransactionRequestDto.class)))
                .thenReturn(responseDto);

        String json = """
                {
                    "account_from": "0000000123",
                    "account_to": "9999999999",
                    "currency_shortname": "USD",
                    "sum": 150.00,
                    "expense_category": "product",
                    "datetime": "2026-10-09T12:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.account_from").value("0000000123"))
                .andExpect(jsonPath("$.limit_exceeded").value(false));
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when request fails bean validation")
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        String invalidJson = """
                {
                    "account_from": "123",
                    "account_to": "",
                    "currency_shortname": "TOOLONG",
                    "sum": -50.00,
                    "expense_category": "",
                    "datetime": null
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalid_params.account_from").exists())
                .andExpect(jsonPath("$.invalid_params.sum").exists());
    }

    @Test
    @DisplayName("Should return 503 ProblemDetail when transaction is parked in DLQ")
    void shouldReturnServiceUnavailableWhenTransactionParked() throws Exception {
        when(transactionOperationService.saveTransactionOperation(any(RegisterTransactionRequestDto.class)))
                .thenThrow(new TransactionParkedException("Exchange rate unavailable for KZT/USD. Parked in DLQ.", 42L));

        String json = """
                {
                    "account_from": "0000000123",
                    "account_to": "9999999999",
                    "currency_shortname": "KZT",
                    "sum": 50000.00,
                    "expense_category": "product",
                    "datetime": "2026-10-09T12:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Transaction Parked"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.unprocessed_transaction_id").value(42));
    }
}
