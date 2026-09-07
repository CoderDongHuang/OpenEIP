import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { GovernanceState } from './GovernanceView';
import { GovernanceDataSurface } from './GovernanceView';

const now = '2026-09-06T00:00:00Z';
const noop = () => undefined;

const state: GovernanceState = {
  tenant: {
    id: 'tenant-1',
    displayName: 'Default Tenant',
    slug: 'default',
    state: 'ACTIVE',
    policyVersion: 'governance-v1',
    revision: 0,
    createdAt: now,
    updatedAt: now,
  },
  memberships: [
    {
      id: 'membership-1',
      principalId: 'principal-1',
      roles: ['VIEWER'],
      state: 'ACTIVE',
      policyVersion: 'governance-v1',
      revision: 0,
      createdAt: now,
      updatedAt: now,
    },
  ],
  audits: [
    {
      id: 'audit-1',
      eventId: 'event-1',
      principalId: 'principal-1',
      action: 'model.registered',
      resourceType: 'model',
      resourceId: 'model-1',
      outcome: 'SUCCESS',
      requestId: 'request-1',
      traceId: '0123456789abcdef',
      policyVersion: 'governance-v1',
      schemaVersion: 'v1',
      occurredAt: now,
      recordHash: 'a'.repeat(64),
      summary: { reasonCode: 'created' },
    },
  ],
  models: [
    {
      id: 'model-1',
      tenantId: 'tenant-1',
      providerId: 'provider-1',
      name: 'Chat model',
      state: 'ENABLED',
      currentVersion: 'v1',
      revision: 2,
      createdAt: now,
      updatedAt: now,
    },
  ],
  prompts: [
    {
      id: 'prompt-1',
      tenantId: 'tenant-1',
      name: 'Support prompt',
      purpose: 'support',
      revision: 1,
      createdAt: now,
      updatedAt: now,
      state: 'DRAFT',
    },
  ],
  usage: [],
  budgets: [],
};

describe('Governance workspace', () => {
  it('renders the server-derived tenant scope and bounded management surfaces', () => {
    const html = renderToStaticMarkup(
      <GovernanceDataSurface
        state={state}
        traces={[]}
        canAdmin
        busy=""
        onVerify={noop}
        onFindTrace={noop}
        onNewModel={noop}
        onNewPrompt={noop}
        onNewBudget={noop}
        onModelAction={noop}
      />,
    );

    expect(html).toContain('Server-derived scope');
    expect(html).toContain('Default Tenant');
    expect(html).toContain('Audit');
    expect(html).toContain('Usage &amp; budgets');
    expect(html).toContain('Trace');
  });

  it('reports audit verification without rendering private Prompt content', () => {
    const html = renderToStaticMarkup(
      <GovernanceDataSurface
        state={state}
        traces={[]}
        verification={{ valid: true, recordCount: 1, from: now, to: now, lastHash: 'a'.repeat(64) }}
        canAdmin={false}
        busy=""
        onVerify={noop}
        onFindTrace={noop}
        onNewModel={noop}
        onNewPrompt={noop}
        onNewBudget={noop}
        onModelAction={noop}
      />,
    );

    expect(html).not.toContain('private prompt body');
    expect(html).not.toContain('secret://');
    expect(html).not.toContain('Register</button>');
  });
});
