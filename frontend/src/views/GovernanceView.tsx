import {
  AuditOutlined,
  ControlOutlined,
  DollarOutlined,
  ExperimentOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';

import {
  CurrentUser,
  GovernanceAuditEvent,
  GovernanceAuditVerification,
  GovernanceBudget,
  GovernanceMembership,
  GovernanceModel,
  GovernancePrompt,
  GovernanceTenant,
  GovernanceTraceLink,
  GovernanceUsage,
  createGovernanceBudget,
  createGovernanceModel,
  createGovernancePrompt,
  enableGovernanceModel,
  getGovernanceTrace,
  listGovernanceAuditEvents,
  listGovernanceBudgets,
  listGovernanceMemberships,
  listGovernanceModels,
  listGovernancePrompts,
  listGovernanceTenants,
  listGovernanceUsage,
  reviewGovernanceModel,
  suspendGovernanceModel,
  verifyGovernanceAudit,
} from '../api';
import { errorMessage, formatDate, shortId } from '../format';

const { Text, Title } = Typography;

export interface GovernanceState {
  tenant?: GovernanceTenant;
  memberships: GovernanceMembership[];
  audits: GovernanceAuditEvent[];
  models: GovernanceModel[];
  prompts: GovernancePrompt[];
  usage: GovernanceUsage[];
  budgets: GovernanceBudget[];
}

const EMPTY: GovernanceState = {
  memberships: [],
  audits: [],
  models: [],
  prompts: [],
  usage: [],
  budgets: [],
};

export function GovernanceView({ token, user }: { token: string; user: CurrentUser }) {
  const [notice, contextHolder] = message.useMessage();
  const [state, setState] = useState<GovernanceState>(EMPTY);
  const [traces, setTraces] = useState<GovernanceTraceLink[]>([]);
  const [verification, setVerification] = useState<GovernanceAuditVerification>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [modelModal, setModelModal] = useState(false);
  const [promptModal, setPromptModal] = useState(false);
  const [budgetModal, setBudgetModal] = useState(false);
  const [modelForm] = Form.useForm();
  const [promptForm] = Form.useForm();
  const [budgetForm] = Form.useForm();
  const canAdmin = user.roles.includes('ROLE_ADMIN');

  const load = useCallback(async () => {
    setError('');
    try {
      const tenant = (await listGovernanceTenants(token)).items[0];
      if (!tenant) throw new Error('No Governance tenant is assigned');
      const [memberships, audits, models, prompts, usage, budgets] = await Promise.all([
        listGovernanceMemberships(token, tenant.id),
        listGovernanceAuditEvents(token),
        listGovernanceModels(token),
        listGovernancePrompts(token),
        listGovernanceUsage(token),
        listGovernanceBudgets(token),
      ]);
      setState({
        tenant,
        memberships: memberships.items,
        audits: audits.items,
        models: models.items,
        prompts: prompts.items,
        usage: usage.items,
        budgets: budgets.items,
      });
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function mutate(key: string, operation: () => Promise<unknown>, success: string) {
    setBusy(key);
    setError('');
    try {
      await operation();
      notice.success(success);
      await load();
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  async function verifyAudit() {
    const to = new Date();
    const from = new Date(to.getTime() - 31 * 24 * 60 * 60 * 1000);
    setBusy('verify');
    setError('');
    try {
      setVerification(await verifyGovernanceAudit(token, from.toISOString(), to.toISOString()));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  async function findTrace(traceId: string) {
    setBusy('trace');
    setError('');
    try {
      setTraces((await getGovernanceTrace(token, traceId)).items);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  if (loading)
    return (
      <div className="centered">
        <Spin />
      </div>
    );

  return (
    <div className="page-stack governance-page">
      {contextHolder}
      <div className="page-toolbar governance-heading">
        <div>
          <Title level={3}>Governance control plane</Title>
          <Text type="secondary">
            {state.tenant ? `${state.tenant.displayName} / ${state.tenant.policyVersion}` : 'Tenant unavailable'}
          </Text>
        </div>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => void load()}
          loading={busy === 'reload'}
          aria-label="Refresh"
        />
      </div>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <GovernanceDataSurface
        state={state}
        traces={traces}
        verification={verification}
        canAdmin={canAdmin}
        busy={busy}
        onVerify={() => void verifyAudit()}
        onFindTrace={(traceId) => void findTrace(traceId)}
        onNewModel={() => setModelModal(true)}
        onNewPrompt={() => setPromptModal(true)}
        onNewBudget={() => setBudgetModal(true)}
        onModelAction={(model, action) =>
          void mutate(
            `${action}-${model.id}`,
            () =>
              action === 'review'
                ? reviewGovernanceModel(token, model)
                : action === 'enable'
                  ? enableGovernanceModel(token, model)
                  : suspendGovernanceModel(token, model),
            `Model ${action} completed`,
          )
        }
      />
      <Modal
        title="Register model policy"
        open={modelModal}
        onCancel={() => setModelModal(false)}
        onOk={() => modelForm.submit()}
        confirmLoading={busy === 'model-create'}
        destroyOnHidden
      >
        <Form
          form={modelForm}
          layout="vertical"
          requiredMark={false}
          initialValues={{ capabilities: ['CHAT'], routingLabels: [] }}
          onFinish={(value) =>
            void mutate('model-create', () => createGovernanceModel(token, value), 'Model policy registered').then(() =>
              setModelModal(false),
            )
          }
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="providerRef" label="Provider reference" rules={[{ required: true }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="secretRef" label="Secret reference" rules={[{ required: true }]}>
            <Input placeholder="secret://env/MODEL_API_KEY" maxLength={256} />
          </Form.Item>
          <Form.Item name="capabilities" label="Capabilities" rules={[{ required: true }]}>
            <Select
              mode="tags"
              tokenSeparators={[',']}
              options={['CHAT', 'EMBEDDING', 'RERANK'].map((value) => ({ value }))}
            />
          </Form.Item>
          <Form.Item name="routingLabels" label="Routing labels">
            <Select mode="tags" tokenSeparators={[',']} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="Create Prompt draft"
        open={promptModal}
        onCancel={() => setPromptModal(false)}
        onOk={() => promptForm.submit()}
        confirmLoading={busy === 'prompt-create'}
        destroyOnHidden
      >
        <Form
          form={promptForm}
          layout="vertical"
          requiredMark={false}
          onFinish={(value) =>
            void mutate('prompt-create', () => createGovernancePrompt(token, value), 'Prompt draft created').then(
              () => {
                promptForm.resetFields();
                setPromptModal(false);
              },
            )
          }
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="purpose" label="Purpose" rules={[{ required: true }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="content" label="Draft content" rules={[{ required: true }]}>
            <Input.TextArea rows={8} maxLength={65536} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="Create budget"
        open={budgetModal}
        onCancel={() => setBudgetModal(false)}
        onOk={() => budgetForm.submit()}
        confirmLoading={busy === 'budget-create'}
        destroyOnHidden
      >
        <Form
          form={budgetForm}
          layout="vertical"
          requiredMark={false}
          initialValues={{ currency: 'USD', window: 'MONTHLY' }}
          onFinish={(value) =>
            void mutate('budget-create', () => createGovernanceBudget(token, value), 'Budget created').then(() =>
              setBudgetModal(false),
            )
          }
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="currency" label="Currency" rules={[{ required: true }]}>
            <Input maxLength={3} />
          </Form.Item>
          <Form.Item name="limit" label="Limit" rules={[{ required: true }]}>
            <InputNumber min={0.000001} precision={6} stringMode={false} />
          </Form.Item>
          <Form.Item name="window" label="Window" rules={[{ required: true }]}>
            <Select options={['DAILY', 'WEEKLY', 'MONTHLY', 'EXECUTION'].map((value) => ({ value }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export function GovernanceDataSurface({
  state,
  traces,
  verification,
  canAdmin,
  busy,
  onVerify,
  onFindTrace,
  onNewModel,
  onNewPrompt,
  onNewBudget,
  onModelAction,
}: {
  state: GovernanceState;
  traces: GovernanceTraceLink[];
  verification?: GovernanceAuditVerification;
  canAdmin: boolean;
  busy: string;
  onVerify: () => void;
  onFindTrace: (traceId: string) => void;
  onNewModel: () => void;
  onNewPrompt: () => void;
  onNewBudget: () => void;
  onModelAction: (model: GovernanceModel, action: 'review' | 'enable' | 'suspend') => void;
}) {
  return (
    <Tabs
      className="governance-tabs"
      items={[
        {
          key: 'scope',
          label: 'Tenant',
          icon: <TeamOutlined />,
          children: <TenantSurface state={state} />,
        },
        {
          key: 'audit',
          label: 'Audit',
          icon: <AuditOutlined />,
          children: (
            <section className="governance-surface">
              <SurfaceHeader title="Audit evidence" count={state.audits.length}>
                <Button
                  icon={<SafetyCertificateOutlined />}
                  onClick={onVerify}
                  loading={busy === 'verify'}
                  disabled={!canAdmin}
                >
                  Verify 31 days
                </Button>
              </SurfaceHeader>
              {verification && (
                <Alert
                  type={verification.valid ? 'success' : 'error'}
                  showIcon
                  message={verification.valid ? 'Hash chain verified' : 'Audit integrity failure'}
                  description={`${verification.recordCount} records checked`}
                />
              )}
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={state.audits}
                columns={[
                  { title: 'Time', dataIndex: 'occurredAt', render: formatDate },
                  { title: 'Action', dataIndex: 'action' },
                  { title: 'Resource', render: (_, row) => `${row.resourceType} / ${shortId(row.resourceId)}` },
                  { title: 'Outcome', dataIndex: 'outcome', render: statusTag },
                  { title: 'Trace', dataIndex: 'traceId', render: shortId },
                  { title: 'Hash', dataIndex: 'recordHash', render: shortId },
                ]}
              />
            </section>
          ),
        },
        {
          key: 'models',
          label: 'Models',
          icon: <ExperimentOutlined />,
          children: (
            <section className="governance-surface">
              <SurfaceHeader title="Model registry" count={state.models.length}>
                {canAdmin && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={onNewModel}>
                    Register
                  </Button>
                )}
              </SurfaceHeader>
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={state.models}
                columns={[
                  { title: 'Name', dataIndex: 'name' },
                  { title: 'Provider', dataIndex: 'providerId', render: shortId },
                  { title: 'Version', dataIndex: 'currentVersion' },
                  { title: 'State', dataIndex: 'state', render: statusTag },
                  { title: 'Updated', dataIndex: 'updatedAt', render: formatDate },
                  {
                    title: '',
                    width: 130,
                    render: (_, model) =>
                      canAdmin && <ModelAction model={model} busy={busy} onAction={onModelAction} />,
                  },
                ]}
              />
            </section>
          ),
        },
        {
          key: 'prompts',
          label: 'Prompts',
          icon: <ControlOutlined />,
          children: (
            <section className="governance-surface">
              <SurfaceHeader title="Prompt registry" count={state.prompts.length}>
                {canAdmin && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={onNewPrompt}>
                    New draft
                  </Button>
                )}
              </SurfaceHeader>
              <Alert type="info" showIcon message="Prompt content is encrypted and omitted from this registry view." />
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={state.prompts}
                columns={[
                  { title: 'Name', dataIndex: 'name' },
                  { title: 'Purpose', dataIndex: 'purpose' },
                  { title: 'State', dataIndex: 'state', render: statusTag },
                  { title: 'Revision', dataIndex: 'revision' },
                  { title: 'Updated', dataIndex: 'updatedAt', render: formatDate },
                ]}
              />
            </section>
          ),
        },
        {
          key: 'cost',
          label: 'Usage & budgets',
          icon: <DollarOutlined />,
          children: <CostSurface state={state} canAdmin={canAdmin} onNewBudget={onNewBudget} />,
        },
        {
          key: 'traces',
          label: 'Traces',
          icon: <SearchOutlined />,
          children: <TraceSurface traces={traces} busy={busy} onFind={onFindTrace} />,
        },
      ]}
    />
  );
}

function TenantSurface({ state }: { state: GovernanceState }) {
  if (!state.tenant) return <Empty description="Tenant unavailable" />;
  return (
    <section className="governance-surface">
      <SurfaceHeader title="Server-derived scope" count={state.memberships.length} />
      <Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 4 }}>
        <Descriptions.Item label="Tenant">{state.tenant.displayName}</Descriptions.Item>
        <Descriptions.Item label="ID">{state.tenant.id}</Descriptions.Item>
        <Descriptions.Item label="State">{statusTag(state.tenant.state)}</Descriptions.Item>
        <Descriptions.Item label="Policy">{state.tenant.policyVersion}</Descriptions.Item>
      </Descriptions>
      <Table
        className="governance-membership-table"
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 760 }}
        dataSource={state.memberships}
        columns={[
          { title: 'Principal', dataIndex: 'principalId', width: 330 },
          {
            title: 'Roles',
            dataIndex: 'roles',
            width: 140,
            render: (roles: string[]) => roles.map((role) => <Tag key={role}>{role}</Tag>),
          },
          { title: 'State', dataIndex: 'state', width: 130, render: statusTag },
          { title: 'Policy', dataIndex: 'policyVersion', width: 160 },
        ]}
      />
    </section>
  );
}

function CostSurface({
  state,
  canAdmin,
  onNewBudget,
}: {
  state: GovernanceState;
  canAdmin: boolean;
  onNewBudget: () => void;
}) {
  return (
    <section className="governance-surface">
      <SurfaceHeader title="Budgets" count={state.budgets.length}>
        {canAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={onNewBudget}>
            Create
          </Button>
        )}
      </SurfaceHeader>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={state.budgets}
        columns={[
          { title: 'Name', dataIndex: 'name' },
          { title: 'Window', dataIndex: 'windowType' },
          { title: 'Limit', render: (_, row) => `${row.currency} ${row.limitAmount}` },
          { title: 'Updated', dataIndex: 'updatedAt', render: formatDate },
        ]}
      />
      <SurfaceHeader title="Attributed usage" count={state.usage.length} />
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={state.usage}
        columns={[
          { title: 'Time', dataIndex: 'createdAt', render: formatDate },
          { title: 'Execution', dataIndex: 'executionId', render: shortId },
          { title: 'Units', render: (_, row) => `${row.inputUnits} in / ${row.outputUnits} out` },
          { title: 'Cost', render: (_, row) => `${row.currency} ${row.calculatedAmount}` },
          { title: 'Trace', dataIndex: 'traceId', render: shortId },
        ]}
      />
    </section>
  );
}

function TraceSurface({
  traces,
  busy,
  onFind,
}: {
  traces: GovernanceTraceLink[];
  busy: string;
  onFind: (traceId: string) => void;
}) {
  return (
    <section className="governance-surface">
      <SurfaceHeader title="Trace explorer" count={traces.length} />
      <Form className="governance-search" layout="inline" onFinish={({ traceId }) => onFind(traceId)}>
        <Form.Item name="traceId" rules={[{ required: true }, { pattern: /^[a-f0-9]{16,32}$/ }]}>
          <Input placeholder="16-32 lowercase hex characters" maxLength={32} />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={busy === 'trace'}>
          Find
        </Button>
      </Form>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={traces}
        columns={[
          { title: 'Time', dataIndex: 'occurredAt', render: formatDate },
          { title: 'Module', dataIndex: 'module' },
          { title: 'Operation', dataIndex: 'operation' },
          { title: 'Outcome', dataIndex: 'outcome', render: statusTag },
          { title: 'Duration', dataIndex: 'durationMs', render: (value) => (value == null ? '-' : `${value} ms`) },
          { title: 'Request', dataIndex: 'requestId', render: shortId },
        ]}
      />
    </section>
  );
}

function ModelAction({
  model,
  busy,
  onAction,
}: {
  model: GovernanceModel;
  busy: string;
  onAction: (model: GovernanceModel, action: 'review' | 'enable' | 'suspend') => void;
}) {
  const action =
    model.state === 'DRAFT'
      ? 'review'
      : model.state === 'REVIEWED' || model.state === 'SUSPENDED'
        ? 'enable'
        : 'suspend';
  return (
    <Popconfirm title={`${action} ${model.name}?`} onConfirm={() => onAction(model, action)}>
      <Button size="small" loading={busy === `${action}-${model.id}`}>
        {action}
      </Button>
    </Popconfirm>
  );
}

function SurfaceHeader({ title, count, children }: { title: string; count: number; children?: React.ReactNode }) {
  return (
    <div className="governance-surface-header">
      <Space>
        <Title level={4}>{title}</Title>
        <Tag>{count}</Tag>
      </Space>
      {children}
    </div>
  );
}

function statusTag(value: string) {
  const success = ['ACTIVE', 'SUCCESS', 'ENABLED', 'PUBLISHED', 'VALID'].includes(value);
  const danger = ['DENIED', 'FAILURE', 'SUSPENDED', 'DEPRECATED'].includes(value);
  return <Tag color={success ? 'green' : danger ? 'red' : 'gold'}>{value}</Tag>;
}
