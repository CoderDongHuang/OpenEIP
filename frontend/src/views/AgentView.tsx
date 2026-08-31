import {
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  ExportOutlined,
  InboxOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { FormInstance } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import {
  AgentDefinitionV2,
  AgentMemoryV2,
  AgentRunEventV2,
  AgentRunV2,
  AgentToolGrantV2,
  AgentToolV2,
  AgentVersionV2,
  EvaluationDatasetV2,
  EvaluationRunV2,
  EvaluationSuiteV2,
  McpServerV2,
  archiveAgentDefinitionV2,
  cancelEvaluationRunV2,
  commandAgentRunV2,
  commandMcpServerV2,
  createAgentCandidateV2,
  createAgentDefinitionV2,
  createAgentRunV2,
  createAgentToolGrantV2,
  createEvaluationDatasetV2,
  createEvaluationRunV2,
  createMcpServerV2,
  deleteAgentMemoryV2,
  disableMcpServerV2,
  exportAgentMemoriesV2,
  listAgentDefinitionsV2,
  listAgentMemoriesV2,
  listAgentRunEventsV2,
  listAgentRunsV2,
  listAgentToolGrantsV2,
  listAgentToolsV2,
  listAgentVersionsV2,
  listEvaluationDatasetsV2,
  listEvaluationSuitesV2,
  listMcpServersV2,
  publishAgentVersionV2,
  quarantineAgentMemoryV2,
  restoreAgentVersionV2,
  revokeAgentToolGrantV2,
  updateAgentDefinitionV2,
} from '../api';
import { errorMessage, formatDate, shortId } from '../format';

const { Text, Title } = Typography;

interface PlatformState {
  definitions: AgentDefinitionV2[];
  versions: AgentVersionV2[];
  runs: AgentRunV2[];
  tools: AgentToolV2[];
  grants: AgentToolGrantV2[];
  memories: AgentMemoryV2[];
  servers: McpServerV2[];
  datasets: EvaluationDatasetV2[];
  suites: EvaluationSuiteV2[];
}

const EMPTY_STATE: PlatformState = {
  definitions: [],
  versions: [],
  runs: [],
  tools: [],
  grants: [],
  memories: [],
  servers: [],
  datasets: [],
  suites: [],
};

export function AgentView({ token }: { token: string }) {
  const [api, contextHolder] = message.useMessage();
  const [state, setState] = useState<PlatformState>(EMPTY_STATE);
  const [events, setEvents] = useState<AgentRunEventV2[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState('');
  const [selectedRunId, setSelectedRunId] = useState('');
  const [lastEvaluation, setLastEvaluation] = useState<EvaluationRunV2>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [agentModal, setAgentModal] = useState(false);
  const [runModal, setRunModal] = useState(false);
  const [grantModal, setGrantModal] = useState(false);
  const [serverModal, setServerModal] = useState(false);
  const [datasetModal, setDatasetModal] = useState(false);
  const [publishModal, setPublishModal] = useState(false);
  const [publishRunId, setPublishRunId] = useState('');
  const [memoryState, setMemoryState] = useState<string>();
  const [agentForm] = Form.useForm();
  const [runForm] = Form.useForm();
  const [grantForm] = Form.useForm();
  const [serverForm] = Form.useForm();
  const [datasetForm] = Form.useForm();
  const [evaluationForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const load = useCallback(async () => {
    setError('');
    try {
      const definitions = (await listAgentDefinitionsV2(token)).items;
      const [versionPages, runs, tools, grants, memories, servers, datasets, suites] = await Promise.all([
        Promise.all(definitions.map((item) => listAgentVersionsV2(token, item.id))),
        listAgentRunsV2(token),
        listAgentToolsV2(token),
        listAgentToolGrantsV2(token),
        listAgentMemoriesV2(token, memoryState),
        listMcpServersV2(token),
        listEvaluationDatasetsV2(token),
        listEvaluationSuitesV2(token),
      ]);
      setState({
        definitions,
        versions: versionPages.flatMap((page) => page.items),
        runs: runs.items,
        tools: tools.items,
        grants: grants.items,
        memories: memories.items,
        servers: servers.items,
        datasets: datasets.items,
        suites: suites.items,
      });
      setSelectedAgentId((current) =>
        definitions.some((item) => item.id === current) ? current : definitions[0]?.id || '',
      );
      setSelectedRunId((current) =>
        runs.items.some((item) => item.id === current) ? current : runs.items[0]?.id || '',
      );
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [memoryState, token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selectedRunId) {
      setEvents([]);
      return;
    }
    listAgentRunEventsV2(token, selectedRunId)
      .then((page) => setEvents(page.items))
      .catch((reason) => setError(errorMessage(reason)));
  }, [selectedRunId, token]);

  const selectedAgent = state.definitions.find((item) => item.id === selectedAgentId);
  const selectedRun = state.runs.find((item) => item.id === selectedRunId);
  const selectedVersions = state.versions.filter((item) => item.agentId === selectedAgentId);
  const publishedVersions = state.versions.filter((item) => item.status === 'PUBLISHED' && item.version !== null);

  useEffect(() => {
    editForm.setFieldsValue({ name: selectedAgent?.name, description: selectedAgent?.description });
  }, [editForm, selectedAgent]);

  async function mutate(key: string, operation: () => Promise<unknown>, success: string) {
    setBusy(key);
    setError('');
    try {
      await operation();
      api.success(success);
      await load();
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

  const tabs = [
    {
      key: 'definitions',
      label: 'Definitions',
      icon: <RobotOutlined />,
      children: (
        <DefinitionsSurface
          definitions={state.definitions}
          selected={selectedAgent}
          versions={selectedVersions}
          busy={busy}
          editForm={editForm}
          onSelect={setSelectedAgentId}
          onNew={() => setAgentModal(true)}
          onSave={(values) =>
            selectedAgent &&
            void mutate('agent-save', () => updateAgentDefinitionV2(token, selectedAgent, values), 'Agent draft saved')
          }
          onCandidate={() =>
            selectedAgent &&
            void mutate('candidate', () => createAgentCandidateV2(token, selectedAgent), 'Candidate snapshot created')
          }
          onPublish={() => setPublishModal(true)}
          onRestore={(version) =>
            selectedAgent &&
            void mutate(
              `restore-${version}`,
              () => restoreAgentVersionV2(token, selectedAgent, version),
              `Version ${version} copied to draft`,
            )
          }
          onArchive={() =>
            selectedAgent &&
            void mutate('archive', () => archiveAgentDefinitionV2(token, selectedAgent), 'Agent archived')
          }
        />
      ),
    },
    {
      key: 'runs',
      label: 'Run timeline',
      icon: <ClockCircleOutlined />,
      children: (
        <RunsSurface
          runs={state.runs}
          selected={selectedRun}
          events={events}
          busy={busy}
          onSelect={setSelectedRunId}
          onNew={() => setRunModal(true)}
          onCommand={(command) =>
            selectedRun &&
            void mutate(
              `run-${command}`,
              () => commandAgentRunV2(token, selectedRun, command),
              `Run ${command} accepted`,
            )
          }
        />
      ),
    },
    {
      key: 'tools',
      label: 'Tool permissions',
      icon: <ToolOutlined />,
      children: (
        <ToolsSurface
          tools={state.tools}
          grants={state.grants}
          versions={publishedVersions}
          busy={busy}
          onNew={() => setGrantModal(true)}
          onRevoke={(grant) =>
            void mutate(`revoke-${grant.id}`, () => revokeAgentToolGrantV2(token, grant), 'Tool grant revoked')
          }
        />
      ),
    },
    {
      key: 'memory',
      label: 'Memory',
      icon: <DatabaseOutlined />,
      children: (
        <MemorySurface
          entries={state.memories}
          state={memoryState}
          busy={busy}
          onState={setMemoryState}
          onExport={() =>
            void mutate('memory-export', () => exportAgentMemoriesV2(token), 'Memory metadata export queued')
          }
          onQuarantine={(entry) =>
            void mutate(
              `quarantine-${entry.id}`,
              () => quarantineAgentMemoryV2(token, entry),
              'Memory entry quarantined',
            )
          }
          onDelete={(entry) =>
            void mutate(`delete-${entry.id}`, () => deleteAgentMemoryV2(token, entry), 'Memory purge queued')
          }
        />
      ),
    },
    {
      key: 'mcp',
      label: 'MCP Servers',
      icon: <ApiOutlined />,
      children: (
        <McpSurface
          servers={state.servers}
          busy={busy}
          onNew={() => setServerModal(true)}
          onCommand={(server, command) =>
            void mutate(
              `${command}-${server.id}`,
              () => commandMcpServerV2(token, server, command),
              command === 'test' ? 'MCP policy test completed' : 'MCP discovery completed',
            )
          }
          onDisable={(server) =>
            void mutate(`disable-${server.id}`, () => disableMcpServerV2(token, server), 'MCP Server disabled')
          }
        />
      ),
    },
    {
      key: 'evaluation',
      label: 'Evaluation',
      icon: <ExperimentOutlined />,
      children: (
        <EvaluationSurface
          datasets={state.datasets}
          suites={state.suites}
          versions={state.versions}
          result={lastEvaluation}
          form={evaluationForm}
          busy={busy}
          onNewDataset={() => setDatasetModal(true)}
          onRun={(values) => {
            setBusy('evaluation-run');
            setError('');
            createEvaluationRunV2(token, { ...values, repeatCount: values.repeatCount || 1 })
              .then((result) => {
                setLastEvaluation(result);
                api.success('Evaluation completed');
              })
              .catch((reason) => setError(errorMessage(reason)))
              .finally(() => setBusy(''));
          }}
          onCancel={() =>
            lastEvaluation &&
            void mutate(
              'evaluation-cancel',
              async () => setLastEvaluation(await cancelEvaluationRunV2(token, lastEvaluation)),
              'Evaluation cancellation accepted',
            )
          }
        />
      ),
    },
  ];

  return (
    <div className="page-stack agent-platform-page">
      {contextHolder}
      <section className="agent-platform-heading">
        <div>
          <Title level={3}>Production Agent Platform</Title>
          <Space wrap>
            <Tag>{state.definitions.length} definitions</Tag>
            <Tag color="processing">{state.runs.filter((item) => !isTerminal(item.status)).length} active runs</Tag>
            <Tag color="success">{state.suites.length} evaluation suites</Tag>
          </Space>
        </div>
        <Tooltip title="Refresh Agent platform">
          <Button icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Refresh Agent platform" />
        </Tooltip>
      </section>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <Tabs className="agent-platform-tabs" items={tabs} destroyInactiveTabPane={false} />

      <Modal title="New Agent" open={agentModal} onCancel={() => setAgentModal(false)} footer={null} destroyOnClose>
        <Form
          form={agentForm}
          layout="vertical"
          onFinish={(values) => {
            void mutate('agent-create', () => createAgentDefinitionV2(token, values), 'Agent draft created').then(
              () => {
                setAgentModal(false);
                agentForm.resetFields();
              },
            );
          }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="type" label="Type" rules={[{ required: true }]}>
            <Select options={['DOCUMENT', 'SQL', 'BI', 'SEARCH', 'WORKFLOW', 'CUSTOM'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea maxLength={1000} rows={3} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy === 'agent-create'} block>
            Create draft
          </Button>
        </Form>
      </Modal>

      <Modal
        title="Publish evaluated candidate"
        open={publishModal}
        okText="Publish"
        okButtonProps={{ disabled: !publishRunId.trim(), loading: busy === 'publish' }}
        onCancel={() => setPublishModal(false)}
        onOk={() => {
          if (!selectedAgent) return;
          void mutate(
            'publish',
            () => publishAgentVersionV2(token, selectedAgent, publishRunId.trim()),
            'Agent version published',
          ).then(() => {
            setPublishModal(false);
            setPublishRunId('');
          });
        }}
      >
        <Input
          value={publishRunId}
          onChange={(event) => setPublishRunId(event.target.value)}
          placeholder="Passing Evaluation run ID"
        />
      </Modal>

      <Modal title="Start Agent run" open={runModal} onCancel={() => setRunModal(false)} footer={null} destroyOnClose>
        <Form
          form={runForm}
          layout="vertical"
          initialValues={{ maxSteps: 32, maxDurationSeconds: 600, maxToolCalls: 64, maxWorkers: 4 }}
          onFinish={(values) => {
            const version = state.versions.find((item) => item.id === values.agentVersionId);
            if (!version || version.version === null) return;
            void mutate(
              'run-create',
              () =>
                createAgentRunV2(token, version.agentId, {
                  agentVersion: version.version as number,
                  input: values.input,
                  resourceHandles: [],
                  budget: {
                    maxSteps: values.maxSteps,
                    maxDurationSeconds: values.maxDurationSeconds,
                    maxToolCalls: values.maxToolCalls,
                    maxWorkers: values.maxWorkers,
                  },
                }),
              'Agent run started',
            ).then(() => {
              setRunModal(false);
              runForm.resetFields();
            });
          }}
        >
          <Form.Item name="agentVersionId" label="Published Agent version" rules={[{ required: true }]}>
            <Select options={versionOptions(publishedVersions, state.definitions)} />
          </Form.Item>
          <Form.Item name="input" label="Task" rules={[{ required: true }]}>
            <Input.TextArea rows={4} maxLength={32000} />
          </Form.Item>
          <div className="agent-budget-grid">
            {['maxSteps', 'maxDurationSeconds', 'maxToolCalls', 'maxWorkers'].map((name) => (
              <Form.Item key={name} name={name} label={budgetLabel(name)}>
                <InputNumber min={name === 'maxWorkers' || name === 'maxToolCalls' ? 0 : 1} max={budgetMax(name)} />
              </Form.Item>
            ))}
          </div>
          <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={busy === 'run-create'} block>
            Start run
          </Button>
        </Form>
      </Modal>

      <Modal
        title="Grant Tool permission"
        open={grantModal}
        onCancel={() => setGrantModal(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={grantForm}
          layout="vertical"
          initialValues={{ approvalMode: 'POLICY', resourceSelector: '{}', argumentConstraints: '{}' }}
          onFinish={(values) => {
            const tool = state.tools.find((item) => item.id === values.toolVersionId);
            void mutate(
              'grant-create',
              () =>
                createAgentToolGrantV2(token, {
                  agentVersionId: values.agentVersionId,
                  toolVersionId: values.toolVersionId,
                  operations: values.operations || tool?.operations || [],
                  resourceSelector: JSON.parse(values.resourceSelector),
                  argumentConstraints: JSON.parse(values.argumentConstraints),
                  approvalMode: values.approvalMode,
                }),
              'Tool permission granted',
            ).then(() => {
              setGrantModal(false);
              grantForm.resetFields();
            });
          }}
        >
          <Form.Item name="agentVersionId" label="Agent version" rules={[{ required: true }]}>
            <Select options={versionOptions(publishedVersions, state.definitions)} />
          </Form.Item>
          <Form.Item name="toolVersionId" label="Tool version" rules={[{ required: true }]}>
            <Select
              options={state.tools.map((item) => ({ value: item.id, label: `${item.toolKey} v${item.version}` }))}
            />
          </Form.Item>
          <Form.Item name="operations" label="Operations" rules={[{ required: true }]}>
            <Select mode="tags" />
          </Form.Item>
          <Form.Item name="approvalMode" label="Approval" rules={[{ required: true }]}>
            <Select options={['NONE', 'POLICY', 'PER_CALL'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name="resourceSelector" label="Resource selector" rules={[jsonRule]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="argumentConstraints" label="Argument constraints" rules={[jsonRule]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy === 'grant-create'} block>
            Create grant
          </Button>
        </Form>
      </Modal>

      <Modal
        title="Register MCP Server"
        open={serverModal}
        onCancel={() => setServerModal(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={serverForm}
          layout="vertical"
          initialValues={{ transport: 'STREAMABLE_HTTP', authType: 'NONE' }}
          onFinish={(values) => {
            void mutate('server-create', () => createMcpServerV2(token, values), 'MCP Server registered').then(() => {
              setServerModal(false);
              serverForm.resetFields();
            });
          }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="transport" label="Transport" rules={[{ required: true }]}>
            <Select options={['STDIO', 'STREAMABLE_HTTP'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name="endpoint" label="Endpoint" rules={[{ required: true }]}>
            <Input maxLength={2048} />
          </Form.Item>
          <Form.Item name="authType" label="Authentication" rules={[{ required: true }]}>
            <Select options={['NONE', 'OAUTH2', 'BEARER_REF', 'MTLS_REF'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name="credentialRef" label="Credential reference">
            <Input placeholder="secret://tenant/path" maxLength={249} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy === 'server-create'} block>
            Register Server
          </Button>
        </Form>
      </Modal>

      <Modal
        title="New Evaluation dataset"
        open={datasetModal}
        onCancel={() => setDatasetModal(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={datasetForm}
          layout="vertical"
          onFinish={(values) => {
            void mutate(
              'dataset-create',
              () => createEvaluationDatasetV2(token, values.name, values.description || ''),
              'Evaluation dataset created',
            ).then(() => {
              setDatasetModal(false);
              datasetForm.resetFields();
            });
          }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea maxLength={1000} rows={3} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy === 'dataset-create'} block>
            Create dataset
          </Button>
        </Form>
      </Modal>
    </div>
  );
}

export function DefinitionsSurface(props: {
  definitions: AgentDefinitionV2[];
  selected?: AgentDefinitionV2;
  versions: AgentVersionV2[];
  busy: string;
  editForm?: FormInstance;
  onSelect: (id: string) => void;
  onNew: () => void;
  onSave: (values: Record<string, unknown>) => void;
  onCandidate: () => void;
  onPublish: () => void;
  onRestore: (version: number) => void;
  onArchive: () => void;
}) {
  const owner = props.selected && props.selected.ownerId !== 'system';
  return (
    <div className="agent-definition-layout">
      <aside className="agent-definition-rail">
        <SectionHeader
          title="Agent definitions"
          action={
            <Button type="primary" icon={<PlusOutlined />} onClick={props.onNew}>
              New
            </Button>
          }
        />
        <List
          dataSource={props.definitions}
          locale={{ emptyText: 'No Agent definitions' }}
          renderItem={(item) => (
            <List.Item
              className={item.id === props.selected?.id ? 'selected' : ''}
              onClick={() => props.onSelect(item.id)}
            >
              <List.Item.Meta
                title={item.name}
                description={
                  <Space>
                    <StatusTag value={item.status} />
                    <Text type="secondary">{item.type}</Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </aside>
      <section className="agent-definition-main">
        {!props.selected ? (
          <Empty />
        ) : (
          <>
            <SectionHeader
              title={props.selected.name}
              action={
                <Space wrap>
                  <Button
                    icon={<ExperimentOutlined />}
                    onClick={props.onCandidate}
                    disabled={!owner || props.selected.status === 'ARCHIVED'}
                    loading={props.busy === 'candidate'}
                  >
                    Create candidate
                  </Button>
                  <Button
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={props.onPublish}
                    disabled={!owner || !props.versions.some((item) => item.status === 'CANDIDATE')}
                  >
                    Publish
                  </Button>
                  <Popconfirm title="Archive this Agent?" onConfirm={props.onArchive}>
                    <Button
                      danger
                      icon={<InboxOutlined />}
                      disabled={!owner || props.selected.status === 'ARCHIVED'}
                      loading={props.busy === 'archive'}
                    >
                      Archive
                    </Button>
                  </Popconfirm>
                </Space>
              }
            />
            {!owner && <Alert type="info" showIcon message="Built-in Agent versions are managed by the platform." />}
            <Form form={props.editForm} layout="vertical" className="agent-draft-form" onFinish={props.onSave}>
              <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                <Input disabled={!owner} maxLength={120} />
              </Form.Item>
              <Form.Item name="description" label="Description">
                <Input.TextArea disabled={!owner} maxLength={1000} rows={2} />
              </Form.Item>
              {owner && (
                <Button htmlType="submit" loading={props.busy === 'agent-save'}>
                  Save draft
                </Button>
              )}
            </Form>
            <Title level={5}>Immutable snapshots</Title>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              scroll={{ x: 760 }}
              dataSource={props.versions}
              columns={[
                { title: 'State', dataIndex: 'status', width: 120, render: (value) => <StatusTag value={value} /> },
                {
                  title: 'Version',
                  dataIndex: 'version',
                  width: 90,
                  render: (value) => (value === null ? 'Candidate' : `v${value}`),
                },
                { title: 'Digest', dataIndex: 'digest', render: (value) => <Text code>{shortId(value)}</Text> },
                { title: 'Draft rev', dataIndex: 'sourceDraftRevision', width: 100 },
                { title: 'Created', dataIndex: 'createdAt', width: 190, render: formatDate },
                {
                  title: '',
                  width: 100,
                  render: (_, item) =>
                    item.status === 'PUBLISHED' && item.version !== null && owner ? (
                      <Button
                        size="small"
                        onClick={() => props.onRestore(item.version as number)}
                        loading={props.busy === `restore-${item.version}`}
                      >
                        Restore
                      </Button>
                    ) : null,
                },
              ]}
            />
          </>
        )}
      </section>
    </div>
  );
}

export function RunsSurface(props: {
  runs: AgentRunV2[];
  selected?: AgentRunV2;
  events: AgentRunEventV2[];
  busy: string;
  onSelect: (id: string) => void;
  onNew: () => void;
  onCommand: (command: 'pause' | 'resume' | 'cancel') => void;
}) {
  return (
    <div className="agent-run-layout">
      <aside className="agent-run-rail">
        <SectionHeader
          title="Runs"
          action={
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={props.onNew}>
              Start
            </Button>
          }
        />
        <List
          dataSource={props.runs}
          locale={{ emptyText: 'No Agent runs' }}
          renderItem={(run) => (
            <List.Item
              className={run.id === props.selected?.id ? 'selected' : ''}
              onClick={() => props.onSelect(run.id)}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Text code>{shortId(run.id)}</Text>
                    <StatusTag value={run.status} />
                  </Space>
                }
                description={formatDate(run.createdAt)}
              />
            </List.Item>
          )}
        />
      </aside>
      <section className="agent-run-main">
        {!props.selected ? (
          <Empty description="No run selected" />
        ) : (
          <>
            <SectionHeader
              title={`Run ${shortId(props.selected.id)}`}
              action={
                <Space>
                  <Tooltip title="Pause">
                    <Button
                      icon={<PauseCircleOutlined />}
                      aria-label="Pause run"
                      disabled={!['QUEUED', 'PLANNING', 'EXECUTING', 'REFLECTING'].includes(props.selected.status)}
                      loading={props.busy === 'run-pause'}
                      onClick={() => props.onCommand('pause')}
                    />
                  </Tooltip>
                  <Tooltip title="Resume">
                    <Button
                      icon={<PlayCircleOutlined />}
                      aria-label="Resume run"
                      disabled={props.selected.status !== 'PAUSED'}
                      loading={props.busy === 'run-resume'}
                      onClick={() => props.onCommand('resume')}
                    />
                  </Tooltip>
                  <Popconfirm title="Cancel this run?" onConfirm={() => props.onCommand('cancel')}>
                    <Tooltip title="Cancel">
                      <Button
                        danger
                        icon={<StopOutlined />}
                        aria-label="Cancel run"
                        disabled={isTerminal(props.selected.status)}
                        loading={props.busy === 'run-cancel'}
                      />
                    </Tooltip>
                  </Popconfirm>
                </Space>
              }
            />
            <div className="agent-run-stats">
              <Statistic title="Status" value={props.selected.status} />
              <Statistic title="Events" value={props.selected.currentSequence} />
              <Statistic title="Max steps" value={props.selected.budget.maxSteps} />
              <Statistic title="Workers" value={props.selected.budget.maxWorkers} />
            </div>
            <Timeline
              className="agent-event-timeline"
              items={props.events.map((event) => ({
                color: event.type.endsWith('failed')
                  ? 'red'
                  : event.type.endsWith('completed') || event.type.endsWith('succeeded')
                    ? 'green'
                    : 'blue',
                dot: event.type.includes('tool') ? (
                  <ToolOutlined />
                ) : event.type.includes('plan') ? (
                  <RobotOutlined />
                ) : (
                  <ClockCircleOutlined />
                ),
                children: (
                  <div className="agent-event-row">
                    <Text code>#{event.sequence}</Text>
                    <Text strong>{event.type}</Text>
                    <Text type="secondary">{formatDate(event.occurredAt)}</Text>
                    <pre>{JSON.stringify(event.payload, null, 2)}</pre>
                  </div>
                ),
              }))}
            />
          </>
        )}
      </section>
    </div>
  );
}

export function ToolsSurface(props: {
  tools: AgentToolV2[];
  grants: AgentToolGrantV2[];
  versions: AgentVersionV2[];
  busy: string;
  onNew: () => void;
  onRevoke: (grant: AgentToolGrantV2) => void;
}) {
  const toolById = new Map(props.tools.map((item) => [item.id, item]));
  return (
    <section className="agent-surface">
      <SectionHeader
        title="Tool permissions"
        action={
          <Button type="primary" icon={<PlusOutlined />} onClick={props.onNew} disabled={!props.versions.length}>
            New grant
          </Button>
        }
      />
      <div className="agent-tool-catalog">
        {props.tools.map((tool) => (
          <div key={tool.id}>
            <span className={`risk-swatch risk-${tool.riskClass.toLowerCase()}`} />
            <div>
              <Text strong>{tool.name}</Text>
              <Text type="secondary">
                {tool.toolKey} v{tool.version}
              </Text>
            </div>
            <StatusTag value={tool.riskClass} />
          </div>
        ))}
      </div>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 780 }}
        dataSource={props.grants}
        locale={{ emptyText: 'No Tool grants' }}
        columns={[
          { title: 'Tool', dataIndex: 'toolVersionId', render: (id) => toolById.get(id)?.toolKey || shortId(id) },
          { title: 'Agent version', dataIndex: 'agentVersionId', render: shortId },
          {
            title: 'Operations',
            dataIndex: 'operations',
            render: (values: string[]) => (
              <Space wrap>
                {values.map((value) => (
                  <Tag key={value}>{value}</Tag>
                ))}
              </Space>
            ),
          },
          { title: 'Approval', dataIndex: 'approvalMode', width: 110, render: (value) => <StatusTag value={value} /> },
          {
            title: 'State',
            width: 100,
            render: (_, item) => (item.revokedAt ? <Tag>Revoked</Tag> : <Tag color="success">Active</Tag>),
          },
          {
            title: '',
            width: 90,
            render: (_, item) =>
              !item.revokedAt && (
                <Popconfirm title="Revoke this Tool grant?" onConfirm={() => props.onRevoke(item)}>
                  <Button danger size="small" loading={props.busy === `revoke-${item.id}`}>
                    Revoke
                  </Button>
                </Popconfirm>
              ),
          },
        ]}
      />
    </section>
  );
}

export function MemorySurface(props: {
  entries: AgentMemoryV2[];
  state?: string;
  busy: string;
  onState: (value?: string) => void;
  onExport: () => void;
  onQuarantine: (entry: AgentMemoryV2) => void;
  onDelete: (entry: AgentMemoryV2) => void;
}) {
  return (
    <section className="agent-surface">
      <SectionHeader
        title="Memory governance"
        action={
          <Space>
            <Select
              allowClear
              value={props.state}
              placeholder="All states"
              onChange={props.onState}
              options={['ACTIVE', 'QUARANTINED', 'DELETING', 'DELETED'].map((value) => ({ value }))}
            />
            <Button icon={<ExportOutlined />} onClick={props.onExport} loading={props.busy === 'memory-export'}>
              Export metadata
            </Button>
          </Space>
        }
      />
      <Alert
        type="info"
        showIcon
        message="Memory content is redacted. Governance views expose metadata and provenance only."
      />
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 900 }}
        dataSource={props.entries}
        locale={{ emptyText: 'No Memory entries' }}
        columns={[
          { title: 'ID', dataIndex: 'id', render: (value) => <Text code>{shortId(value)}</Text> },
          { title: 'Purpose', dataIndex: 'purpose' },
          { title: 'Source', render: (_, item) => `${item.sourceType}:${shortId(item.sourceId)}` },
          { title: 'Sensitivity', dataIndex: 'sensitivity', render: (value) => <StatusTag value={value} /> },
          { title: 'State', dataIndex: 'state', render: (value) => <StatusTag value={value} /> },
          { title: 'Retention', dataIndex: 'retentionDeadline', width: 190, render: formatDate },
          {
            title: '',
            width: 190,
            render: (_, item) => (
              <Space>
                <Button
                  size="small"
                  disabled={item.state !== 'ACTIVE'}
                  loading={props.busy === `quarantine-${item.id}`}
                  onClick={() => props.onQuarantine(item)}
                >
                  Quarantine
                </Button>
                <Popconfirm title="Queue permanent purge?" onConfirm={() => props.onDelete(item)}>
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={item.state === 'DELETED' || item.state === 'DELETING'}
                    loading={props.busy === `delete-${item.id}`}
                  >
                    Delete
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
    </section>
  );
}

export function McpSurface(props: {
  servers: McpServerV2[];
  busy: string;
  onNew: () => void;
  onCommand: (server: McpServerV2, command: 'test' | 'discover') => void;
  onDisable: (server: McpServerV2) => void;
}) {
  return (
    <section className="agent-surface">
      <SectionHeader
        title="MCP Servers"
        action={
          <Button type="primary" icon={<PlusOutlined />} onClick={props.onNew}>
            Register
          </Button>
        }
      />
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 900 }}
        dataSource={props.servers}
        locale={{ emptyText: 'No MCP Servers' }}
        columns={[
          {
            title: 'Server',
            render: (_, item) => (
              <div className="table-primary">
                <Text strong>{item.name}</Text>
                <Text type="secondary">{item.endpoint}</Text>
              </div>
            ),
          },
          { title: 'Transport', dataIndex: 'transport', width: 160 },
          {
            title: 'Auth',
            render: (_, item) => (
              <Space>
                <span>{item.authType}</span>
                {item.credentialConfigured && <SafetyCertificateOutlined />}
              </Space>
            ),
          },
          { title: 'Status', dataIndex: 'status', render: (value) => <StatusTag value={value} /> },
          { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
          {
            title: '',
            width: 240,
            render: (_, item) => (
              <Space>
                <Button
                  size="small"
                  onClick={() => props.onCommand(item, 'test')}
                  disabled={item.status === 'DISABLED'}
                  loading={props.busy === `test-${item.id}`}
                >
                  Test
                </Button>
                <Button
                  size="small"
                  onClick={() => props.onCommand(item, 'discover')}
                  disabled={item.status === 'DISABLED'}
                  loading={props.busy === `discover-${item.id}`}
                >
                  Discover
                </Button>
                <Popconfirm title="Disable this MCP Server?" onConfirm={() => props.onDisable(item)}>
                  <Button
                    size="small"
                    danger
                    disabled={item.status === 'DISABLED'}
                    loading={props.busy === `disable-${item.id}`}
                  >
                    Disable
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
    </section>
  );
}

export function EvaluationSurface(props: {
  datasets: EvaluationDatasetV2[];
  suites: EvaluationSuiteV2[];
  versions: AgentVersionV2[];
  result?: EvaluationRunV2;
  form?: FormInstance;
  busy: string;
  onNewDataset: () => void;
  onRun: (values: {
    suiteVersionId: string;
    candidateAgentVersionId: string;
    baselineAgentVersionId: string;
    repeatCount: number;
  }) => void;
  onCancel: () => void;
}) {
  const candidates = props.versions.filter((item) => item.status === 'CANDIDATE' || item.status === 'PUBLISHED');
  const baselines = props.versions.filter((item) => item.status === 'PUBLISHED');
  return (
    <section className="agent-surface">
      <SectionHeader
        title="Evaluation Dashboard"
        action={
          <Button icon={<PlusOutlined />} onClick={props.onNewDataset}>
            New dataset
          </Button>
        }
      />
      <div className="evaluation-layout">
        <Form
          form={props.form}
          layout="vertical"
          className="evaluation-controls"
          initialValues={{ repeatCount: 3 }}
          onFinish={props.onRun}
        >
          <Form.Item name="suiteVersionId" label="Suite" rules={[{ required: true }]}>
            <Select options={props.suites.map((item) => ({ value: item.versionId, label: item.name }))} />
          </Form.Item>
          <Form.Item name="candidateAgentVersionId" label="Candidate" rules={[{ required: true }]}>
            <Select
              options={candidates.map((item) => ({
                value: item.id,
                label: `${shortId(item.agentId)} · ${item.status === 'CANDIDATE' ? 'candidate' : `v${item.version}`}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="baselineAgentVersionId" label="Published baseline" rules={[{ required: true }]}>
            <Select
              options={baselines.map((item) => ({
                value: item.id,
                label: `${shortId(item.agentId)} · v${item.version}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="repeatCount" label="Repeats">
            <InputNumber min={1} max={10} />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            icon={<ExperimentOutlined />}
            loading={props.busy === 'evaluation-run'}
            disabled={!props.suites.length || !candidates.length || !baselines.length}
            block
          >
            Run Evaluation
          </Button>
        </Form>
        <div className="evaluation-result">
          {!props.result ? (
            <Empty description="No Evaluation run selected" />
          ) : (
            <>
              <SectionHeader
                title={`Evaluation ${shortId(props.result.id)}`}
                action={
                  <Space>
                    <StatusTag value={props.result.status} />
                    <StatusTag value={props.result.gateStatus || 'PENDING'} />
                    {props.result.status === 'RUNNING' && (
                      <Button danger size="small" onClick={props.onCancel} loading={props.busy === 'evaluation-cancel'}>
                        Cancel
                      </Button>
                    )}
                  </Space>
                }
              />
              {props.result.gateStatus === 'FAIL' && <Alert type="error" showIcon message="Release gate failed" />}
              <div className="evaluation-metrics">
                {props.result.metrics.map((metric) => (
                  <div key={metric.key}>
                    <Text>{metric.key}</Text>
                    <Progress
                      percent={Math.round(metric.value * 100)}
                      status={props.result?.gateStatus === 'FAIL' ? 'exception' : 'normal'}
                    />
                    <Text type="secondary">
                      n={metric.sampleCount}
                      {metric.low !== undefined && metric.high !== undefined
                        ? ` · 95% CI ${metric.low.toFixed(3)}–${metric.high.toFixed(3)}`
                        : ''}
                    </Text>
                  </div>
                ))}
              </div>
              <Table
                rowKey="key"
                size="small"
                pagination={false}
                dataSource={props.result.gates}
                columns={[
                  { title: 'Gate', dataIndex: 'key' },
                  { title: 'Status', dataIndex: 'status', render: (value) => <StatusTag value={value} /> },
                  { title: 'Actual', dataIndex: 'actual' },
                  { title: 'Threshold', dataIndex: 'threshold' },
                  { title: 'Reason', dataIndex: 'reasonCode' },
                ]}
              />
            </>
          )}
        </div>
      </div>
      <Title level={5}>Datasets</Title>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={props.datasets}
        columns={[
          { title: 'Name', dataIndex: 'name' },
          { title: 'Description', dataIndex: 'description' },
          { title: 'Status', dataIndex: 'status', render: (value) => <StatusTag value={value} /> },
          { title: 'Updated', dataIndex: 'updatedAt', render: formatDate },
        ]}
      />
    </section>
  );
}

function SectionHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="agent-section-header">
      <Title level={4}>{title}</Title>
      {action}
    </div>
  );
}

function StatusTag({ value }: { value: string }) {
  const color = ['PASS', 'PUBLISHED', 'SUCCEEDED', 'ACTIVE', 'REGISTERED', 'READ'].includes(value)
    ? 'success'
    : ['FAIL', 'FAILED', 'DESTRUCTIVE', 'DELETED', 'CANCELLED'].includes(value)
      ? 'error'
      : ['RUNNING', 'EXECUTING', 'PLANNING', 'QUEUED', 'CANDIDATE', 'WRITE'].includes(value)
        ? 'processing'
        : 'default';
  return <Tag color={color}>{value}</Tag>;
}

function isTerminal(status: string) {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status);
}

function versionOptions(versions: AgentVersionV2[], definitions: AgentDefinitionV2[]) {
  return versions.map((item) => ({
    value: item.id,
    label: `${definitions.find((definition) => definition.id === item.agentId)?.name || shortId(item.agentId)} · v${item.version}`,
  }));
}

function budgetLabel(name: string) {
  return (
    { maxSteps: 'Steps', maxDurationSeconds: 'Seconds', maxToolCalls: 'Tool calls', maxWorkers: 'Workers' } as Record<
      string,
      string
    >
  )[name];
}

function budgetMax(name: string) {
  return ({ maxSteps: 64, maxDurationSeconds: 1800, maxToolCalls: 128, maxWorkers: 16 } as Record<string, number>)[
    name
  ];
}

const jsonRule = {
  validator: (_: unknown, value: string) => {
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? Promise.resolve()
        : Promise.reject(new Error('Enter a JSON object'));
    } catch {
      return Promise.reject(new Error('Enter valid JSON'));
    }
  },
};
