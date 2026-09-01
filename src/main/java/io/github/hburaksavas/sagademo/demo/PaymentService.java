package io.github.hburaksavas.sagademo.demo;

import io.github.hburaksavas.sagademo.demo.api.PaymentRequest;
import io.github.hburaksavas.sagademo.demo.api.RemoteOperationRequest;
import io.github.hburaksavas.sagademo.demo.client.AccountingClient;
import io.github.hburaksavas.sagademo.demo.client.LimitClient;
import io.github.hburaksavas.sagademo.saga.SagaTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final LimitClient limitClient;
    private final AccountingClient accountingClient;
    private final JdbcTemplate jdbc;

    public PaymentService(LimitClient limitClient,
                          AccountingClient accountingClient,
                          JdbcTemplate jdbc) {
        this.limitClient = limitClient;
        this.accountingClient = accountingClient;
        this.jdbc = jdbc;
    }

    @Transactional
    @SagaTransactional(type = "INSTALLMENT_PAYMENT")
    public String pay(PaymentRequest request) {
        RemoteOperationRequest remoteRequest =
                new RemoteOperationRequest(request.customerNo(), request.amount());

        limitClient.reserve(remoteRequest);
        accountingClient.createEntry(remoteRequest);

        String paymentId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO DEMO_PAYMENT
                  (ID, CUSTOMER_NO, AMOUNT, STATUS, CREATED_AT)
                VALUES (?, ?, ?, 'COMPLETED', SYSTIMESTAMP)
                """, paymentId, request.customerNo(), request.amount());

        if (request.failAfterRemoteCalls()) {
            throw new IllegalStateException("Demo failure after successful remote calls");
        }
        return paymentId;
    }
}
