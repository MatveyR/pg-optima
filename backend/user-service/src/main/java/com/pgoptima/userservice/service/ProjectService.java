package com.pgoptima.userservice.service;

import com.pgoptima.shareddto.request.CreateProjectRequest;
import com.pgoptima.shareddto.request.SaveQueryRequest;
import com.pgoptima.shareddto.response.ProjectDTO;
import com.pgoptima.shareddto.response.SavedQueryDTO;

import java.util.List;

public interface ProjectService {
    ProjectDTO createProject(Long userId, CreateProjectRequest request);
    void deleteProject(Long userId, Long projectId);
    List<ProjectDTO> getUserProjects(Long userId);
    ProjectDTO getProject(Long userId, Long projectId);
    SavedQueryDTO saveQuery(Long userId, SaveQueryRequest request);
    void deleteQuery(Long userId, Long queryId);
    List<SavedQueryDTO> getProjectQueries(Long userId, Long projectId);
}