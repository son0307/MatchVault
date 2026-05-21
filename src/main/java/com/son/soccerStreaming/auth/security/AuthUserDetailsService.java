package com.son.soccerStreaming.auth.security;

import com.son.soccerStreaming.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByEmail(username)
                .map(AuthUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
