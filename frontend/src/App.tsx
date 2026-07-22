import {
  DatabaseOutlined,
  LogoutOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Alert, Avatar, Button, Empty, Form, Input, Layout, Space, Spin, Tag, Tooltip, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';

import './App.css';
import { ApiError, ChatMessage, ChatSession, CurrentUser, createSession, currentUser, getHistory, login } from './api';
import { Citation, streamMessage } from './sse';

const { Header } = Layout;
const { Title, Text } = Typography;

interface DisplayMessage extends ChatMessage {
  citations?: Citation[];
  pending?: boolean;
}

const TOKEN_KEY = 'openeip.accessToken';
const SESSION_KEY = 'openeip.chatSession';

function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) || '');
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [session, setSession] = useState<ChatSession | null>(() => readStoredSession());
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [loading, setLoading] = useState(Boolean(token));
  const [streaming, setStreaming] = useState(false);
  const [status, setStatus] = useState('');
  const [lastPrompt, setLastPrompt] = useState('');
  const [draft, setDraft] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const transcriptRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    let active = true;
    Promise.all([currentUser(token), session ? getHistory(token, session.sessionId) : Promise.resolve([])])
      .then(([resolvedUser, history]) => {
        if (!active) return;
        setUser(resolvedUser);
        setMessages(history);
      })
      .catch(() => {
        if (active) clearAuthentication();
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
    // Session is restored once with the token; explicit session changes update history directly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  function clearAuthentication() {
    abortRef.current?.abort();
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(SESSION_KEY);
    setToken('');
    setUser(null);
    setSession(null);
    setMessages([]);
    setStreaming(false);
  }

  async function handleLogin(values: { username: string; password: string }) {
    setStatus('');
    const pair = await login(values.username, values.password);
    sessionStorage.setItem(TOKEN_KEY, pair.accessToken);
    setLoading(true);
    setToken(pair.accessToken);
  }

  async function handleCreate(values: { knowledgeBaseId: string; title?: string }) {
    if (!token) return;
    abortRef.current?.abort();
    setStatus('');
    const created = await createSession(token, values.knowledgeBaseId.trim(), values.title?.trim() || '');
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(created));
    setSession(created);
    setMessages([]);
  }

  async function send(prompt = draft) {
    if (!token || !session || !prompt.trim() || streaming) return;
    const content = prompt.trim();
    const localUserId = `local-user-${Date.now()}`;
    const localAssistantId = `local-assistant-${Date.now()}`;
    setMessages((current) => [
      ...current,
      { messageId: localUserId, sequence: current.length, role: 'user', content, createdAt: new Date().toISOString() },
      {
        messageId: localAssistantId,
        sequence: current.length + 1,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
        pending: true,
      },
    ]);
    setDraft('');
    setLastPrompt(content);
    setStreaming(true);
    setStatus('Streaming');
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamMessage(token, session.sessionId, content, controller.signal, (event) => {
        if (event.event === 'token') {
          setMessages((current) =>
            current.map((message) =>
              message.messageId === localAssistantId ? { ...message, content: message.content + event.token } : message,
            ),
          );
        } else if (event.event === 'done') {
          setMessages((current) =>
            current.map((message) =>
              message.messageId === localAssistantId
                ? { ...message, pending: false, citations: event.citations }
                : message,
            ),
          );
          setStatus('Complete');
        } else {
          throw new ApiError(event.message, 503, event.code);
        }
      });
    } catch (error) {
      const cancelled = error instanceof DOMException && error.name === 'AbortError';
      setStatus(cancelled ? 'Cancelled' : error instanceof Error ? error.message : 'Chat failed');
      setMessages((current) =>
        current.map((message) => (message.messageId === localAssistantId ? { ...message, pending: false } : message)),
      );
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      setStreaming(false);
    }
  }

  if (loading)
    return (
      <div className="loading-shell">
        <Spin size="large" />
      </div>
    );
  if (!token || !user) return <LoginView onLogin={handleLogin} status={status} />;

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <Title level={3} className="brand">
          OpenEIP
        </Title>
        <Space>
          <Text className="user-name">{user.username}</Text>
          <Tooltip title="Log out">
            <Button type="text" danger icon={<LogoutOutlined />} onClick={clearAuthentication} />
          </Tooltip>
        </Space>
      </Header>
      <div className="workspace">
        <aside className="session-rail">
          <Title level={4} className="rail-title">
            Chat session
          </Title>
          <Form layout="vertical" onFinish={handleCreate} requiredMark={false}>
            <Form.Item
              label="Knowledge base ID"
              name="knowledgeBaseId"
              rules={[{ required: true }, { pattern: /^[0-9a-f-]{36}$/i }]}
            >
              <Input prefix={<DatabaseOutlined />} maxLength={36} />
            </Form.Item>
            <Form.Item label="Title" name="title">
              <Input maxLength={120} placeholder="New conversation" />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<PlusOutlined />} block>
              New session
            </Button>
          </Form>
          {session && (
            <div className="session-meta">
              <Text strong>{session.title}</Text>
              <Text className="session-id">{session.sessionId}</Text>
            </div>
          )}
        </aside>
        <main className="chat-main">
          <div className="transcript" ref={transcriptRef} aria-live="polite">
            {!session || messages.length === 0 ? (
              <div className="empty-chat">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={session ? 'No messages yet' : 'Create a session'}
                />
              </div>
            ) : (
              messages.map((message) => <MessageView key={message.messageId} message={message} />)
            )}
          </div>
          <div className="composer">
            <div className="composer-row">
              <Input.TextArea
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                maxLength={4000}
                autoSize={{ minRows: 2, maxRows: 6 }}
                disabled={!session || streaming}
                placeholder="Message"
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault();
                    void send();
                  }
                }}
              />
              {streaming ? (
                <Tooltip title="Cancel">
                  <Button
                    danger
                    icon={<StopOutlined />}
                    onClick={() => abortRef.current?.abort()}
                    aria-label="Cancel"
                  />
                </Tooltip>
              ) : (
                <Tooltip title="Send">
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    onClick={() => void send()}
                    disabled={!session || !draft.trim()}
                    aria-label="Send"
                  />
                </Tooltip>
              )}
            </div>
            <div className="composer-status" role="status">
              <Space>
                <span>{status}</span>
                {!streaming && lastPrompt && status && status !== 'Complete' && (
                  <Button type="link" size="small" icon={<ReloadOutlined />} onClick={() => void send(lastPrompt)}>
                    Retry
                  </Button>
                )}
              </Space>
            </div>
          </div>
        </main>
      </div>
    </Layout>
  );
}

