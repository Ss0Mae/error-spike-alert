package com.seongmin.spike.project.api;

import com.seongmin.spike.common.response.ApiResponse;
import com.seongmin.spike.project.domain.ProjectRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectRepository projects;

    public record ProjectResponse(Long id, String name, String apiKey, Instant createdAt) {}

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list() {
        return ApiResponse.ok(projects.findAll().stream()
                .map(p -> new ProjectResponse(p.getId(), p.getName(), p.getApiKey(), p.getCreatedAt())).toList());
    }
}
