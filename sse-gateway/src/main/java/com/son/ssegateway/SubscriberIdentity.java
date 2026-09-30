package com.son.ssegateway;

import com.son.sseidentity.BrowserIdentity;
import io.netty.util.NetUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class SubscriberIdentity {
    record Key(String ip, String browser) { }
    private final BrowserIdentity cookies;
    private final Set<String> trustedProxies;

    SubscriberIdentity(@Value("${sse.identity.secret:}") String secret,
                       @Value("${sse.trusted-proxies:127.0.0.1,::1}") String trustedProxies,
                       Environment environment) {
        if (!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "none")))
            throw new IllegalArgumentException("Gateway requires server.forward-headers-strategy=none; trusted proxy IPs are checked explicitly");
        cookies = new BrowserIdentity(secret, environment.acceptsProfiles(Profiles.of("local & !prod")));
        this.trustedProxies = Arrays.stream(trustedProxies.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(SubscriberIdentity::normalize).collect(Collectors.toUnmodifiableSet());
    }

    Key resolve(ServerHttpRequest request) {
        var values = request.getCookies().get(BrowserIdentity.COOKIE);
        String browser = values != null && values.size() == 1 ? cookies.verify(values.get(0).getValue()) : null;
        if (browser == null) throw new ConnectionAdmission.Rejected("browser_identity_required");
        var remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) throw new ConnectionAdmission.Rejected("invalid_client_ip");
        String ip = normalize(remote.getAddress().getHostAddress());
        // Nginx must overwrite X-Real-IP. Never accept client-supplied X-Forwarded-For.
        if (trustedProxies.contains(ip) && request.getHeaders().containsHeader("X-Real-IP")) {
            var headers = request.getHeaders().get("X-Real-IP");
            if (headers.size() != 1) throw new ConnectionAdmission.Rejected("invalid_client_ip");
            try { ip = normalize(headers.get(0)); }
            catch (IllegalArgumentException e) { throw new ConnectionAdmission.Rejected("invalid_client_ip"); }
        }
        return new Key(ip, browser);
    }

    private static String normalize(String value) {
        // Literal parsing only: do not perform DNS on a request header.
        byte[] bytes = NetUtil.createByteArrayFromIpAddressString(value);
        if (bytes == null) throw new IllegalArgumentException("Expected literal IP address");
        try { return java.net.InetAddress.getByAddress(bytes).getHostAddress(); }
        catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("Invalid IP address"); }
    }
}
