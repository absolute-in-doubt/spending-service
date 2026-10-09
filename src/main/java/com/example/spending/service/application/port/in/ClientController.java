package com.example.spending.service.application.port.in;

import com.example.spending.service.application.port.in.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.application.port.in.dto.ExceededTransactionResponseDto;
import com.example.spending.service.application.port.in.dto.ExpenseLimitResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inbound port interface for Client REST API.
 * Provides endpoints for managing expense limits and querying exceeded transactions.
 */
@Tag(name = "Client API", description = "External API for client requests: limits and exceeded transactions")
@RequestMapping("/api/client")
public interface ClientController {

    /**
     * Establishes a new monthly expense limit for a client account.
     * Timestamp is set automatically to the current instant.
     *
     * @param request the new limit parameters
     * @return the created expense limit
     */
    @Operation(summary = "Establish a new expense limit", description = "Creates a new limit in USD. Timestamp is recorded automatically.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Limit established successfully",
                    content = @Content(schema = @Schema(implementation = ExpenseLimitResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/limits")
    ResponseEntity<ExpenseLimitResponseDto> createLimit(@Valid @RequestBody CreateExpenseLimitRequestDto request);

    /**
     * Retrieves all historical limits established for the specified account.
     *
     * @param accountFrom 10-digit account number (query param)
     * @return list of expense limits
     */
    @Operation(summary = "Get all expense limits", description = "Returns all historical limits established for the client account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of limits retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ExpenseLimitResponseDto.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid account number",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/limits")
    ResponseEntity<List<ExpenseLimitResponseDto>> getLimits(
            @Parameter(description = "10-digit client account number")
            @RequestParam(name = "account_from", required = false) String accountFrom
    );

    /**
     * Retrieves all transactions that exceeded the monthly limit with active limit details.
     *
     * @param accountFrom 10-digit account number (query param)
     * @return list of exceeded transactions
     */
    @Operation(summary = "Get exceeded transactions", description = "Returns all transactions that exceeded the limit with limit establishment data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of exceeded transactions retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ExceededTransactionResponseDto.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid account number",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/transactions/exceeded")
    ResponseEntity<List<ExceededTransactionResponseDto>> getExceededTransactions(
            @Parameter(description = "10-digit client account number")
            @RequestParam(name = "account_from", required = false) String accountFrom
    );
}
