package com.ravi.gateway.infrastructure.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Transparently rewrites incoming requests for legacy /v3/api-docs paths under /services/{serviceName}/
 * to /openapi.json without defining a @RequestMapping ending in /v3/api-docs (which confuses SpringDoc's
 * path prefix detection).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SwaggerDocsRewriteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String uri = httpRequest.getRequestURI();
            if (uri.startsWith("/services/") && uri.endsWith("/v3/api-docs")) {
                String rewritten = uri.substring(0, uri.length() - "/v3/api-docs".length()) + "/openapi.json";
                httpRequest.getRequestDispatcher(rewritten).forward(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
