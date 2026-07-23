export interface ApiEnvelope<T> {
  code: number | string;
  message: string;
  data: T;
  requestId: string;
  timestamp: string;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  roles: string[];
  permissions: string[];
}

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  active: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Role {
  id: string;
  name: string;
  description: string;
  permissions: string[];
}

export interface DocumentFile {
  id: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: string;
  createdAt: string;
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  role: 'OWNER' | 'EDITOR' | 'VIEWER';
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeDocument {
  documentId: string;
  status: string;
  failureCode?: string | null;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessingResult {
  documentId: string;
  status: string;
  sourceType: string;
  chunkCount: number;
  vectorCount: number;
  updatedAt: string;
}

export interface ChatSession {
  sessionId: string;
  knowledgeBaseId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  messageId: string;
  sequence: number;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface AgentTool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

export interface AgentMetadata {
  agentId: string;
  name: string;
  description: string;
  version: string;
  spiVersion: string;
  tools: AgentTool[];
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly requestId?: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept', 'application/json');
  const response = await fetch(path, { ...init, headers });
  if (response.status === 204) return undefined as T;
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !body || body.code !== 0) {
    throw new ApiError(
      body?.message || `Request failed (${response.status})`,
      response.status,
      body ? String(body.code) : undefined,
      body?.requestId,
    );
  }
  return body.data;
}

const json = (method: string, value?: unknown): RequestInit => ({
  method,
  body: value === undefined ? undefined : JSON.stringify(value),
});

export const login = (username: string, password: string) =>
  request<Tokens>('/api/v1/auth/login', json('POST', { username, password }));
export const register = (username: string, email: string, password: string) =>
  request<CurrentUser>('/api/v1/auth/register', json('POST', { username, email, password }));
export const currentUser = (token: string) => request<CurrentUser>('/api/v1/auth/me', {}, token);
export const listUsers = (token: string, page = 1, pageSize = 20) =>
  request<PageResult<AdminUser>>(`/api/v1/auth/users?page=${page}&pageSize=${pageSize}`, {}, token);
export const listRoles = (token: string) => request<Role[]>('/api/v1/auth/roles', {}, token);
export const replaceUserRoles = (token: string, userId: string, roleNames: string[]) =>
  request<CurrentUser>(`/api/v1/auth/users/${encodeURIComponent(userId)}/roles`, json('PUT', { roleNames }), token);
export const setUserActive = (token: string, userId: string, active: boolean) =>
  request<AdminUser>(`/api/v1/auth/users/${encodeURIComponent(userId)}/active`, json('PATCH', { active }), token);

export const listFiles = (token: string, page = 1, pageSize = 100) =>
  request<PageResult<DocumentFile>>(`/api/v1/documents/files?page=${page}&pageSize=${pageSize}`, {}, token);
export const uploadFile = (token: string, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return request<DocumentFile>('/api/v1/documents/files', { method: 'POST', body: form }, token);
};
export const deleteFile = (token: string, fileId: string) =>
  request<void>(`/api/v1/documents/files/${encodeURIComponent(fileId)}`, { method: 'DELETE' }, token);
export async function downloadFile(token: string, file: DocumentFile): Promise<void> {
  const response = await fetch(`/api/v1/documents/files/${encodeURIComponent(file.id)}/content`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' },
  });
  if (!response.ok) throw new ApiError(`Download failed (${response.status})`, response.status);
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.originalName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export const listKnowledgeBases = (token: string, page = 1, pageSize = 100) =>
  request<PageResult<KnowledgeBase>>(`/api/v1/knowledge/bases?page=${page}&pageSize=${pageSize}`, {}, token);
export const createKnowledgeBase = (token: string, name: string, description: string) =>
  request<KnowledgeBase>('/api/v1/knowledge/bases', json('POST', { name, description }), token);
export const updateKnowledgeBase = (token: string, id: string, name: string, description: string) =>
  request<KnowledgeBase>(
    `/api/v1/knowledge/bases/${encodeURIComponent(id)}`,
    json('PATCH', { name, description }),
    token,
  );
export const deleteKnowledgeBase = (token: string, id: string) =>
  request<void>(`/api/v1/knowledge/bases/${encodeURIComponent(id)}`, { method: 'DELETE' }, token);
export const listKnowledgeDocuments = (token: string, baseId: string) =>
  request<KnowledgeDocument[]>(`/api/v1/knowledge/bases/${encodeURIComponent(baseId)}/documents`, {}, token);
export const attachKnowledgeDocument = (token: string, baseId: string, documentId: string) =>
  request<KnowledgeDocument>(
    `/api/v1/knowledge/bases/${encodeURIComponent(baseId)}/documents`,
    json('POST', { documentId }),
    token,
  );
export const detachKnowledgeDocument = (token: string, baseId: string, documentId: string) =>
  request<void>(
    `/api/v1/knowledge/bases/${encodeURIComponent(baseId)}/documents/${encodeURIComponent(documentId)}`,
    { method: 'DELETE' },
    token,
  );
export const processKnowledgeDocument = (token: string, baseId: string, documentId: string) =>
  request<ProcessingResult>(
    `/api/v1/knowledge/bases/${encodeURIComponent(baseId)}/documents/${encodeURIComponent(documentId)}/processing`,
    { method: 'POST' },
    token,
  );

export const listSessions = (token: string) => request<ChatSession[]>('/api/v1/chat/sessions', {}, token);
export const createSession = (token: string, knowledgeBaseId: string, title: string) =>
  request<ChatSession>('/api/v1/chat/sessions', json('POST', { knowledgeBaseId, title: title || undefined }), token);
export const getHistory = (token: string, sessionId: string) =>
  request<ChatMessage[]>(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {}, token);

export const listAgents = (token: string) => request<AgentMetadata[]>('/api/v1/agents', {}, token);
