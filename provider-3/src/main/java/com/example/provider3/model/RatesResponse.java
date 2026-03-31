package com.example.provider3.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class RatesResponse {

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("base")
    private String base;

    @JsonProperty("rates")
    private Map<String, BigDecimal> rates;

    @JsonProperty("timestamp")
    private long timestamp;
}
