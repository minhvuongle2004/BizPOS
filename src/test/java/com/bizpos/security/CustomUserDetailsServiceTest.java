package com.bizpos.security;

import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho CustomUserDetailsService")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("Load user thành công khi user tồn tại với role ADMIN")
    void loadUserByUsername_userExistsAdmin_returnsUserDetails() {
        User adminUser = User.builder()
                .id(1L)
                .username("admin")
                .password("$2a$10$hashedpassword")
                .role(Role.ADMIN)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        assertNotNull(userDetails);
        assertEquals("admin", userDetails.getUsername());
        assertEquals("$2a$10$hashedpassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertEquals(1, userDetails.getAuthorities().size());
        verify(userRepository, times(1)).findByUsername("admin");
    }

    @Test
    @DisplayName("Load user thành công khi user tồn tại với role STAFF")
    void loadUserByUsername_userExistsStaff_returnsUserDetails() {
        User staffUser = User.builder()
                .id(2L)
                .username("staff")
                .password("$2a$10$hashedpasswordstaff")
                .role(Role.STAFF)
                .build();

        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(staffUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("staff");

        assertNotNull(userDetails);
        assertEquals("staff", userDetails.getUsername());
        assertEquals("$2a$10$hashedpasswordstaff", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STAFF")));
        assertEquals(1, userDetails.getAuthorities().size());
        verify(userRepository, times(1)).findByUsername("staff");
    }

    @Test
    @DisplayName("Ném UsernameNotFoundException khi user không tồn tại trong hệ thống")
    void loadUserByUsername_userNotFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown_user")
        );

        assertTrue(exception.getMessage().contains("unknown_user"));
        verify(userRepository, times(1)).findByUsername("unknown_user");
    }
}
