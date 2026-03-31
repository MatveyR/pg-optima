package com.pgoptima.userservice.controller;

import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.userservice.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Internal API", description = "For service-to-service communication")
public class InternalController {

    private final ConnectionService connectionService;

    @GetMapping("/connections/{id}")
    @Operation(summary = "Get connection details with password (for analytics-service)")
    public ConnectionDTO getConnectionForInternal(@PathVariable Long id) {
        // Здесь можно добавить проверку внутреннего токена, но для простоты пока открыто
        return connectionService.getConnectionForInternalUse(id);
    }
}