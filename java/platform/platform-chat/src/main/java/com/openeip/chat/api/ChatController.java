package com.openeip.chat.api;

import com.openeip.chat.api.dto.ChatMessageResponse;
import com.openeip.chat.api.dto.ChatSessionResponse;
import com.openeip.chat.api.dto.CreateChatSessionRequest;
import com.openeip.chat.api.dto.StreamChatMessageRequest;
import com.openeip.chat.application.ChatSessionService;
import com.openeip.chat.infrastructure.ChatStreamGateway;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {
  private final ChatSessionService sessions;
  private final ChatStreamGateway gateway;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application-scoped services.")
  public ChatController(ChatSessionService sessions, ChatStreamGateway gateway) {
    this.sessions = sessions;
    this.gateway = gateway;
  }

  @PostMapping
  public ResponseEntity<ApiEnvelope<ChatSessionResponse>> create(
      @Valid @RequestBody CreateChatSessionRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var session = sessions.create(authentication.getName(), body.knowledgeBaseId(), body.title());
    return ResponseEntity.status(201)
        .body(ApiEnvelope.success(ChatSessionResponse.from(session), RequestIdFilter.get(request)));
  }

  @GetMapping("/{sessionId}/messages")
  public ApiEnvelope<List<ChatMessageResponse>> history(
      @PathVariable("sessionId") String sessionId,
      Authentication authentication,
      HttpServletRequest request) {
    var data =
        sessions.history(authentication.getName(), sessionId).stream()
            .map(ChatMessageResponse::from)
            .toList();
    return ApiEnvelope.success(data, RequestIdFilter.get(request));
  }

  @PostMapping(value = "/{sessionId}/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> stream(
      @PathVariable("sessionId") String sessionId,
      @Valid @RequestBody StreamChatMessageRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    String userId = authentication.getName();
    String requestId = RequestIdFilter.get(request);
    var context = sessions.begin(userId, sessionId, requestId, body.message(), body.resolvedTopK());
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
        .header("X-Accel-Buffering", "no")
        .body(gateway.open(userId, context));
  }
}
