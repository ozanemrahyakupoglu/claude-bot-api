package com.claudebot.api.controller;

import com.claudebot.api.service.EventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventStreamService eventStream;

    public EventController(EventStreamService eventStream) {
        this.eventStream = eventStream;
    }

    /**
     * Live events of every run (also the ones started over the synchronous endpoints), as Server-Sent Events.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return eventStream.subscribe();
    }
}
