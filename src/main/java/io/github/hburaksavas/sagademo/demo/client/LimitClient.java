package io.github.hburaksavas.sagademo.demo.client;

import io.github.hburaksavas.sagademo.demo.api.RemoteOperationRequest;
import io.github.hburaksavas.sagademo.demo.api.RemoteOperationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "limitClient", url = "${demo.remote-base-url}")
public interface LimitClient {

    @PostMapping("/demo/limits/reservations")
    RemoteOperationResponse reserve(@RequestBody RemoteOperationRequest request);
}
