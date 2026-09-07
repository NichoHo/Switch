package com.switchpay.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class AdminAuthFilter extends OncePerRequestFilter {

    private final String adminApiKey;

    public AdminAuthFilter(@Value("${admin.api.key:admin-secret-key}") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String rawKey = bearerToken(request.getHeader("Authorization"));
        if (rawKey == null || !rawKey.equals(adminApiKey)) {
            writeUnauthorized(response, "Invalid or missing admin API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String key = authorizationHeader.substring("Bearer ".length()).trim();
        return key.isEmpty() ? null : key;
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"https://switch.dev/errors/unauthorized","title":"Unauthorized",\
                "status":401,"code":"unauthorized","detail":"%s"}"""
                .formatted(detail));
    }
}
