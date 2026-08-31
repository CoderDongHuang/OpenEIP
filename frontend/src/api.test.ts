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
    const workflow: api.WorkflowDefinition = {
      id: 'workflow',
      name: 'Flow',
      description: '',
      role: 'OWNER',
      status: 'DRAFT',
      draftRevision: 2,
      publishedVersion: 1,
      graph: { schemaVersion: 1, nodes: [], edges: [] },
      createdAt: '2026-07-24T00:00:00Z',
      updatedAt: '2026-07-24T00:00:00Z',
    };
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
      api.listWorkflows(token),
      api.createWorkflow(token, 'Flow', ''),
      api.updateWorkflow(token, workflow, 'Flow', '', workflow.graph),
      api.validateWorkflow(token, workflow.id),
      api.publishWorkflow(token, workflow),
      api.listWorkflowVersions(token, workflow.id),
      api.restoreWorkflowVersion(token, workflow.id, 1),
      api.listWorkflowExecutions(token, workflow.id),
      api.triggerWorkflow(token, workflow.id, {}, 'run-1'),
      api.getWorkflowExecution(token, 'execution'),
      api.cancelWorkflowExecution(token, 'execution'),
      api.retryWorkflowNode(token, 'execution', 'node'),
      api.listWorkflowEvents(token, 'execution'),
      api.listWorkflowTriggers(token, workflow.id),
      api.createWorkflowTrigger(token, workflow.id, 'WEBHOOK', {}),
      api.decideWorkflowApproval(token, 'approval', 'APPROVE'),
    ]);
    expect(fetchMock).toHaveBeenCalledTimes(36);
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
    await api.deleteWorkflow('token', 'workflow');
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

  it('encodes Agent v2 governance, execution, and Evaluation contracts', async () => {
    const requests: Array<[RequestInfo | URL, RequestInit | undefined]> = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        requests.push([input, init]);
        return envelope({ items: [] });
      }),
    );
    vi.stubGlobal('crypto', { randomUUID: () => '00000000-0000-4000-8000-000000000001' });
    const token = 'agent-token';
    const now = '2026-08-31T00:00:00Z';
    const definition: api.AgentDefinitionV2 = {
      id: 'agent/id',
      ownerId: 'owner',
      name: 'Agent',
      type: 'CUSTOM',
      description: '',
      status: 'PUBLISHED',
      draft: {},
      draftRevision: 2,
      publishedVersion: 1,
      revision: 3,
      createdAt: now,
      updatedAt: now,
    };
    const run: api.AgentRunV2 = {
      id: 'run/id',
      agentId: definition.id,
      agentVersionId: 'version',
      agentVersion: 1,
      status: 'PAUSED',
      resourceHandles: [],
      budget: { maxSteps: 8, maxDurationSeconds: 60, maxToolCalls: 8, maxWorkers: 1 },
      currentSequence: 1,
      revision: 4,
      failureCode: null,
      createdAt: now,
      updatedAt: now,
    };
    const grant: api.AgentToolGrantV2 = {
      id: 'grant/id',
      agentVersionId: 'version',
      toolVersionId: 'tool',
      operations: ['search'],
      resourceSelector: {},
      approvalMode: 'NONE',
      expiresAt: null,
      revision: 5,
      revokedAt: null,
    };
    const memory: api.AgentMemoryV2 = {
      id: 'memory/id',
      agentId: definition.id,
      purpose: 'support',
      sourceType: 'RUN',
      sourceId: run.id,
      sensitivity: 'INTERNAL',
      provenance: {},
      state: 'ACTIVE',
      retentionDeadline: now,
      revision: 6,
      createdAt: now,
    };
    const server: api.McpServerV2 = {
      id: 'server/id',
      name: 'MCP',
      transport: 'STREAMABLE_HTTP',
      endpoint: 'https://mcp.example.test',
      authType: 'NONE',
      credentialConfigured: false,
      status: 'REGISTERED',
      revision: 7,
      updatedAt: now,
    };
    const evaluation: api.EvaluationRunV2 = {
      id: 'evaluation/id',
      suiteVersionId: 'suite',
      candidateAgentVersionId: 'candidate',
      baselineAgentVersionId: 'baseline',
      repeatCount: 3,
      status: 'RUNNING',
      gateStatus: null,
      revision: 8,
      metrics: [],
      gates: [],
      createdAt: now,
    };

    await Promise.all([
      api.listAgentDefinitionsV2(token),
      api.createAgentDefinitionV2(token, { name: 'Agent', type: 'CUSTOM', description: '' }),
      api.updateAgentDefinitionV2(token, definition, { description: 'Updated' }),
      api.archiveAgentDefinitionV2(token, definition),
      api.listAgentVersionsV2(token, definition.id),
      api.createAgentCandidateV2(token, definition),
      api.publishAgentVersionV2(token, definition, 'evaluation'),
      api.restoreAgentVersionV2(token, definition, 1),
      api.listAgentRunsV2(token),
      api.createAgentRunV2(token, definition.id, {
        agentVersion: 1,
        input: 'task',
        resourceHandles: [],
        budget: { maxSteps: 8 },
      }),
      api.listAgentRunEventsV2(token, run.id),
      api.commandAgentRunV2(token, run, 'resume'),
      api.listAgentToolsV2(token),
      api.listAgentToolGrantsV2(token),
      api.createAgentToolGrantV2(token, {
        agentVersionId: 'version',
        toolVersionId: 'tool',
        operations: ['search'],
        resourceSelector: {},
        argumentConstraints: {},
        approvalMode: 'NONE',
      }),
      api.revokeAgentToolGrantV2(token, grant),
      api.listAgentMemoriesV2(token, 'ACTIVE'),
      api.quarantineAgentMemoryV2(token, memory),
      api.deleteAgentMemoryV2(token, memory),
      api.exportAgentMemoriesV2(token, 'support'),
      api.listMcpServersV2(token),
      api.createMcpServerV2(token, server),
      api.commandMcpServerV2(token, server, 'test'),
      api.commandMcpServerV2(token, server, 'discover'),
      api.disableMcpServerV2(token, server),
      api.listEvaluationDatasetsV2(token),
      api.createEvaluationDatasetV2(token, 'Regression', 'Pinned'),
      api.listEvaluationSuitesV2(token),
      api.createEvaluationRunV2(token, {
        suiteVersionId: 'suite',
        candidateAgentVersionId: 'candidate',
        baselineAgentVersionId: 'baseline',
        repeatCount: 3,
      }),
      api.getEvaluationRunV2(token, evaluation.id),
      api.cancelEvaluationRunV2(token, evaluation),
    ]);

    expect(requests).toHaveLength(31);
    expect(
      requests.every((request) => new Headers(request[1]?.headers).get('Authorization') === `Bearer ${token}`),
    ).toBe(true);
    const mutations = requests.filter((request) => ['POST', 'PATCH', 'DELETE'].includes(request[1]?.method || ''));
    expect(
      mutations.every(
        (request) =>
          new Headers(request[1]?.headers).has('Idempotency-Key') ||
          request[1]?.method === 'PATCH' ||
          String(request[0]).endsWith('/api/v2/agents'),
      ),
    ).toBe(true);
    expect(requests.some((request) => String(request[0]).includes('agent%2Fid/versions:candidate'))).toBe(true);
    expect(requests.some((request) => String(request[0]).includes('evaluation%2Fid:cancel'))).toBe(true);
  });
});
