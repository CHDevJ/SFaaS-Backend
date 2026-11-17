package com.sfaasproject.stf.measurement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates only the approved POST ingestion route, without opening an internal wildcard. */
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectProvider<InternalApiKeyVerifier> keys;
    private final AuthenticationEntryPoint entryPoint;

    public InternalApiKeyAuthenticationFilter(ObjectProvider<InternalApiKeyVerifier> keys,
            AuthenticationEntryPoint entryPoint) {
        this.keys = keys;
        this.entryPoint = entryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/internal/v1/measurements".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var key = keys.getIfAvailable();
        if (key == null || !key.matches(request.getHeader("X-Internal-Api-Key"))) {
            entryPoint.commence(request, response, new BadCredentialsException("Invalid internal key"));
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("NODE_RED", null,
                List.of(new SimpleGrantedAuthority("ROLE_NODE_RED"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
