import { afterEach, describe, expect, it, vi } from 'vitest';

import * as api from './api';

const envelope = (data: unknown, code: number | string = 0, message = 'success') =>
  new Response(JSON.stringify({ code, message, data, requestId: 'request-1', timestamp: '2026-07-22T00:00:00Z' }), {
    status: code === 0 ? 200 : 400,
    headers: { 'Content-Type': 'application/json' },
  });

describe('API client', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('encodes every public JSON contract with bearer authentication', async () => {
    const requests: Array<[RequestInfo | URL, RequestInit | undefined]> = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push([input, init]);
      return envelope({ items: [], roles: [], permissions: [] });
    });
    vi.stubGlobal('fetch', fetchMock);
    const token = 'access';
    await Promise.all([
      api.login('demo', 'password'),
      api.register('demo2', 'demo2@example.test', 'password'),
      api.currentUser(token),
      api.listUsers(token),
      api.listRoles(token),
      api.replaceUserRoles(token, 'user', ['ROLE_USER']),
      api.setUserActive(token, 'user', true),
      api.listFiles(token),
      api.listKnowledgeBases(token),
      api.createKnowledgeBase(token, 'Base', 'Description'),
      api.updateKnowledgeBase(token, 'base', 'Base', 'Description'),
      api.listKnowledgeDocuments(token, 'base'),
      api.attachKnowledgeDocument(token, 'base', 'document'),
      api.processKnowledgeDocument(token, 'base', 'document'),
      api.retryKnowledgeDocument(token, 'base', 'document'),
      api.searchKnowledge(token, 'base', 'invoice', 'HYBRID'),
      api.listSessions(token),
      api.createSession(token, 'base', 'Title'),
      api.getHistory(token, 'session'),
      api.listAgents(token),
    ]);
    expect(fetchMock).toHaveBeenCalledTimes(20);
    const search = requests.find((call) => String(call[0]).endsWith('/knowledge/bases/base/search'));
    expect(search?.[1]?.body).toBe(JSON.stringify({ query: 'invoice', mode: 'HYBRID', topK: 10 }));
    const authenticated = requests.filter(
      (call) =>
        String(call[0]).includes('/api/v1/') &&
        !String(call[0]).includes('/login') &&
        !String(call[0]).includes('/register'),
    );
    expect(
      authenticated.every(
        (call) => new Headers((call[1] as RequestInit).headers).get('Authorization') === 'Bearer access',
      ),
    ).toBe(true);
  });

  it('handles multipart uploads, no-content deletes, and downloads', async () => {
    const requests: Array<[RequestInfo | URL, RequestInit | undefined]> = [];
    const responses = [
      envelope({ id: 'file' }),
      new Response(null, { status: 204 }),
      new Response(null, { status: 204 }),
      new Response(null, { status: 204 }),
      new Response('content', { status: 200 }),
    ];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push([input, init]);
      const response = responses.shift();
      if (!response) throw new Error('Unexpected fetch call');
      return response;
    });
    vi.stubGlobal('fetch', fetchMock);
    const createObjectURL = vi.fn(() => 'blob:test');
    const revokeObjectURL = vi.fn();
    const click = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    vi.stubGlobal('document', { createElement: () => ({ href: '', download: '', click }) });
    await api.uploadFile('token', new File(['content'], 'source.txt', { type: 'text/plain' }));
    await api.deleteFile('token', 'file');
    await api.deleteKnowledgeBase('token', 'base');
    await api.detachKnowledgeDocument('token', 'base', 'file');
    await api.downloadFile('token', {
      id: 'file',
      originalName: 'source.txt',
      contentType: 'text/plain',
      sizeBytes: 7,
      sha256: 'a'.repeat(64),
      status: 'READY',
      createdAt: '2026-07-22T00:00:00Z',
    });
    const uploadHeaders = new Headers(requests[0][1]?.headers);
    expect(uploadHeaders.has('Content-Type')).toBe(false);
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });

  it('surfaces stable envelope and malformed-response errors', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(envelope(null, 'AUTH-E-001', 'Invalid username or password'))
      .mockResolvedValueOnce(new Response('not-json', { status: 502 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(api.login('demo', 'bad')).rejects.toMatchObject({
      message: 'Invalid username or password',
      status: 400,
      code: 'AUTH-E-001',
      requestId: 'request-1',
    });
    await expect(api.currentUser('token')).rejects.toMatchObject({ message: 'Request failed (502)', status: 502 });
  });
});
