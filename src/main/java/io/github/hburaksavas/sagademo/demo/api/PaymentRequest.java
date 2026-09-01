package io.github.hburaksavas.sagademo.demo.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String customerNo,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        boolean failAfterRemoteCalls) {
}
