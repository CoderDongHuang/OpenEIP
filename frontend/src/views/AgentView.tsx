import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlayCircleOutlined,
  RobotOutlined,
  StopOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Checkbox,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';

import { AgentMetadata, KnowledgeBase, listAgents, listKnowledgeBases } from '../api';
import { AgentEvent, streamAgent } from '../agent-sse';
import { errorMessage } from '../format';

const { Paragraph, Text, Title } = Typography;

export function AgentView({ token }: { token: string }) {
  const [agents, setAgents] = useState<AgentMetadata[]>([]);
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [agentId, setAgentId] = useState('');
  const [tools, setTools] = useState<string[]>([]);
  const [baseId, setBaseId] = useState<string>();
  const [input, setInput] = useState('');
  const [maxSteps, setMaxSteps] = useState(4);
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [answer, setAnswer] = useState('');
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const selected = agents.find((agent) => agent.agentId === agentId);
  const requiresBase = tools.includes('knowledge.search');

  useEffect(() => {
    Promise.all([listAgents(token), listKnowledgeBases(token)])
      .then(([catalog, basePage]) => {
        setAgents(catalog);
        setBases(basePage.items);
        if (catalog[0]) {
          setAgentId(catalog[0].agentId);
          setTools(catalog[0].tools.map((tool) => tool.name));
        }
      })
      .catch((reason) => setError(errorMessage(reason)))
      .finally(() => setLoading(false));
  }, [token]);

  const timeline = useMemo(() => events.filter((event) => event.event !== 'answer.delta'), [events]);
  async function execute() {
    if (!selected || !input.trim() || !tools.length || (requiresBase && !baseId)) return;
    setRunning(true);
    setError('');
    setEvents([]);
    setAnswer('');
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const boundedInput = input.trim();
      const runtimeInput = requiresBase
        ? `search: ${boundedInput}`
        : tools.includes('document.inspect')
          ? `inspect: ${boundedInput}`
          : boundedInput;
      await streamAgent(token, selected.agentId, runtimeInput, baseId, tools, maxSteps, controller.signal, (event) => {
        setEvents((current) => [...current, event]);
        if (event.event === 'answer.delta') setAnswer((current) => current + event.text);
        if (event.event === 'execution.error') setError(event.message);
      });
    } catch (reason) {
      if (!(reason instanceof DOMException && reason.name === 'AbortError')) setError(errorMessage(reason));
    } finally {
      setRunning(false);
      if (abortRef.current === controller) abortRef.current = null;
    }
  }

  if (loading)
    return (
      <div className="centered">
        <Spin />
      </div>
    );
  return (
    <div className="page-stack agent-page">
      <section className="page-intro">
        <Title level={3}>Agent runtime</Title>
        <Paragraph type="secondary">Run the bounded Agent with an explicit tool allowlist and step limit.</Paragraph>
      </section>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      {!selected ? (
        <Empty description="No Agents available" />
      ) : (
        <div className="agent-layout">
          <section className="agent-controls">
            <Form layout="vertical" requiredMark={false}>
              <Form.Item label="Agent">
                <Select
                  value={agentId}
                  options={agents.map((agent) => ({ value: agent.agentId, label: agent.name }))}
                  onChange={(value) => {
                    setAgentId(value);
                    const agent = agents.find((item) => item.agentId === value);
                    setTools(agent?.tools.map((tool) => tool.name) || []);
                  }}
                />
              </Form.Item>
              <div className="agent-meta">
                <Text strong>{selected.name}</Text>
                <Text type="secondary">
                  v{selected.version} · SPI {selected.spiVersion}
                </Text>
                <Paragraph>{selected.description}</Paragraph>
              </div>
              <Form.Item label="Allowed tools" required>
                <Checkbox.Group
                  value={tools}
                  onChange={(values) => setTools(values as string[])}
                  className="tool-options"
                >
                  {selected.tools.map((tool) => (
                    <Checkbox key={tool.name} value={tool.name}>
                      <span>
                        <strong>{tool.name}</strong>
                        <small>{tool.description}</small>
                      </span>
                    </Checkbox>
                  ))}
                </Checkbox.Group>
              </Form.Item>
              <Form.Item label="Knowledge base" required={requiresBase}>
                <Select
                  allowClear
                  value={baseId}
                  placeholder={requiresBase ? 'Required for knowledge.search' : 'Optional'}
                  options={bases.map((base) => ({ value: base.id, label: base.name }))}
                  onChange={setBaseId}
                />
              </Form.Item>
              <Form.Item label="Maximum steps">
                <InputNumber min={1} max={8} value={maxSteps} onChange={(value) => setMaxSteps(value || 1)} />
              </Form.Item>
              <Form.Item label="Task" required>
                <Input.TextArea
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  maxLength={4000}
                  rows={6}
                  placeholder="Describe the task for the Agent"
                />
              </Form.Item>
              {running ? (
                <Button danger icon={<StopOutlined />} onClick={() => abortRef.current?.abort()} block>
                  Cancel execution
                </Button>
              ) : (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={() => void execute()}
                  disabled={!input.trim() || !tools.length || (requiresBase && !baseId)}
                  block
                >
                  Run Agent
                </Button>
              )}
            </Form>
          </section>
          <section className="agent-result" aria-live="polite">
            <Title level={4}>Execution</Title>
            {!events.length ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No execution yet" />
            ) : (
              <>
                <Timeline
                  items={timeline.map((event) => ({
                    dot:
                      event.event === 'execution.completed' ? (
                        <CheckCircleOutlined />
                      ) : event.event.startsWith('tool.') ? (
                        <ToolOutlined />
                      ) : event.event === 'execution.started' ? (
                        <RobotOutlined />
                      ) : (
                        <ClockCircleOutlined />
                      ),
                    color:
                      event.event === 'execution.error'
                        ? 'red'
                        : event.event === 'execution.completed'
                          ? 'green'
                          : 'blue',
                    children: eventLabel(event),
                  }))}
                />
                {answer && (
                  <div className="agent-answer">
                    <Text type="secondary">Answer</Text>
                    <p>{answer}</p>
                  </div>
                )}
                {running && <Tag color="processing">Running</Tag>}
              </>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

function eventLabel(event: AgentEvent) {
  if (event.event === 'execution.started')
    return (
      <Space>
        <Text strong>Execution started</Text>
        <Tag>{event.agentId}</Tag>
      </Space>
    );
  if (event.event === 'tool.started')
    return (
      <span>
        <Text strong>{event.toolName}</Text> started at step {event.step}
      </span>
    );
  if (event.event === 'tool.completed')
    return (
      <span>
        <Text strong>{event.toolName}</Text> completed in {event.durationMs.toFixed(0)} ms
      </span>
    );
  if (event.event === 'execution.completed')
    return (
      <span>
        Completed after {event.steps} step{event.steps === 1 ? '' : 's'}
      </span>
    );
  if (event.event === 'execution.error')
    return (
      <Text type="danger">
        {event.message} ({event.code})
      </Text>
    );
  return null;
}
