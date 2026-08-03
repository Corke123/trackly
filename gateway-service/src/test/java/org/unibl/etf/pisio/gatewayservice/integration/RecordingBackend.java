package org.unibl.etf.pisio.gatewayservice.integration;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A stand-in for a downstream service on an ephemeral port. It records what actually arrived, which
 * is the only way to tell whether the gateway stripped the api prefix and relayed the access token.
 */
final class RecordingBackend implements AutoCloseable {

    private final Queue<ReceivedRequest> received = new ConcurrentLinkedQueue<>();

    private final DisposableServer server;

    RecordingBackend(String body) {
        this.server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    received.add(new ReceivedRequest(request.uri(), request.requestHeaders().get("Authorization")));
                    return response.sendString(Mono.just(body));
                })
                .bindNow();
    }

    String uri() {
        return "http://localhost:" + server.port();
    }

    /**
     * Removes and returns the oldest request this backend received, so each test sees only its own.
     */
    ReceivedRequest takeRequest() {
        return received.poll();
    }

    @Override
    public void close() {
        server.disposeNow();
    }

    record ReceivedRequest(String path, String authorization) {
    }
}
