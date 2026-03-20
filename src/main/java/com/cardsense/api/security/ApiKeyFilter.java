package com.cardsense.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String MOCK_API_KEY = "dummy-api-key"; // In real env, from config/secrets

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip security for health check
        if (isHealthEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (isAuthenticated(apiKey)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid or missing API Key");
        }
    }

    private boolean isAuthenticated(String apiKey) {
        // Simple equality check for MVP
        return MOCK_API_KEY.equals(apiKey);
    }

    private boolean isHealthEndpoint(String requestUri) {
        return "/health".equals(requestUri) || requestUri.startsWith("/v1/health");
    }
}
