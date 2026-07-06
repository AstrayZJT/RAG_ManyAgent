package com.astray.insightflow.task.service;

import com.astray.insightflow.common.model.TaskProgressEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TaskProgressPublisher {

    private final Map<String, List<TaskProgressEvent>> eventHistory = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));

        for (TaskProgressEvent event : eventHistory.getOrDefault(taskId, List.of())) {
            sendEvent(emitter, event);
        }
        publish(taskId, "stream", "CONNECTED", "SSE stream connected", Map.of("taskId", taskId));
        return emitter;
    }

    public void publish(String taskId, String stage, String status, String message, Map<String, Object> payload) {
        TaskProgressEvent event = new TaskProgressEvent(Instant.now(), stage, status, message, payload);
        eventHistory.computeIfAbsent(taskId, key -> new ArrayList<>()).add(event);
        emitters.getOrDefault(taskId, new CopyOnWriteArrayList<>()).forEach(emitter -> sendEvent(emitter, event));
    }

    public List<TaskProgressEvent> history(String taskId) {
        return List.copyOf(eventHistory.getOrDefault(taskId, List.of()));
    }

    private void sendEvent(SseEmitter emitter, TaskProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.stage())
                    .data(event));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void remove(String taskId, SseEmitter emitter) {
        emitters.getOrDefault(taskId, new CopyOnWriteArrayList<>()).remove(emitter);
    }
}
