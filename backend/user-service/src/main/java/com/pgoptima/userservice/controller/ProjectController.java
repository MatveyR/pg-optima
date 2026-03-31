package com.pgoptima.userservice.controller;

import com.pgoptima.shareddto.request.CreateProjectRequest;
import com.pgoptima.shareddto.request.SaveQueryRequest;
import com.pgoptima.shareddto.response.ProjectDTO;
import com.pgoptima.shareddto.response.SavedQueryDTO;
import com.pgoptima.userservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Manage projects and saved queries")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create new project")
    public ResponseEntity<ProjectDTO> createProject(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.createProject(userId, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long id) {
        projectService.deleteProject(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List user projects")
    public ResponseEntity<List<ProjectDTO>> listProjects(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(projectService.getUserProjects(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by id")
    public ResponseEntity<ProjectDTO> getProject(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(userId, id));
    }

    @PostMapping("/queries")
    @Operation(summary = "Save query to project")
    public ResponseEntity<SavedQueryDTO> saveQuery(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody SaveQueryRequest request) {
        return ResponseEntity.ok(projectService.saveQuery(userId, request));
    }

    @DeleteMapping("/queries/{id}")
    @Operation(summary = "Delete saved query")
    public ResponseEntity<Void> deleteQuery(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long id) {
        projectService.deleteQuery(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/queries")
    @Operation(summary = "Get all queries in project")
    public ResponseEntity<List<SavedQueryDTO>> getProjectQueries(@AuthenticationPrincipal Long userId,
                                                                 @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProjectQueries(userId, projectId));
    }
}