package com.pgoptima.shareddto.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private Instant createdAt;
    private List<SavedQueryDTO> savedQueries;
}