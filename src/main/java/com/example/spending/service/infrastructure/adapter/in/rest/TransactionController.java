package com.example.spending.service.infrastructure.adapter.in.rest;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.RegisterTransactionRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Inbound port interface for Transaction Reception REST API (Bank integration).
 */
@Tag(name = "Transaction API", description = "Bank integration API for receiving and processing customer transactions")
@RequestMapping("/api/transactions")
public interface TransactionController {

    /**
     * Receives and processes a transaction operation in real-time.
     * Evaluates monthly limits and sets the limit_exceeded flag.
     *
     * @param request the incoming transaction details
     * @return transaction response with limit exceedance flag
     */
    @Operation(summary = "Register a transaction operation", description = "Receives a transaction in real-time and evaluates monthly USD limits")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction processed successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Exchange rate unavailable, transaction parked in DLQ",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    ResponseEntity<TransactionResponseDto> receiveTransaction(@Valid @RequestBody RegisterTransactionRequestDto request);
}
