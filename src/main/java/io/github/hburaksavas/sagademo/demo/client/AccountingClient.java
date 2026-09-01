package io.github.hburaksavas.sagademo.demo.client;

import io.github.hburaksavas.sagademo.demo.api.RemoteOperationRequest;
import io.github.hburaksavas.sagademo.demo.api.RemoteOperationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "accountingClient", url = "${demo.remote-base-url}")
public interface AccountingClient {

    @PostMapping("/demo/accounting/entries")
    RemoteOperationResponse createEntry(@RequestBody RemoteOperationRequest request);
}
