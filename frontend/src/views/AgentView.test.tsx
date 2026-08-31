import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import {
  DefinitionsSurface,
  EvaluationSurface,
  McpSurface,
  MemorySurface,
  RunsSurface,
  ToolsSurface,
} from './AgentView';

const now = '2026-08-31T00:00:00Z';
const definition = {
  id: 'agent-1',
  ownerId: 'owner-1',
  name: 'Support Agent',
  type: 'CUSTOM' as const,
  description: 'Support',
  status: 'PUBLISHED' as const,
  draft: {},
  draftRevision: 2,
  publishedVersion: 1,
  revision: 2,
  createdAt: now,
  updatedAt: now,
};
const version = {
  id: 'version-1',
  agentId: definition.id,
  version: 1,
  status: 'PUBLISHED' as const,
  sourceDraftRevision: 2,
  digest: 'a'.repeat(64),
  config: {},
  evaluationRunId: 'evaluation-1',
  createdBy: definition.ownerId,
  createdAt: now,
  publishedBy: definition.ownerId,
  publishedAt: now,
};
const run = {
  id: 'run-12345678',
  agentId: definition.id,
  agentVersionId: version.id,
  agentVersion: 1,
  status: 'EXECUTING' as const,
  resourceHandles: [],
  budget: { maxSteps: 32, maxDurationSeconds: 600, maxToolCalls: 64, maxWorkers: 4 },
  currentSequence: 1,
  revision: 1,
  failureCode: null,
  createdAt: now,
  updatedAt: now,
};
const noop = () => undefined;

describe('Agent Platform management surfaces', () => {
  it('renders immutable definitions and versions', () => {
    const html = renderToStaticMarkup(
      <DefinitionsSurface
        definitions={[definition]}
        selected={definition}
        versions={[version]}
        busy=""
        onSelect={noop}
        onNew={noop}
        onSave={noop}
        onCandidate={noop}
        onPublish={noop}
        onRestore={noop}
        onArchive={noop}
      />,
    );
    expect(html).toContain('Agent definitions');
    expect(html).toContain('Immutable snapshots');
    expect(html).toContain('PUBLISHED');
  });

  it('renders the safe ordered run timeline', () => {
    const html = renderToStaticMarkup(
      <RunsSurface
        runs={[run]}
        selected={run}
        events={[
          {
            id: 'event-1',
            runId: run.id,
            sequence: 1,
            type: 'plan.created',
            payload: { stepCount: 1 },
            occurredAt: now,
          },
        ]}
        busy=""
        onSelect={noop}
        onNew={noop}
        onCommand={noop}
      />,
    );
    expect(html).toContain('Runs');
    expect(html).toContain('plan.created');
    expect(html).toContain('stepCount');
  });

  it('renders Tool risk and effective grants', () => {
    const tool = {
      id: 'tool-1',
      toolKey: 'openeip.search',
      name: 'Search',
      version: '1.0.0',
      riskClass: 'READ' as const,
      operations: ['search'],
      maxDurationMs: 1000,
    };
    const html = renderToStaticMarkup(
      <ToolsSurface
        tools={[tool]}
        versions={[version]}
        grants={[
          {
            id: 'grant-1',
            agentVersionId: version.id,
            toolVersionId: tool.id,
            operations: ['search'],
            resourceSelector: {},
            approvalMode: 'NONE',
            expiresAt: null,
            revision: 0,
            revokedAt: null,
          },
        ]}
        busy=""
        onNew={noop}
        onRevoke={noop}
      />,
    );
    expect(html).toContain('Tool permissions');
    expect(html).toContain('openeip.search');
    expect(html).toContain('search');
  });

  it('renders redacted Memory governance actions', () => {
    const html = renderToStaticMarkup(
      <MemorySurface
        entries={[
          {
            id: 'memory-1',
            agentId: definition.id,
            purpose: 'support',
            sourceType: 'RUN',
            sourceId: run.id,
            sensitivity: 'INTERNAL',
            provenance: {},
            state: 'ACTIVE',
            retentionDeadline: now,
            revision: 0,
            createdAt: now,
          },
        ]}
        busy=""
        onState={noop}
        onExport={noop}
        onQuarantine={noop}
        onDelete={noop}
      />,
    );
    expect(html).toContain('Memory governance');
    expect(html).toContain('content is redacted');
    expect(html).toContain('Quarantine');
  });

  it('renders MCP policy and discovery controls without credentials', () => {
    const html = renderToStaticMarkup(
      <McpSurface
        servers={[
          {
            id: 'server-1',
            name: 'CRM',
            transport: 'STREAMABLE_HTTP',
            endpoint: 'https://mcp.example.test',
            authType: 'BEARER_REF',
            credentialConfigured: true,
            status: 'REGISTERED',
            revision: 0,
            updatedAt: now,
          },
        ]}
        busy=""
        onNew={noop}
        onCommand={noop}
        onDisable={noop}
      />,
    );
    expect(html).toContain('MCP Servers');
    expect(html).toContain('Discover');
    expect(html).not.toContain('secret://');
  });

  it('renders Evaluation metrics, confidence, and gate evidence', () => {
    const html = renderToStaticMarkup(
      <EvaluationSurface
        datasets={[
          {
            id: 'dataset-1',
            name: 'Regression 500',
            description: 'Pinned',
            status: 'PUBLISHED',
            revision: 0,
            updatedAt: now,
          },
        ]}
        suites={[
          {
            id: 'suite-1',
            name: 'Alpha gates',
            status: 'PUBLISHED',
            versionId: 'suite-version-1',
            datasetVersionIds: ['dataset-version-1'],
            gatePolicy: {},
            digest: 'b'.repeat(64),
          },
        ]}
        versions={[version]}
        result={{
          id: 'evaluation-1',
          suiteVersionId: 'suite-version-1',
          candidateAgentVersionId: version.id,
          baselineAgentVersionId: version.id,
          repeatCount: 3,
          status: 'COMPLETED',
          gateStatus: 'PASS',
          revision: 1,
          metrics: [{ key: 'deterministicSafety', value: 1, sampleCount: 500, low: 1, high: 1 }],
          gates: [{ key: 'deterministicSafety', status: 'PASS', actual: 1, threshold: 1, reasonCode: 'THRESHOLD_MET' }],
          createdAt: now,
        }}
        busy=""
        onNewDataset={noop}
        onRun={noop}
        onCancel={noop}
      />,
    );
    expect(html).toContain('Evaluation Dashboard');
    expect(html).toContain('deterministicSafety');
    expect(html).toContain('n=500');
    expect(html).toContain('THRESHOLD_MET');
  });
});
