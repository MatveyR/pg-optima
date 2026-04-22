package com.pgoptima.userservice.controller;

import com.pgoptima.shareddto.request.CreateConnectionRequest;
import com.pgoptima.shareddto.request.UpdateConnectionRequest;
import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.userservice.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/connections")
@RequiredArgsConstructor
@Tag(name = "Connections", description = "Manage database connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    @PostMapping
    @Operation(summary = "Create new connection")
    public ResponseEntity<ConnectionDTO> createConnection(@AuthenticationPrincipal Long userId,
                                                          @Valid @RequestBody CreateConnectionRequest request) {
        return ResponseEntity.ok(connectionService.createConnection(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update connection")
    public ResponseEntity<ConnectionDTO> updateConnection(@AuthenticationPrincipal Long userId,
                                                          @PathVariable("id") Long id,
                                                          @Valid @RequestBody UpdateConnectionRequest request) {
        return ResponseEntity.ok(connectionService.updateConnection(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete connection")
    public ResponseEntity<Void> deleteConnection(@AuthenticationPrincipal Long userId,
                                                 @PathVariable("id") Long id) {
        connectionService.deleteConnection(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get connection by id")
    public ResponseEntity<ConnectionDTO> getConnection(@AuthenticationPrincipal Long userId,
                                                       @PathVariable("id") Long id) {
        return ResponseEntity.ok(connectionService.getConnection(userId, id));
    }

    @GetMapping
    @Operation(summary = "List user connections")
    public ResponseEntity<List<ConnectionDTO>> listConnections(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(connectionService.getUserConnections(userId));
    }

    @PostMapping("/test")
    @Operation(summary = "Test connection")
    public ResponseEntity<Map<String, Boolean>> testConnection(@Valid @RequestBody CreateConnectionRequest request) {
        boolean ok = connectionService.testConnection(request);
        return ResponseEntity.ok(Map.of("success", ok));
    }
}