package com.son.ssegateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContent;
import reactor.core.publisher.FluxSink;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class Subscriber {
    private final Channel channel;
    private final FluxSink<byte[]> sink;
    private final LatestMailbox mailbox;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.function.Consumer<String> onClose;
    private final long stallNanos;
    private long heartbeatAt = System.nanoTime();
    private java.util.concurrent.ScheduledFuture<?> watch;
    private final String diagnostic;

    Subscriber(Channel channel, FluxSink<byte[]> sink, long maxBytes, long stallMs,
               String diagnostic, java.util.function.Consumer<String> onClose) {
        this.channel = channel; this.sink = sink; this.mailbox = new LatestMailbox(maxBytes);
        this.stallNanos = TimeUnit.MILLISECONDS.toNanos(stallMs); this.diagnostic = diagnostic; this.onClose = onClose;
    }
    void start() {
        // Install before Reactor's bridge. Observe the actual outbound write future, not Flux demand.
        channel.pipeline().addBefore("reactor.right.reactiveBridge", "sse-write-progress", new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                boolean content = msg instanceof ByteBuf b && b.isReadable() || msg instanceof HttpContent h && h.content().isReadable();
                if (content) {
                    ChannelPromise original = promise.unvoid();
                    ChannelProgressivePromise tracked = ctx.newProgressivePromise();
                    tracked.addListener(new ChannelProgressiveFutureListener() {
                        @Override public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                            mailbox.progressed(System.nanoTime());
                        }
                        @Override public void operationComplete(ChannelProgressiveFuture future) {
                            if (future.isSuccess()) {
                                mailbox.written(System.nanoTime()); original.trySuccess(); wake();
                            } else { original.tryFailure(future.cause()); Subscriber.this.close("write_error"); }
                        }
                    });
                    promise = tracked;
                }
                ctx.write(msg, promise);
            }
        });
        sink.onRequest(ignored -> wake());
        sink.onCancel(() -> close("client_closed"));
        channel.closeFuture().addListener(ignored -> close("client_closed"));
        watch = channel.eventLoop().scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            if (mailbox.stalled(now, stallNanos)) { close("stalled"); return; }
            if (now - heartbeatAt >= TimeUnit.SECONDS.toNanos(25)) {
                heartbeatAt = now;
                if (mailbox.idle()) offer(Snapshot.heartbeat());
            }
            wake();
        }, 100, 100, TimeUnit.MILLISECONDS);
        offer(Snapshot.connect());
    }
    void offer(Snapshot snapshot) {
        if (closed.get()) return;
        if (!mailbox.offer(snapshot, System.nanoTime())) { close("mailbox_limit"); return; }
        wake();
    }
    private void wake() {
        if (closed.get() || !scheduled.compareAndSet(false, true)) return;
        channel.eventLoop().execute(() -> {
            scheduled.set(false);
            if (closed.get() || !channel.isWritable() || sink.requestedFromDownstream() == 0) return;
            Snapshot next = mailbox.take();
            if (next != null) sink.next(next.bytes());
        });
    }
    void close(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        mailbox.close();
        if (watch != null) watch.cancel(false);
        onClose.accept(reason);
        if (!reason.equals("client_closed")) LoggerFactory.getLogger(Subscriber.class)
                .warn("SSE subscriber closed. reason={}, testClient={}", reason, diagnostic);
        // Close the real transport, including any pending write. Completing a Flux alone is insufficient.
        channel.close(); sink.complete();
    }
    long bytes() { return mailbox.bytes(); }
}
