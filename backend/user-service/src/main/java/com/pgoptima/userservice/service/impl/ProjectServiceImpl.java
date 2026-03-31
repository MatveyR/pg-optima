package com.pgoptima.userservice.service.impl;

import com.pgoptima.shareddto.request.CreateProjectRequest;
import com.pgoptima.shareddto.request.SaveQueryRequest;
import com.pgoptima.shareddto.response.ProjectDTO;
import com.pgoptima.shareddto.response.SavedQueryDTO;
import com.pgoptima.userservice.entity.ProjectEntity;
import com.pgoptima.userservice.entity.SavedQueryEntity;
import com.pgoptima.userservice.exception.ResourceNotFoundException;
import com.pgoptima.userservice.repository.ProjectRepository;
import com.pgoptima.userservice.repository.SavedQueryRepository;
import com.pgoptima.userservice.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final SavedQueryRepository savedQueryRepository;

    @Override
    @Transactional
    public ProjectDTO createProject(Long userId, CreateProjectRequest request) {
        ProjectEntity entity = new ProjectEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setOwnerId(userId);
        ProjectEntity saved = projectRepository.save(entity);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        projectRepository.deleteByIdAndOwnerId(projectId, userId);
    }

    @Override
    public List<ProjectDTO> getUserProjects(Long userId) {
        return projectRepository.findByOwnerId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectDTO getProject(Long userId, Long projectId) {
        ProjectEntity entity = projectRepository.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public SavedQueryDTO saveQuery(Long userId, SaveQueryRequest request) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(request.getProjectId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found or access denied"));
        SavedQueryEntity query = new SavedQueryEntity();
        query.setName(request.getName());
        query.setSqlQuery(request.getSqlQuery());
        query.setDescription(request.getDescription());
        query.setProject(project);
        SavedQueryEntity saved = savedQueryRepository.save(query);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteQuery(Long userId, Long queryId) {
        SavedQueryEntity query = savedQueryRepository.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException("Query not found"));
        if (!query.getProject().getOwnerId().equals(userId)) {
            throw new ResourceNotFoundException("Query not found or access denied");
        }
        savedQueryRepository.delete(query);
    }

    @Override
    public List<SavedQueryDTO> getProjectQueries(Long userId, Long projectId) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return project.getSavedQueries().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ProjectDTO toDto(ProjectEntity entity) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setOwnerId(entity.getOwnerId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setSavedQueries(entity.getSavedQueries().stream().map(this::toDto).collect(Collectors.toList()));
        return dto;
    }

    private SavedQueryDTO toDto(SavedQueryEntity entity) {
        SavedQueryDTO dto = new SavedQueryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSqlQuery(entity.getSqlQuery());
        dto.setDescription(entity.getDescription());
        dto.setProjectId(entity.getProject().getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}