package com.iknow.controller;

import com.iknow.dto.request.CreateSessionRequest;
import com.iknow.dto.response.SessionResponse;
import com.iknow.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // POST /api/sessions — 세션 생성
    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(sessionService.createSession(request));
    }

    // PATCH /api/sessions/:id/end — 세션 종료
    @PatchMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse> endSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.endSession(sessionId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }
}
