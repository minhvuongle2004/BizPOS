package com.bizpos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Request không có Authorization header -> filter chain tiếp tục, không set Authentication")
    void doFilterInternal_noAuthorizationHeader_continuesChainWithoutAuth() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(tokenProvider);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Request có Authorization header không phải Bearer -> filter chain tiếp tục, không set Authentication")
    void doFilterInternal_nonBearerHeader_continuesChainWithoutAuth() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(tokenProvider);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Bearer token hợp lệ -> xác thực thành công và lưu vào SecurityContext")
    void doFilterInternal_validBearerToken_setsAuthentication() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(token)).thenReturn("admin");

        UserDetails userDetails = new User(
                "admin",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer token không hợp lệ (sai signature/malformed) -> không set Authentication")
    void doFilterInternal_invalidToken_continuesChainWithoutAuth() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
        verify(tokenProvider, never()).getUsernameFromToken(anyString());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Token gây ngoại lệ khi validate/parse -> filter chain vẫn tiếp tục và an toàn")
    void doFilterInternal_tokenThrowsException_continuesChainSafely() throws ServletException, IOException {
        String token = "error.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenThrow(new RuntimeException("Lỗi giải mã JWT"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("UserDetailsService ném UsernameNotFoundException -> filter chain vẫn tiếp tục an toàn")
    void doFilterInternal_userNotFound_continuesChainSafely() throws ServletException, IOException {
        String token = "valid.jwt.for.deleted.user";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(token)).thenReturn("deleted_user");
        when(userDetailsService.loadUserByUsername("deleted_user"))
                .thenThrow(new UsernameNotFoundException("User không tồn tại"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
