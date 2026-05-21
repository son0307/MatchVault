package com.son.soccerStreaming.auth.security;

import com.son.soccerStreaming.auth.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthUserDetails implements UserDetails {

    private final AppUser user;

    public AuthUserDetails(AppUser user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getNickname() {
        return user.getNickname();
    }

    public String getRole() {
        return user.roleOrDefault().name();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.roleOrDefault().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
