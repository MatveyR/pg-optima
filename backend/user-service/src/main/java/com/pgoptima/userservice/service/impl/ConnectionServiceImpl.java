package com.pgoptima.userservice.service.impl;

import com.pgoptima.shareddto.enums.ConnectionStatus;
import com.pgoptima.shareddto.request.CreateConnectionRequest;
import com.pgoptima.shareddto.request.UpdateConnectionRequest;
import com.pgoptima.shareddto.response.ConnectionDTO;
import com.pgoptima.userservice.crypto.EncryptionService;
import com.pgoptima.userservice.entity.ConnectionEntity;
import com.pgoptima.userservice.exception.ResourceNotFoundException;
import com.pgoptima.userservice.repository.ConnectionRepository;
import com.pgoptima.userservice.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final EncryptionService encryptionService;

    @Override
    @Transactional
    public ConnectionDTO createConnection(Long userId, CreateConnectionRequest request) {
        ConnectionEntity entity = new ConnectionEntity();
        entity.setName(request.getName());
        entity.setDatabaseType(request.getDatabaseType());
        entity.setHost(request.getHost());
        entity.setPort(request.getPort());
        entity.setDatabase(request.getDatabase());
        entity.setUsername(request.getUsername());
        entity.setEncryptedPassword(encryptionService.encrypt(request.getPassword()));
        entity.setSslMode(request.getSslMode());
        entity.setOwnerId(userId);
        entity.setStatus(ConnectionStatus.ACTIVE);

        ConnectionEntity saved = connectionRepository.save(entity);
        return toDto(saved, false);
    }

    @Override
    @Transactional
    public ConnectionDTO updateConnection(Long userId, Long connectionId, UpdateConnectionRequest request) {
        ConnectionEntity entity = connectionRepository.findByIdAndOwnerId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDatabaseType() != null) entity.setDatabaseType(request.getDatabaseType());
        if (request.getHost() != null) entity.setHost(request.getHost());
        if (request.getPort() != null) entity.setPort(request.getPort());
        if (request.getDatabase() != null) entity.setDatabase(request.getDatabase());
        if (request.getUsername() != null) entity.setUsername(request.getUsername());
        if (request.getPassword() != null) entity.setEncryptedPassword(encryptionService.encrypt(request.getPassword()));
        if (request.getSslMode() != null) entity.setSslMode(request.getSslMode());

        ConnectionEntity saved = connectionRepository.save(entity);
        return toDto(saved, false);
    }

    @Override
    @Transactional
    public void deleteConnection(Long userId, Long connectionId) {
        connectionRepository.deleteByIdAndOwnerId(connectionId, userId);
    }

    @Override
    public ConnectionDTO getConnection(Long userId, Long connectionId) {
        ConnectionEntity entity = connectionRepository.findByIdAndOwnerId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        return toDto(entity, false);
    }

    @Override
    public List<ConnectionDTO> getUserConnections(Long userId) {
        return connectionRepository.findByOwnerId(userId).stream()
                .map(e -> toDto(e, false))
                .collect(Collectors.toList());
    }

    @Override
    public boolean testConnection(CreateConnectionRequest request) {
        String url = request.getDatabaseType().buildJdbcUrl(request.getHost(), request.getPort(), request.getDatabase());
        try (Connection conn = DriverManager.getConnection(url, request.getUsername(), request.getPassword())) {
            return conn.isValid(2);
        } catch (SQLException e) {
            log.warn("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ConnectionDTO getConnectionForInternalUse(Long connectionId) {
        ConnectionEntity entity = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        return toDto(entity, true);
    }

    private ConnectionDTO toDto(ConnectionEntity entity, boolean includePassword) {
        ConnectionDTO dto = new ConnectionDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDatabaseType(entity.getDatabaseType());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setDatabase(entity.getDatabase());
        dto.setUsername(entity.getUsername());
        dto.setSslMode(entity.getSslMode());
        dto.setStatus(entity.getStatus());
        dto.setOwnerId(entity.getOwnerId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (includePassword) {
            dto.setPassword(encryptionService.decrypt(entity.getEncryptedPassword()));
        }
        return dto;
    }
}