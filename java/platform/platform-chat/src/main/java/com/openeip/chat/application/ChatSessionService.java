package com.openeip.chat.application;

import com.openeip.chat.domain.MessageRole;
import com.openeip.chat.domain.entity.ChatMessage;
import com.openeip.chat.domain.entity.ChatSession;
import com.openeip.chat.domain.repository.ChatMessageRepository;
import com.openeip.chat.domain.repository.ChatSessionRepository;
import com.openeip.chat.shared.exception.ChatException;
import com.openeip.knowledge.application.KnowledgeBaseService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional user-scoped chat session and complete-message service. */
@Service
public class ChatSessionService {
  public static final String MVP_TENANT = "default";

  private final ChatSessionRepository sessions;
  private final ChatMessageRepository messages;
  private final KnowledgeBaseService knowledge;
  private final Clock clock;

  @Autowired
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are application-scoped services.")
  public ChatSessionService(
      ChatSessionRepository sessions,
      ChatMessageRepository messages,
      KnowledgeBaseService knowledge) {
    this(sessions, messages, knowledge, Clock.systemUTC());
  }

  ChatSessionService(
      ChatSessionRepository sessions,
      ChatMessageRepository messages,
      KnowledgeBaseService knowledge,
      Clock clock) {
    this.sessions = sessions;
    this.messages = messages;
    this.knowledge = knowledge;
    this.clock = clock;
  }

  @Transactional
  public ChatSession create(String userId, String knowledgeBaseId, String title) {
    knowledge.get(userId, knowledgeBaseId);
    Instant now = clock.instant();
    return sessions.save(
        new ChatSession(
            UUID.randomUUID().toString(),
            MVP_TENANT,
            userId,
            knowledgeBaseId,
            validTitle(title),
            now));
  }

  @Transactional(readOnly = true)
  public List<ChatSession> list(String userId) {
    return sessions.findAllByTenantIdAndOwnerIdOrderByUpdatedAtDesc(MVP_TENANT, userId);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> history(String userId, String sessionId) {
    owned(userId, sessionId);
    return messages.findAllByTenantIdAndSessionIdAndOwnerIdOrderByMessageIndexAsc(
        MVP_TENANT, sessionId, userId);
  }

  @Transactional
  public StreamContext begin(
      String userId, String sessionId, String requestId, String content, int topK) {
    validUuid(requestId);
    String validContent = validMessage(content);
    if (topK < 1 || topK > 20) {
      throw ChatException.invalid("Invalid Chat topK");
    }
    ChatSession session = locked(userId, sessionId);
    knowledge.get(userId, session.getKnowledgeBaseId());
    Instant now = clock.instant();
    long index = session.begin(requestId, now);
    messages.save(
        new ChatMessage(
            UUID.randomUUID().toString(),
            MVP_TENANT,
            sessionId,
            userId,
            index,
            MessageRole.USER,
            validContent,
            now));
    return new StreamContext(
        sessionId, session.getKnowledgeBaseId(), requestId, validContent, topK);
  }

  @Transactional
  public void complete(String userId, String sessionId, String requestId, String content) {
    String validContent = validAssistant(content);
    ChatSession session = locked(userId, sessionId);
    Instant now = clock.instant();
    long index = session.complete(requestId, now);
    messages.save(
        new ChatMessage(
            UUID.randomUUID().toString(),
            MVP_TENANT,
            sessionId,
            userId,
            index,
            MessageRole.ASSISTANT,
            validContent,
            now));
  }

  @Transactional
  public void cancel(String userId, String sessionId, String requestId) {
    locked(userId, sessionId).cancel(requestId, clock.instant());
  }

  private ChatSession owned(String userId, String sessionId) {
    validUuid(sessionId);
    return sessions
        .findByIdAndTenantIdAndOwnerId(sessionId, MVP_TENANT, userId)
        .orElseThrow(ChatException::notFound);
  }

  private ChatSession locked(String userId, String sessionId) {
    validUuid(sessionId);
    return sessions
        .findOwnedForUpdate(sessionId, MVP_TENANT, userId)
        .orElseThrow(ChatException::notFound);
  }

  private static String validTitle(String value) {
    String title = value == null || value.isBlank() ? "New conversation" : value.trim();
    if (title.length() > 120 || hasForbiddenControl(title)) {
      throw ChatException.invalid("Invalid Chat title");
    }
    return title;
  }

  private static String validMessage(String value) {
    if (value == null || value.isBlank() || value.length() > 4000 || hasForbiddenControl(value)) {
      throw ChatException.invalid("Invalid Chat message");
    }
    return value;
  }

  private static String validAssistant(String value) {
    if (value == null || value.isBlank() || value.length() > 8000 || hasForbiddenControl(value)) {
      throw ChatException.upstream();
    }
    return value;
  }

  private static boolean hasForbiddenControl(String value) {
    return value
        .chars()
        .anyMatch(
            character ->
                character < 32 && character != '\n' && character != '\r' && character != '\t');
  }

  private static void validUuid(String value) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw ChatException.invalid("Invalid Chat identifier");
      }
    } catch (IllegalArgumentException exception) {
      throw ChatException.invalid("Invalid Chat identifier");
    }
  }

  public record StreamContext(
      String sessionId, String knowledgeBaseId, String requestId, String message, int topK) {}
}
