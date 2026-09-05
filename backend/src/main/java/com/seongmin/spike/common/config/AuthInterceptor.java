package com.seongmin.spike.common.config;

import com.seongmin.spike.common.exception.ApiException;
import com.seongmin.spike.project.domain.Project;
import com.seongmin.spike.project.domain.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 수집 API는 X-API-Key(프로젝트 키), 나머지 /api/** 는 X-Admin-Token. */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    public static final String PROJECT_ATTR = "project";
    private final ProjectRepository projects;
    private final SpikeProperties props;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;
        if ("/api/errors".equals(req.getRequestURI()) && "POST".equals(req.getMethod())) {
            String key = req.getHeader("X-API-Key");
            Project project = key == null ? null : projects.findByApiKey(key).orElse(null);
            if (project == null) throw ApiException.unauthorized("INVALID_API_KEY", "X-API-Key header missing or unknown");
            req.setAttribute(PROJECT_ATTR, project);
            return true;
        }
        if (!props.adminToken().equals(req.getHeader("X-Admin-Token"))) {
            throw ApiException.unauthorized("INVALID_ADMIN_TOKEN", "X-Admin-Token header missing or invalid");
        }
        return true;
    }
}
