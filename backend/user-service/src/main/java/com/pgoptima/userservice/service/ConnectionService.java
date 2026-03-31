package com.pgoptima.userservice.service;

import com.pgoptima.shareddto.request.CreateConnectionRequest;
import com.pgoptima.shareddto.request.UpdateConnectionRequest;
import com.pgoptima.shareddto.response.ConnectionDTO;

import java.util.List;

public interface ConnectionService {
    ConnectionDTO createConnection(Long userId, CreateConnectionRequest request);
    ConnectionDTO updateConnection(Long userId, Long connectionId, UpdateConnectionRequest request);
    void deleteConnection(Long userId, Long connectionId);
    ConnectionDTO getConnection(Long userId, Long connectionId);
    List<ConnectionDTO> getUserConnections(Long userId);
    boolean testConnection(CreateConnectionRequest request);
    // Внутренний метод для analytics-service
    ConnectionDTO getConnectionForInternalUse(Long connectionId);
}