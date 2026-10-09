package com.example.spending.service.infrastructure.adapter.out.client.twelvedata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataQuoteResponse(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("name") String name,
        @JsonProperty("datetime") String datetime,
        @JsonProperty("close") BigDecimal close,
        @JsonProperty("previous_close") BigDecimal previousClose,
        @JsonProperty("rate") BigDecimal rate,
        @JsonProperty("status") String status,
        @JsonProperty("code") Integer code,
        @JsonProperty("message") String message
) {
    public BigDecimal effectiveCloseRate() {
        return close != null ? close : rate;
    }

    public BigDecimal effectiveRate() {
        if (close != null) {
            return close;
        }
        if (rate != null) {
            return rate;
        }
        return previousClose;
    }

    public boolean hasAnyRate() {
        return effectiveRate() != null;
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(status) || (code != null && code >= 400);
    }
}
