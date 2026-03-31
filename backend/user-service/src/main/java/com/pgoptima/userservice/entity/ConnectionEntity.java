package com.pgoptima.userservice.entity;

import com.pgoptima.shareddto.enums.ConnectionStatus;
import com.pgoptima.shareddto.enums.DatabaseType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "connections")
@Data
public class ConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseType databaseType = DatabaseType.POSTGRESQL;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 5432;

    @Column(nullable = false)
    private String database;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String encryptedPassword; // хранится в зашифрованном виде

    private String sslMode = "disable";

    @Enumerated(EnumType.STRING)
    private ConnectionStatus status = ConnectionStatus.ACTIVE;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}