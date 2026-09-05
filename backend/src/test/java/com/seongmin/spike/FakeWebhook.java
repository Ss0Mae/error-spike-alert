package com.seongmin.spike;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 의존성 0 인 webhook 대역. 응답 코드를 순서대로 스크립트하고, 앞쪽 N 요청을 지연시킬 수 있다. */
public class FakeWebhook implements AutoCloseable {
    public record Received(String idempotencyKey, String body, Instant at) {}

    private final HttpServer server;
    private final Queue<Integer> scripted = new ConcurrentLinkedQueue<>();
    private final AtomicInteger delayRemaining = new AtomicInteger(0);
    private volatile long delayMs = 0;
    public final List<Received> received = new CopyOnWriteArrayList<>();

    public FakeWebhook() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook"; }
    public void script(int... statuses) { for (int s : statuses) scripted.add(s); }
    public void delayFirst(int n, long ms) { delayRemaining.set(n); delayMs = ms; }
    public long duplicates() { return received.size() - received.stream().map(Received::idempotencyKey).distinct().count(); }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(ex.getRequestHeaders().getFirst("Idempotency-Key"), body, Instant.now()));
        if (delayRemaining.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
        }
        Integer status = scripted.poll();
        int code = status == null ? 200 : status;
        if (code == 429) ex.getResponseHeaders().add("Retry-After", "1");
        ex.sendResponseHeaders(code, -1);
        ex.close();
    }

    @Override public void close() { server.stop(0); }
}
