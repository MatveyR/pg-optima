package com.pgoptima.shareddto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class SavedQueryDTO {
    private Long id;
    private String name;
    private String sqlQuery;
    private String description;
    private Long projectId;
    private Instant createdAt;
    private Instant updatedAt;
}