function LoginView({
  onLogin,
  status,
}: {
  onLogin: (values: { username: string; password: string }) => Promise<void>;
  status: string;
}) {
  const [error, setError] = useState('');
  return (
    <div className="login-shell">
      <section className="login-panel">
        <div className="login-mark" />
        <Title level={1}>OpenEIP</Title>
        <Text type="secondary">Sign in to your workspace</Text>
        {error && <Alert type="error" message={error} showIcon style={{ marginTop: 20 }} />}
        {status && <Alert type="warning" message={status} showIcon style={{ marginTop: 20 }} />}
        <Form
          layout="vertical"
          requiredMark={false}
          style={{ marginTop: 28 }}
          onFinish={async (values) => {
            setError('');
            try {
              await onLogin(values);
            } catch (reason) {
              setError(reason instanceof Error ? reason.message : 'Sign in failed');
            }
          }}
        >
          <Form.Item label="Username" name="username" rules={[{ required: true }]}>
            <Input prefix={<UserOutlined />} maxLength={64} autoComplete="username" />
          </Form.Item>
          <Form.Item label="Password" name="password" rules={[{ required: true }]}>
            <Input.Password maxLength={72} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            Sign in
          </Button>
        </Form>
      </section>
      <section className="login-context">
        <div>
          <MessageOutlined style={{ fontSize: 36, color: '#65c69d' }} />
          <h2>Enterprise knowledge chat</h2>
        </div>
      </section>
    </div>
  );
}

function MessageView({ message }: { message: DisplayMessage }) {
  const assistant = message.role === 'assistant';
  return (
    <article className={`message-row ${message.role}`}>
      <Avatar
        icon={assistant ? <MessageOutlined /> : <UserOutlined />}
        style={{ background: assistant ? '#2f8f68' : '#59666f' }}
      />
      <div className="message-body">
        <span className="message-role">{assistant ? 'OpenEIP' : 'You'}</span>
        <p className="message-content">{message.content || (message.pending ? '...' : '')}</p>
        {message.citations && message.citations.length > 0 && (
          <div className="citations">
            {message.citations.map((citation) => (
              <Tag key={citation.chunkId}>
                {citation.documentId.slice(0, 8)} · {citation.score.toFixed(3)}
              </Tag>
            ))}
          </div>
        )}
      </div>
    </article>
  );
}

function readStoredSession(): ChatSession | null {
  try {
    const value = sessionStorage.getItem(SESSION_KEY);
    return value ? (JSON.parse(value) as ChatSession) : null;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export default App;
