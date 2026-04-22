package com.pgoptima.analyticsservice.client;

import com.pgoptima.shareddto.response.ConnectionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8082}")
public interface UserServiceClient {

    @GetMapping("/internal/connections/{id}")
    ConnectionDTO getConnectionById(@PathVariable("id") Long id,
                                    @RequestHeader(value = "Authorization", required = false) String authHeader);
}