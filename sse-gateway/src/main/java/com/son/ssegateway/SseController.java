package com.son.ssegateway;

import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class SseController {
    private final SubscriberHub hub;
    private final ConnectionAdmission admission;
    private final SubscriberIdentity identity;
    public SseController(SubscriberHub hub, ConnectionAdmission admission, SubscriberIdentity identity) {
        this.hub = hub; this.admission = admission; this.identity = identity;
    }
    @GetMapping(value = "/api/v1/live/stream/fixtures/{fixtureId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<Void> stream(@PathVariable long fixtureId,
                            @RequestHeader(value = "X-Local-SSE-Client", required = false) String marker,
                            ServerHttpRequest request, ServerHttpResponse response) {
        if (fixtureId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // Reserve before SSE headers; release on completion, failure and cancellation.
        return Mono.using(() -> admission.acquire(identity.resolve(request)), permit -> writeStream(fixtureId, marker, response),
                ConnectionAdmission.Permit::close)
                .onErrorResume(ConnectionAdmission.Rejected.class, rejected -> {
                    boolean rateLimited = rejected.reason.equals("rate_limit");
                    HttpStatus status = switch (rejected.reason) {
                        case "browser_identity_required" -> HttpStatus.UNAUTHORIZED;
                        case "invalid_client_ip" -> HttpStatus.BAD_REQUEST;
                        case "connection_limit" -> HttpStatus.SERVICE_UNAVAILABLE;
                        default -> HttpStatus.TOO_MANY_REQUESTS;
                    };
                    response.setStatusCode(status);
                    response.getHeaders().set("Retry-After", rateLimited ? "1" : "5");
                    response.getHeaders().set("X-SSE-Rejection", rejected.reason);
                    response.getHeaders().setCacheControl("no-store");
                    return response.setComplete();
                });
    }

    private Mono<Void> writeStream(long fixtureId, String marker, ServerHttpResponse response) {
        reactor.netty.http.server.HttpServerResponse nativeResponse = ServerHttpResponseDecorator.getNativeResponse(response);
        AtomicReference<Channel> channel = new AtomicReference<>();
        nativeResponse.withConnection(connection -> channel.set(connection.channel()));
        response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().set("X-Accel-Buffering", "no");
        return response.writeAndFlushWith(hub.subscribe(fixtureId, channel.get(), marker)
                .map(bytes -> Mono.just(response.bufferFactory().wrap(bytes))));
    }
}
