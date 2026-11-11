package com.example.studyassistant.security;

import com.example.studyassistant.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AccountAccessConfig implements WebMvcConfigurer {
    private final UserRepository users;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                var session = request.getSession(false);
                if (session == null || !(session.getAttribute("userId") instanceof Long userId)) return true;

                var account = users.findById(userId);
                if (account.isEmpty()) {
                    session.invalidate();
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                }

                boolean admin = "ADMIN".equals(account.get().getRole());
                if (path.startsWith("/api/")) {
                    if (admin && !adminApi(path, request.getMethod())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng dành cho tài khoản người dùng");
                    }
                    return true;
                }
                if (path.matches("/(?:index|login|register|errors)\\.html")) return true;
                if (path.matches("/[^/]+\\.html") && admin != path.startsWith("/admin")) {
                    response.sendRedirect(request.getContextPath() + (admin ? "/admin.html" : "/dashboard.html"));
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/api/**", "/*.html")
          .excludePathPatterns("/api/auth/**", "/api/client-errors", "/api/payments/webhook/verify", "/errors.html")
          .order(-100);
    }

    private boolean adminApi(String path, String method) {
        return path.startsWith("/api/admin/")
                || path.equals("/api/payments/pending")
                || path.matches("/api/payments/[0-9]+/(approve|reject)")
                || path.equals("/api/plans/admin")
                || (path.equals("/api/plans") && method.equals("POST"))
                || (path.matches("/api/plans/[0-9]+") && method.equals("DELETE"));
    }
}
