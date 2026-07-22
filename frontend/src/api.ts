export interface ApiEnvelope<T> {
  code: number | string;
  message: string;
  data: T;
  requestId: string;
  timestamp: string;
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

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(path, { ...init, headers });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !body || body.code !== 0) {
    throw new ApiError(
      body?.message || `Request failed (${response.status})`,
      response.status,
      String(body?.code || ''),
    );
  }
  return body.data;
}

export function login(username: string, password: string): Promise<Tokens> {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function currentUser(token: string): Promise<CurrentUser> {
  return request('/api/v1/auth/me', {}, token);
}

export function createSession(token: string, knowledgeBaseId: string, title: string): Promise<ChatSession> {
  return request(
    '/api/v1/chat/sessions',
    { method: 'POST', body: JSON.stringify({ knowledgeBaseId, title: title || undefined }) },
    token,
  );
}

export function getHistory(token: string, sessionId: string): Promise<ChatMessage[]> {
  return request(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {}, token);
}
