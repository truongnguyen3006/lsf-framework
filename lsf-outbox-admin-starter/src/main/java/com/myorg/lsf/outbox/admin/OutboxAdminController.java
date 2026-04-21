package com.myorg.lsf.outbox.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("${lsf.outbox.admin.base-path:/lsf/outbox}")
public class OutboxAdminController {

    private final OutboxAdminService svc;

    @GetMapping
    public List<OutboxAdminRow> list(
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "msgKey", required = false) String msgKey,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset
    ) {
        List<OutboxStatus> statuses = status == null ? List.of() : status.stream().map(OutboxStatus::from).toList();
        java.time.Instant fromTs = (from == null || from.isBlank()) ? null : java.time.Instant.parse(from);
        java.time.Instant toTs = (to == null || to.isBlank()) ? null : java.time.Instant.parse(to);

        return svc.list(statuses, topic, msgKey, eventType, correlationId, fromTs, toTs, limit, offset);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> byId(@PathVariable("id") long id) {
        Optional<OutboxAdminRow> row = svc.findById(id);
        return row.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "NOT_FOUND", "id", id)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> byEventId(@PathVariable("eventId") String eventId) {
        Optional<OutboxAdminRow> row = svc.findByEventId(eventId);
        return row.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "NOT_FOUND", "eventId", eventId)));
    }

    @PostMapping("/requeue/event/{eventId}")
    public Map<String, Object> requeueEvent(
            @PathVariable("eventId") String eventId,
            @RequestParam(name = "mode", defaultValue = "RETRY") String mode,
            @RequestParam(name = "resetRetry", defaultValue = "true") boolean resetRetry
    ) {
        OutboxStatus m = OutboxStatus.from(mode);
        int updated = svc.requeueByEventId(eventId, m, resetRetry);
        return Map.of("updated", updated, "eventId", eventId, "mode", m.name(), "resetRetry", resetRetry);
    }

    @PostMapping("/requeue/failed")
    public Map<String, Object> requeueFailed(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "resetRetry", defaultValue = "true") boolean resetRetry
    ) {
        int updated = svc.requeueFailed(limit, resetRetry);
        return Map.of("updated", updated, "resetRetry", resetRetry);
    }

    public record MarkFailedRequest(String error) {}

    @PostMapping("/mark-failed/event/{eventId}")
    public Map<String, Object> markFailed(
            @PathVariable("eventId") String eventId,
            @RequestBody(required = false) MarkFailedRequest req
    ) {
        String err = (req == null) ? "" : req.error();
        int updated = svc.markFailedByEventId(eventId, err);
        return Map.of("updated", updated, "eventId", eventId);
    }

    @DeleteMapping("/event/{eventId}")
    public Map<String, Object> deleteByEventId(@PathVariable("eventId") String eventId) {
        int deleted = svc.deleteByEventId(eventId);
        return Map.of("deleted", deleted, "eventId", eventId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> forbidden(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", e.getMessage()));
    }
}
