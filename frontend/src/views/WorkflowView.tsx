import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SendOutlined,
  StopOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Background,
  BackgroundVariant,
  Connection,
  Controls,
  Edge,
  MiniMap,
  Node,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { FormInstance } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  WorkflowDefinition,
  WorkflowEdge,
  WorkflowEvent,
  WorkflowExecution,
  WorkflowGraph,
  WorkflowNode,
  WorkflowNodeType,
  WorkflowTrigger,
  WorkflowVersion,
  createWorkflow,
  createWorkflowTrigger,
  cancelWorkflowExecution,
  decideWorkflowApproval,
  deleteWorkflow,
  listWorkflowEvents,
  listWorkflowExecutions,
  listWorkflowTriggers,
  listWorkflowVersions,
  listWorkflows,
  publishWorkflow,
  retryWorkflowNode,
  restoreWorkflowVersion,
  triggerWorkflow,
  updateWorkflow,
  validateWorkflow,
} from '../api';
import { formatDate, shortId } from '../format';

const { Text, Title } = Typography;
type CanvasNode = Node<{ label: string; nodeType: WorkflowNodeType; config: Record<string, unknown> }>;
type SideTab = 'INSPECTOR' | 'RUNS' | 'TRIGGERS';
interface TriggerFormValues {
  type: WorkflowTrigger['type'];
  expression?: string;
  eventType?: string;
}

const nodeTypes: WorkflowNodeType[] = [
  'LLM',
  'AGENT',
  'TOOL',
  'CONDITION',
  'LOOP',
  'APPROVAL',
  'DELAY',
  'WEBHOOK',
  'END',
];

export function WorkflowView({ token }: { token: string }) {
  return (
    <ReactFlowProvider>
      <WorkflowWorkspace token={token} />
    </ReactFlowProvider>
  );
}

