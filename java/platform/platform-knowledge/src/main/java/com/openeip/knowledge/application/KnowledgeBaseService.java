package com.openeip.knowledge.application;

import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.knowledge.domain.MemberRole;
import com.openeip.knowledge.domain.entity.KnowledgeBase;
import com.openeip.knowledge.domain.entity.KnowledgeBaseMember;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.repository.KnowledgeBaseMemberRepository;
import com.openeip.knowledge.domain.repository.KnowledgeBaseRepository;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional knowledge-base control plane with membership-scoped access. */
@Service
public class KnowledgeBaseService {
  public static final String MVP_TENANT = "default";

  private final KnowledgeBaseRepository bases;
  private final KnowledgeBaseMemberRepository members;
  private final KnowledgeDocumentRepository documents;
  private final DocumentFileRepository files;
  private final Clock clock;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public KnowledgeBaseService(
      KnowledgeBaseRepository bases,
      KnowledgeBaseMemberRepository members,
      KnowledgeDocumentRepository documents,
      DocumentFileRepository files) {
    this(bases, members, documents, files, Clock.systemUTC());
  }

  KnowledgeBaseService(
      KnowledgeBaseRepository bases,
      KnowledgeBaseMemberRepository members,
      KnowledgeDocumentRepository documents,
      DocumentFileRepository files,
      Clock clock) {
    this.bases = bases;
    this.members = members;
    this.documents = documents;
    this.files = files;
    this.clock = clock;
  }

  @Transactional
  public BaseAccess create(String userId, String name, String description) {
    String validName = name(name);
    String validDescription = description(description);
    if (bases.existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(MVP_TENANT, userId, validName)) {
      throw KnowledgeException.conflict("Knowledge base name already exists");
    }
    Instant now = clock.instant();
    KnowledgeBase base =
        bases.save(
            new KnowledgeBase(
                UUID.randomUUID().toString(),
                MVP_TENANT,
                userId,
                validName,
                validDescription,
                now));
    members.save(
        new KnowledgeBaseMember(
            UUID.randomUUID().toString(), MVP_TENANT, base.getId(), userId, MemberRole.OWNER, now));
    return new BaseAccess(base, MemberRole.OWNER);
  }

  @Transactional(readOnly = true)
  public Page<BaseAccess> list(String userId, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw KnowledgeException.invalid("Invalid page");
    return bases
        .findAccessible(
            MVP_TENANT,
            userId,
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt")))
        .map(base -> new BaseAccess(base, role(base.getId(), userId)));
  }

  @Transactional(readOnly = true)
  public BaseAccess get(String userId, String baseId) {
    KnowledgeBase base = base(baseId);
    return new BaseAccess(base, role(baseId, userId));
  }

  @Transactional
  public BaseAccess update(String userId, String baseId, String name, String description) {
    BaseAccess access = get(userId, baseId);
    requireEditor(access.role());
    String validName = name(name);
    if (bases.existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
        MVP_TENANT, access.base().getOwnerId(), validName, baseId)) {
      throw KnowledgeException.conflict("Knowledge base name already exists");
    }
    access.base().update(validName, description(description), clock.instant());
    return access;
  }

  @Transactional
  public void delete(String userId, String baseId) {
    BaseAccess access = get(userId, baseId);
    if (access.role() != MemberRole.OWNER) throw KnowledgeException.forbidden();
    access.base().delete(clock.instant());
  }

  @Transactional
  public KnowledgeDocument attach(
      String userId, boolean administrator, String baseId, String documentId) {
    BaseAccess access = get(userId, baseId);
    requireEditor(access.role());
    boolean accessible =
        administrator
            ? files.findByIdAndTenantIdAndDeletedAtIsNull(documentId, MVP_TENANT).isPresent()
            : files
                .findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(documentId, MVP_TENANT, userId)
                .isPresent();
    if (!accessible) throw KnowledgeException.notFound();
    if (documents
        .findByTenantIdAndKnowledgeBaseIdAndDocumentId(MVP_TENANT, baseId, documentId)
        .isPresent()) {
      throw KnowledgeException.conflict("Document is already attached");
    }
    return documents.save(
        new KnowledgeDocument(
            UUID.randomUUID().toString(), MVP_TENANT, baseId, documentId, clock.instant()));
  }

  @Transactional(readOnly = true)
  public List<KnowledgeDocument> listDocuments(String userId, String baseId) {
    get(userId, baseId);
    return documents.findAllByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDesc(MVP_TENANT, baseId);
  }

  @Transactional(readOnly = true)
  public KnowledgeDocument getDocument(String userId, String baseId, String documentId) {
    get(userId, baseId);
    return document(baseId, documentId);
  }

  @Transactional
  public void detach(String userId, String baseId, String documentId) {
    BaseAccess access = get(userId, baseId);
    requireEditor(access.role());
    documents.delete(document(baseId, documentId));
  }

  private KnowledgeBase base(String id) {
    validUuid(id);
    return bases
        .findByIdAndTenantIdAndDeletedAtIsNull(id, MVP_TENANT)
        .orElseThrow(KnowledgeException::notFound);
  }

  private KnowledgeDocument document(String baseId, String documentId) {
    validUuid(documentId);
    return documents
        .findByTenantIdAndKnowledgeBaseIdAndDocumentId(MVP_TENANT, baseId, documentId)
        .orElseThrow(KnowledgeException::notFound);
  }

  private MemberRole role(String baseId, String userId) {
    return members
        .findByTenantIdAndKnowledgeBaseIdAndUserId(MVP_TENANT, baseId, userId)
        .map(KnowledgeBaseMember::getRole)
        .orElseThrow(KnowledgeException::notFound);
  }

  private static void requireEditor(MemberRole role) {
    if (!role.canEdit()) throw KnowledgeException.forbidden();
  }

  private static String name(String value) {
    if (value == null || value.trim().isEmpty() || value.trim().length() > 120) {
      throw KnowledgeException.invalid("Knowledge base name must contain 1 to 120 characters");
    }
    return value.trim();
  }

  private static String description(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > 2000)
      throw KnowledgeException.invalid("Description exceeds 2000 characters");
    return result;
  }

  static void validUuid(String value) {
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw KnowledgeException.invalid("Invalid resource identifier");
    }
  }

  public record BaseAccess(KnowledgeBase base, MemberRole role) {}
}
