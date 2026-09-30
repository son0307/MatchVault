package com.son.soccerStreaming.live.controller;

import com.son.sseidentity.BrowserIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SseIdentityController {
    private final BrowserIdentity identity;
    private final boolean secure;

    public SseIdentityController(@Value("${sse.identity.secret:}") String secret,
                                 @Value("${live.sse.transport:mvc}") String transport, Environment environment) {
        boolean local = environment.acceptsProfiles(Profiles.of("local & !prod"));
        secure = !local;
        // Legacy MVC transport has no Gateway cookie requirement.
        identity = "redis".equals(transport) ? new BrowserIdentity(secret, local) : null;
    }

    @GetMapping("/api/v1/live/identity")
    public ResponseEntity<Void> ensure(@CookieValue(name = BrowserIdentity.COOKIE, required = false) String cookie) {
        var response = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        if (identity != null && identity.verify(cookie) == null) {
            response.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(BrowserIdentity.COOKIE, identity.issue())
                    .httpOnly(true).secure(secure).sameSite("Lax").path(BrowserIdentity.PATH)
                    .maxAge(BrowserIdentity.MAX_AGE_SECONDS).build().toString());
        }
        return response.build();
    }
}
