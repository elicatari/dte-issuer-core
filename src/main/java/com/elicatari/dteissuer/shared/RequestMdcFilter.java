package com.elicatari.dteissuer.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlación {@code X-Request-Id} en MDC y en la respuesta. Va por delante de
 * Spring Security para que un 401 también lleve el header. El {@code tenant_id}
 * lo aporta {@link TenantContextFilter} cuando el JWT es válido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = RequestMdc.resolve(request.getHeader(RequestMdc.HEADER));
        RequestMdc.putRequestId(requestId);
        response.setHeader(RequestMdc.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestMdc.clear();
        }
    }
}