function WorkflowWorkspace({ token }: { token: string }) {
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState('');
  const [executions, setExecutions] = useState<WorkflowExecution[]>([]);
  const [selectedExecutionId, setSelectedExecutionId] = useState('');
  const [events, setEvents] = useState<WorkflowEvent[]>([]);
  const [triggers, setTriggers] = useState<WorkflowTrigger[]>([]);
  const [versions, setVersions] = useState<WorkflowVersion[]>([]);
  const [sideTab, setSideTab] = useState<SideTab>('INSPECTOR');
  const [nodeToAdd, setNodeToAdd] = useState<WorkflowNodeType>('LLM');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [triggerOpen, setTriggerOpen] = useState(false);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [triggerForm] = Form.useForm();
  const [messageApi, contextHolder] = message.useMessage();
  const selected = workflows.find((item) => item.id === selectedId);
  const selectedNode = nodes.find((node) => node.id === selectedNodeId);
  const editable = selected?.role === 'OWNER' || selected?.role === 'EDITOR';
  const runnable = editable || selected?.role === 'RUNNER';

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const page = await listWorkflows(token);
      setWorkflows(page.items);
      setSelectedId((current) =>
        current && page.items.some((item) => item.id === current) ? current : page.items[0]?.id || '',
      );
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selected) {
      setNodes([]);
      setEdges([]);
      setExecutions([]);
      setTriggers([]);
      return;
    }
    setNodes(selected.graph.nodes.map(toCanvasNode));
    setEdges(selected.graph.edges.map(toCanvasEdge));
    setSelectedNodeId('');
    Promise.all([listWorkflowExecutions(token, selected.id), listWorkflowTriggers(token, selected.id)])
      .then(([executionPage, triggerList]) => {
        setExecutions(executionPage.items);
        setTriggers(triggerList);
      })
      .catch((reason) => setError(errorMessage(reason)));
  }, [selectedId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedExecutionId) {
      setEvents([]);
      return;
    }
    void listWorkflowEvents(token, selectedExecutionId)
      .then(setEvents)
      .catch((reason) => setError(errorMessage(reason)));
  }, [selectedExecutionId, token]);

  const onConnect = useCallback(
    (connection: Connection) => {
      if (!editable || !connection.source || !connection.target) return;
      setEdges((current) =>
        addEdge(
          {
            ...connection,
            id: edgeId(connection.source, connection.target, current.length),
            type: 'smoothstep',
            animated: false,
          },
          current,
        ),
      );
    },
    [editable, setEdges],
  );

  const graph = useMemo<WorkflowGraph>(() => fromCanvas(nodes, edges), [nodes, edges]);

  async function save(): Promise<boolean> {
    if (!selected) return false;
    setBusy('save');
    setError('');
    try {
      const updated = await updateWorkflow(token, selected, selected.name, selected.description, graph);
      replaceWorkflow(updated);
      messageApi.success('Draft saved');
      return true;
    } catch (reason) {
      setError(errorMessage(reason));
      return false;
    } finally {
      setBusy('');
    }
  }

  async function validate() {
    if (!selected) return;
    setError('');
    if (editable && !(await save())) return;
    setBusy('validate');
    try {
      const result = await validateWorkflow(token, selected.id);
      if (result.valid) messageApi.success('Graph is valid');
      else setError(result.errors.map((item) => `${item.code}: ${item.message}`).join(' / '));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  async function publish() {
    if (!selected) return;
    setBusy('publish');
    setError('');
    try {
      const saved = await updateWorkflow(token, selected, selected.name, selected.description, graph);
      const version = await publishWorkflow(token, saved);
      replaceWorkflow({ ...saved, status: 'PUBLISHED', publishedVersion: version.version });
      messageApi.success(`Published version ${version.version}`);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  async function run() {
    if (!selected) return;
    setBusy('run');
    try {
      const execution = await triggerWorkflow(token, selected.id);
      setExecutions((current) => [execution, ...current.filter((item) => item.id !== execution.id)]);
      setSelectedExecutionId(execution.id);
      setSideTab('RUNS');
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setBusy('');
    }
  }

  function addNode(type: WorkflowNodeType) {
    const id = `${type.toLowerCase()}_${Date.now().toString(36)}`;
    setNodes((current) => [
      ...current,
      {
        id,
        position: { x: 180 + (current.length % 3) * 220, y: 100 + Math.floor(current.length / 3) * 150 },
        data: { label: titleCase(type), nodeType: type, config: defaultNodeConfig(type) },
        type: 'default',
      },
    ]);
    setSelectedNodeId(id);
  }

  function removeSelectedNode() {
    if (!selectedNode || selectedNode.data.nodeType === 'START') return;
    setNodes((current) => current.filter((node) => node.id !== selectedNode.id));
    setEdges((current) => current.filter((edge) => edge.source !== selectedNode.id && edge.target !== selectedNode.id));
    setSelectedNodeId('');
  }

  function updateSelectedConfig(config: Record<string, unknown>) {
    setNodes((current) =>
      current.map((node) => (node.id === selectedNodeId ? { ...node, data: { ...node.data, config } } : node)),
    );
  }

  function replaceWorkflow(workflow: WorkflowDefinition) {
    setWorkflows((current) => current.map((item) => (item.id === workflow.id ? workflow : item)));
  }

  const refreshOperations = useCallback(async () => {
    if (!selected) return;
    const [executionPage, triggerList] = await Promise.all([
      listWorkflowExecutions(token, selected.id),
      listWorkflowTriggers(token, selected.id),
    ]);
    setExecutions(executionPage.items);
    setTriggers(triggerList);
    if (selectedExecutionId) setEvents(await listWorkflowEvents(token, selectedExecutionId));
  }, [selected, selectedExecutionId, token]);

  const selectedExecutionStatus = executions.find((item) => item.id === selectedExecutionId)?.status;
  useEffect(() => {
    if (!selectedExecutionId || !selectedExecutionStatus || isTerminal(selectedExecutionStatus)) return;
    const timer = window.setInterval(() => void refreshOperations(), 3000);
    return () => window.clearInterval(timer);
  }, [refreshOperations, selectedExecutionId, selectedExecutionStatus]);

  if (loading && !workflows.length)
    return (
      <div className="centered">
        <Spin />
      </div>
    );

  return (
    <div className="workflow-page">
      {contextHolder}
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <div className="workflow-toolbar">
        <Space wrap>
          <Select<WorkflowNodeType>
            value={nodeToAdd}
            disabled={!editable}
            onChange={setNodeToAdd}
            options={nodeTypes.map((type) => ({ value: type, label: titleCase(type) }))}
          />
          <Tooltip title="Add node">
            <Button
              icon={<PlusOutlined />}
              disabled={!editable}
              onClick={() => addNode(nodeToAdd)}
              aria-label="Add node"
            />
          </Tooltip>
          <Tooltip title="Delete selected node">
            <Button
              danger
              icon={<DeleteOutlined />}
              disabled={!editable || !selectedNode}
              onClick={removeSelectedNode}
              aria-label="Delete selected node"
            />
          </Tooltip>
        </Space>
        <Space wrap>
          <Tooltip title="Version history">
            <Button
              icon={<HistoryOutlined />}
              disabled={!selected}
              aria-label="Version history"
              onClick={async () => {
                if (!selected) return;
                setVersions(await listWorkflowVersions(token, selected.id));
                setVersionsOpen(true);
              }}
            />
          </Tooltip>
          <Button
            icon={<CheckCircleOutlined />}
            disabled={!selected}
            loading={busy === 'validate'}
            onClick={() => void validate()}
          >
            Validate
          </Button>
          <Button icon={<SaveOutlined />} disabled={!editable} loading={busy === 'save'} onClick={() => void save()}>
            Save
          </Button>
          <Button
            icon={<SendOutlined />}
            disabled={!editable}
            loading={busy === 'publish'}
            onClick={() => void publish()}
          >
            Publish
          </Button>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            disabled={!runnable || !selected?.publishedVersion}
            loading={busy === 'run'}
            onClick={() => void run()}
          >
            Run
          </Button>
        </Space>
      </div>
      <div className="workflow-workspace">
        <aside className="workflow-rail">
          <div className="rail-heading">
            <Title level={4}>Workflows</Title>
            <Space>
              <Tooltip title="Refresh">
                <Button type="text" icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Refresh" />
              </Tooltip>
              <Tooltip title="New workflow">
                <Button
                  type="text"
                  icon={<PlusOutlined />}
                  onClick={() => setCreateOpen(true)}
                  aria-label="New workflow"
                />
              </Tooltip>
            </Space>
          </div>
          <List
            dataSource={workflows}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No workflows" /> }}
            renderItem={(workflow) => (
              <List.Item
                className={workflow.id === selectedId ? 'selected' : ''}
                onClick={() => setSelectedId(workflow.id)}
              >
                <List.Item.Meta
                  title={workflow.name}
                  description={
                    <Space size={4} wrap>
                      <Tag>{workflow.status}</Tag>
                      <Text type="secondary">v{workflow.publishedVersion || '-'}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </aside>
        <main className="workflow-canvas" aria-label="Workflow canvas">
          {!selected ? (
            <Empty description="Create a workflow" />
          ) : (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={editable ? onNodesChange : undefined}
              onEdgesChange={editable ? onEdgesChange : undefined}
              onConnect={onConnect}
              onNodeClick={(_, node) => {
                setSelectedNodeId(node.id);
                setSideTab('INSPECTOR');
              }}
              onPaneClick={() => setSelectedNodeId('')}
              nodesDraggable={editable}
              nodesConnectable={editable}
              deleteKeyCode={editable ? ['Backspace', 'Delete'] : null}
              fitView
              minZoom={0.25}
              maxZoom={1.8}
            >
              <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="#b9c5c0" />
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) => (node.data.nodeType === 'APPROVAL' ? '#c78c2f' : '#338b6a')}
              />
              <Controls showInteractive={false} />
            </ReactFlow>
          )}
        </main>
        <aside className="workflow-inspector">
          <Segmented<SideTab>
            block
            value={sideTab}
            onChange={setSideTab}
            options={[
              { label: 'Inspector', value: 'INSPECTOR' },
              { label: 'Runs', value: 'RUNS' },
              { label: 'Triggers', value: 'TRIGGERS' },
            ]}
          />
          {sideTab === 'INSPECTOR' && (
            <NodeInspector node={selectedNode} editable={Boolean(editable)} onConfig={updateSelectedConfig} />
          )}
          {sideTab === 'RUNS' && (
            <RunPanel
              token={token}
              executions={executions}
              events={events}
              selectedId={selectedExecutionId}
              onSelect={setSelectedExecutionId}
              onRefresh={() => void refreshOperations()}
              onCancel={async (executionId) => {
                await cancelWorkflowExecution(token, executionId);
                await refreshOperations();
              }}
              onRetry={async (executionId, nodeId) => {
                await retryWorkflowNode(token, executionId, nodeId);
                await refreshOperations();
              }}
              onDecision={async (approvalId, decision) => {
                await decideWorkflowApproval(token, approvalId, decision);
                await refreshOperations();
              }}
            />
          )}
          {sideTab === 'TRIGGERS' && (
            <TriggerPanel triggers={triggers} editable={Boolean(editable)} onCreate={() => setTriggerOpen(true)} />
          )}
        </aside>
      </div>

      <Modal
        title="New workflow"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
      >
        <Form
          form={createForm}
          layout="vertical"
          requiredMark={false}
          onFinish={async (values) => {
            const created = await createWorkflow(token, values.name.trim(), values.description?.trim() || '');
            setWorkflows((current) => [created, ...current]);
            setSelectedId(created.id);
            setCreateOpen(false);
            createForm.resetFields();
          }}
        >
          <Form.Item name="name" label="Name" rules={[{ required: true }, { max: 120 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ max: 2000 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <TriggerModal
        open={triggerOpen}
        form={triggerForm}
        onClose={() => setTriggerOpen(false)}
        onCreate={async (type, config) => {
          if (!selected) return;
          const created = await createWorkflowTrigger(token, selected.id, type, config);
          setTriggers((current) => [created, ...current]);
          setTriggerOpen(false);
          triggerForm.resetFields();
          if (created.secret)
            Modal.info({
              title: 'Webhook secret',
              content: <Input.Password value={created.secret} readOnly visibilityToggle />,
            });
        }}
      />

      <Drawer title="Version history" open={versionsOpen} onClose={() => setVersionsOpen(false)} width={420}>
        <List
          dataSource={versions}
          renderItem={(version) => (
            <List.Item
              actions={
                editable
                  ? [
                      <Button
                        key="restore"
                        onClick={async () => {
                          if (!selected) return;
                          const restored = await restoreWorkflowVersion(token, selected.id, version.version);
                          replaceWorkflow(restored);
                          setVersionsOpen(false);
                        }}
                      >
                        Restore
                      </Button>,
                    ]
                  : []
              }
            >
              <List.Item.Meta
                title={`Version ${version.version}`}
                description={`${formatDate(version.publishedAt)} / ${version.graphSha256.slice(0, 12)}`}
              />
            </List.Item>
          )}
        />
        {selected?.role === 'OWNER' && (
          <Button
            danger
            block
            icon={<DeleteOutlined />}
            onClick={() =>
              Modal.confirm({
                title: `Delete ${selected.name}?`,
                okText: 'Delete',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteWorkflow(token, selected.id);
                  setVersionsOpen(false);
                  await load();
                },
              })
            }
          >
            Delete workflow
          </Button>
        )}
      </Drawer>
    </div>
  );
}

function NodeInspector({
  node,
  editable,
  onConfig,
}: {
  node?: CanvasNode;
  editable: boolean;
  onConfig: (config: Record<string, unknown>) => void;
}) {
  if (!node) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No node selected" />;
  return (
    <div className="node-inspector">
      <Space direction="vertical" size={2}>
        <Tag color="green">{node.data.nodeType}</Tag>
        <Text code>{node.id}</Text>
      </Space>
      {node.data.nodeType === 'DELAY' ? (
        <label>
          <Text>Seconds</Text>
          <InputNumber
            min={1}
            max={86400}
            value={Number(node.data.config.seconds || 1)}
            disabled={!editable}
            onChange={(value) => onConfig({ ...node.data.config, seconds: value || 1 })}
          />
        </label>
      ) : node.data.nodeType === 'APPROVAL' ? (
        <Space direction="vertical" size={10}>
          <label>
            <Text>Decision mode</Text>
            <Segmented
              block
              options={['ANY', 'ALL']}
              value={String(node.data.config.mode || 'ANY')}
              disabled={!editable}
              onChange={(value) => onConfig({ ...node.data.config, mode: value })}
            />
          </label>
          <label>
            <Text>Assignee IDs</Text>
            <Input
              value={Array.isArray(node.data.config.assigneeIds) ? node.data.config.assigneeIds.join(', ') : ''}
              disabled={!editable}
              onChange={(event) =>
                onConfig({
                  ...node.data.config,
                  assigneeIds: event.target.value
                    .split(',')
                    .map((value) => value.trim())
                    .filter(Boolean),
                })
              }
            />
          </label>
        </Space>
      ) : (
        <label>
          <Text>Configuration</Text>
          <Input.TextArea
            rows={8}
            value={JSON.stringify(node.data.config, null, 2)}
            disabled={!editable}
            onChange={(event) => {
              try {
                onConfig(JSON.parse(event.target.value));
              } catch {
                /* keep last valid configuration */
              }
            }}
          />
        </label>
      )}
    </div>
  );
}

function RunPanel({
  token,
  executions,
  events,
  selectedId,
  onSelect,
  onRefresh,
  onCancel,
  onRetry,
  onDecision,
}: {
  token: string;
  executions: WorkflowExecution[];
  events: WorkflowEvent[];
  selectedId: string;
  onSelect: (id: string) => void;
  onRefresh: () => void;
  onCancel: (executionId: string) => Promise<void>;
  onRetry: (executionId: string, nodeId: string) => Promise<void>;
  onDecision: (approvalId: string, decision: 'APPROVE' | 'REJECT') => Promise<void>;
}) {
  void token;
  const selected = executions.find((execution) => execution.id === selectedId);
  const retryNodeId = [...events].reverse().find((event) => event.nodeId)?.nodeId;
  return (
    <div className="run-panel">
      <div className="inspector-heading">
        <Text strong>Executions</Text>
        <Button type="text" icon={<ReloadOutlined />} onClick={onRefresh} aria-label="Refresh executions" />
      </div>
      <List
        size="small"
        dataSource={executions}
        locale={{ emptyText: 'No executions' }}
        renderItem={(execution) => (
          <List.Item className={execution.id === selectedId ? 'selected' : ''} onClick={() => onSelect(execution.id)}>
            <List.Item.Meta
              title={
                <Space>
                  <Tag color={statusColor(execution.status)}>{execution.status}</Tag>
                  <Text>{shortId(execution.id)}</Text>
                </Space>
              }
              description={formatDate(execution.createdAt)}
            />
          </List.Item>
        )}
      />
      {selectedId && (
        <>
          <Space wrap>
            {selected && !isTerminal(selected.status) && (
              <Button
                danger
                size="small"
                icon={<StopOutlined />}
                onClick={() =>
                  Modal.confirm({
                    title: 'Cancel execution?',
                    content: shortId(selected.id),
                    okText: 'Cancel execution',
                    okButtonProps: { danger: true },
                    onOk: () => onCancel(selected.id),
                  })
                }
              >
                Cancel
              </Button>
            )}
            {selected?.status === 'FAILED' && retryNodeId && (
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() =>
                  Modal.confirm({
                    title: 'Retry failed node?',
                    content: retryNodeId,
                    okText: 'Retry',
                    onOk: () => onRetry(selected.id, retryNodeId),
                  })
                }
              >
                Retry
              </Button>
            )}
          </Space>
          <Timeline
            items={events.map((event) => ({
              color: event.type.includes('failed')
                ? 'red'
                : event.type.includes('waiting') || event.type.includes('approval')
                  ? 'gold'
                  : 'green',
              children: (
                <div>
                  <Text strong>{event.type.replace('workflow.', '')}</Text>
                  <small>{event.nodeId || formatDate(event.occurredAt)}</small>
                  {typeof event.data.approvalId === 'string' && event.type.endsWith('approval.requested') && (
                    <Space>
                      <Button
                        size="small"
                        type="primary"
                        onClick={() => void onDecision(String(event.data.approvalId), 'APPROVE')}
                      >
                        Approve
                      </Button>
                      <Button
                        size="small"
                        danger
                        onClick={() => void onDecision(String(event.data.approvalId), 'REJECT')}
                      >
                        Reject
                      </Button>
                    </Space>
                  )}
                </div>
              ),
            }))}
          />
        </>
      )}
    </div>
  );
}

function TriggerPanel({
  triggers,
  editable,
  onCreate,
}: {
  triggers: WorkflowTrigger[];
  editable: boolean;
  onCreate: () => void;
}) {
  return (
    <div className="trigger-panel">
      <Button block icon={<ThunderboltOutlined />} disabled={!editable} onClick={onCreate}>
        New trigger
      </Button>
      <List
        dataSource={triggers}
        locale={{ emptyText: 'No triggers' }}
        renderItem={(trigger) => (
          <List.Item>
            <List.Item.Meta
              avatar={<ClockCircleOutlined />}
              title={
                <Space>
                  <Text>{trigger.type}</Text>
                  <Tag color={trigger.enabled ? 'green' : 'default'}>{trigger.enabled ? 'Enabled' : 'Disabled'}</Tag>
                </Space>
              }
              description={JSON.stringify(trigger.config)}
            />
          </List.Item>
        )}
      />
    </div>
  );
}

function TriggerModal({
  open,
  form,
  onClose,
  onCreate,
}: {
  open: boolean;
  form: FormInstance<TriggerFormValues>;
  onClose: () => void;
  onCreate: (type: WorkflowTrigger['type'], config: Record<string, unknown>) => Promise<void>;
}) {
  const type = Form.useWatch('type', form) as WorkflowTrigger['type'] | undefined;
  return (
    <Modal title="New trigger" open={open} onCancel={onClose} onOk={() => form.submit()}>
      <Form
        form={form}
        layout="vertical"
        initialValues={{ type: 'WEBHOOK' }}
        onFinish={(values: TriggerFormValues) =>
          void onCreate(
            values.type,
            values.type === 'CRON'
              ? { expression: values.expression }
              : values.type === 'EVENT'
                ? { eventType: values.eventType }
                : {},
          )
        }
      >
        <Form.Item name="type" label="Type" rules={[{ required: true }]}>
          <Select options={['WEBHOOK', 'CRON', 'EVENT'].map((value) => ({ value, label: titleCase(value) }))} />
        </Form.Item>
        {type === 'CRON' && (
          <Form.Item name="expression" label="UTC cron" rules={[{ required: true }]}>
            <Input placeholder="0 9 * * 1-5" />
          </Form.Item>
        )}
        {type === 'EVENT' && (
          <Form.Item name="eventType" label="Event type" rules={[{ required: true }]}>
            <Input placeholder="document.lifecycle.parsed" />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}

function toCanvasNode(node: WorkflowNode): CanvasNode {
  return {
    id: node.id,
    position: node.position,
    type: 'default',
    data: { label: titleCase(node.type), nodeType: node.type, config: node.config },
  };
}

function toCanvasEdge(edge: WorkflowEdge): Edge {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourcePort === 'out' ? null : edge.sourcePort,
    targetHandle: edge.targetPort === 'in' ? null : edge.targetPort,
    type: 'smoothstep',
  };
}

function fromCanvas(nodes: CanvasNode[], edges: Edge[]): WorkflowGraph {
  return {
    schemaVersion: 1,
    nodes: nodes.map((node) => ({
      id: node.id,
      type: node.data.nodeType,
      schemaVersion: 1,
      position: node.position,
      config: node.data.config,
    })),
    edges: edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      sourcePort: edge.sourceHandle || 'out',
      target: edge.target,
      targetPort: edge.targetHandle || 'in',
    })),
  };
}

function edgeId(source: string, target: string, index: number) {
  return `edge_${source}_${target}_${index}`.slice(0, 64);
}
function titleCase(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function defaultNodeConfig(type: WorkflowNodeType): Record<string, unknown> {
  if (type === 'DELAY') return { seconds: 5 };
  if (type === 'APPROVAL') return { assigneeIds: [], mode: 'ANY' };
  if (type === 'CONDITION') return { field: 'status', operator: 'EQUALS', value: 'ready' };
  if (type === 'LOOP') return { maxIterations: 1 };
  return {};
}
function errorMessage(reason: unknown) {
  return reason instanceof Error ? reason.message : 'Workflow request failed';
}
function statusColor(status: WorkflowExecution['status']) {
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'FAILED' || status === 'CANCELLED') return 'red';
  if (status.startsWith('WAITING')) return 'gold';
  return 'blue';
}

function isTerminal(status: WorkflowExecution['status']) {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED';
}
