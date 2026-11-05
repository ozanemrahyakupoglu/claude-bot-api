package com.claudebot.api.service;

import com.claudebot.api.dto.RunEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

/**
 * Pushes run events to every connected UI over Server-Sent Events.
 * Sending happens on one background thread so a slow browser never holds up reading the CLI output.
 */
@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-sender");
        t.setDaemon(true);
        return t;
    });

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        sender.execute(() -> send(emitter, SseEmitter.event().comment("connected")));
        log.info("Event stream subscribed: clients={}", emitters.size());
        return emitter;
    }

    public void publish(RunEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        sender.execute(() -> {
            for (SseEmitter emitter : emitters) {
                send(emitter, SseEmitter.event().name("run").data(event));
            }
        });
    }

    // Proxy'ler ve tarayıcılar boşta kalan bağlantıyı kapatmasın; kopan istemciler de burada ayıklanır
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        sender.execute(() -> {
            for (SseEmitter emitter : emitters) {
                send(emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        emitters.forEach(SseEmitter::complete);
        sender.shutdownNow();
    }
}
