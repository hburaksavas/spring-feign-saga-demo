package io.github.hburaksavas.sagademo.demo.api;

import java.math.BigDecimal;

public record RemoteOperationRequest(String customerNo, BigDecimal amount) {
}
