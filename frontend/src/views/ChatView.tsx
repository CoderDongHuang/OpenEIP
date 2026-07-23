import {
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Alert, Avatar, Button, Empty, Form, Input, List, Modal, Select, Spin, Tag, Tooltip, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  ApiError,
  ChatMessage,
  ChatSession,
  KnowledgeBase,
  createSession,
  getHistory,
  listKnowledgeBases,
  listSessions,
} from '../api';
import { errorMessage, formatDate } from '../format';
import { Citation, streamMessage } from '../sse';

const { Text, Title } = Typography;
interface DisplayMessage extends ChatMessage {
  citations?: Citation[];
  pending?: boolean;
}

export function ChatView({ token }: { token: string }) {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newOpen, setNewOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const transcriptRef = useRef<HTMLDivElement>(null);

  const loadCatalog = useCallback(async () => {
    const [sessionList, basePage] = await Promise.all([listSessions(token), listKnowledgeBases(token)]);
    setSessions(sessionList);
    setBases(basePage.items);
    setSelectedId((current) =>
      current && sessionList.some((item) => item.sessionId === current) ? current : sessionList[0]?.sessionId || '',
    );
  }, [token]);
  useEffect(() => {
    setLoading(true);
    loadCatalog()
      .catch((r) => setError(errorMessage(r)))
      .finally(() => setLoading(false));
  }, [loadCatalog]);
  useEffect(() => {
    abortRef.current?.abort();
    if (!selectedId) {
      setMessages([]);
      return;
    }
    setLoading(true);
    setError('');
    getHistory(token, selectedId)
      .then(setMessages)
      .catch((r) => setError(errorMessage(r)))
      .finally(() => setLoading(false));
  }, [selectedId, token]);
  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  async function send() {
    if (!selectedId || !draft.trim() || streaming) return;
    const content = draft.trim();
    const userId = `user-${Date.now()}`;
    const assistantId = `assistant-${Date.now()}`;
    setMessages((current) => [
      ...current,
      { messageId: userId, sequence: current.length, role: 'user', content, createdAt: new Date().toISOString() },
      {
        messageId: assistantId,
        sequence: current.length + 1,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
        pending: true,
      },
    ]);
    setDraft('');
    setError('');
    setStreaming(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamMessage(token, selectedId, content, controller.signal, (event) => {
        if (event.event === 'token')
          setMessages((current) =>
            current.map((item) =>
              item.messageId === assistantId ? { ...item, content: item.content + event.token } : item,
            ),
          );
        else if (event.event === 'done')
          setMessages((current) =>
            current.map((item) =>
              item.messageId === assistantId ? { ...item, pending: false, citations: event.citations } : item,
            ),
          );
        else throw new ApiError(event.message, 503, event.code);
      });
      await loadCatalog();
    } catch (reason) {
      if (!(reason instanceof DOMException && reason.name === 'AbortError')) setError(errorMessage(reason));
      setMessages((current) =>
        current.map((item) => (item.messageId === assistantId ? { ...item, pending: false } : item)),
      );
    } finally {
      setStreaming(false);
      if (abortRef.current === controller) abortRef.current = null;
    }
  }
  const selected = sessions.find((item) => item.sessionId === selectedId);
  return (
    <div className="chat-workspace">
      <aside className="chat-sessions">
        <div className="rail-heading">
          <Title level={4}>Sessions</Title>
          <Tooltip title="New session">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setNewOpen(true)} aria-label="New session" />
          </Tooltip>
        </div>
        <List
          loading={loading && !sessions.length}
          dataSource={sessions}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No sessions" /> }}
          renderItem={(session) => (
            <List.Item
              className={session.sessionId === selectedId ? 'selected' : ''}
              onClick={() => setSelectedId(session.sessionId)}
            >
              <List.Item.Meta title={session.title} description={formatDate(session.updatedAt)} />
            </List.Item>
          )}
        />
      </aside>
      <section className="chat-main">
        <div className="chat-title">
          <div>
            <Title level={4}>{selected?.title || 'Chat'}</Title>
            {selected && (
              <Text type="secondary">
                {bases.find((base) => base.id === selected.knowledgeBaseId)?.name || 'Knowledge base'}
              </Text>
            )}
          </div>
          <Tooltip title="Reload history">
            <Button
              icon={<ReloadOutlined />}
              disabled={!selected}
              onClick={() => selected && void getHistory(token, selected.sessionId).then(setMessages)}
              aria-label="Reload history"
            />
          </Tooltip>
        </div>
        {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
        <div className="transcript" ref={transcriptRef} aria-live="polite">
          {loading ? (
            <Spin />
          ) : !selected || !messages.length ? (
            <Empty description={selected ? 'Ask your first question' : 'Create a session to start'} />
          ) : (
            messages.map((message) => <ChatMessageView key={message.messageId} message={message} />)
          )}
        </div>
        <div className="composer">
          <div className="composer-row">
            <Input.TextArea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              disabled={!selected || streaming}
              maxLength={4000}
              autoSize={{ minRows: 2, maxRows: 6 }}
              placeholder="Ask the selected knowledge base"
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  void send();
                }
              }}
            />
            {streaming ? (
              <Button danger icon={<StopOutlined />} onClick={() => abortRef.current?.abort()} aria-label="Cancel" />
            ) : (
              <Button
                type="primary"
                icon={<SendOutlined />}
                disabled={!selected || !draft.trim()}
                onClick={() => void send()}
                aria-label="Send"
              />
            )}
          </div>
        </div>
      </section>
      <Modal title="New chat session" open={newOpen} footer={null} onCancel={() => setNewOpen(false)} destroyOnClose>
        <Form
          layout="vertical"
          requiredMark={false}
          onFinish={async (values) => {
            try {
              const created = await createSession(token, values.knowledgeBaseId, values.title || '');
              setNewOpen(false);
              await loadCatalog();
              setSelectedId(created.sessionId);
            } catch (reason) {
              setError(errorMessage(reason));
            }
          }}
        >
          <Form.Item label="Knowledge base" name="knowledgeBaseId" rules={[{ required: true }]}>
            <Select options={bases.map((base) => ({ value: base.id, label: base.name }))} />
          </Form.Item>
          <Form.Item label="Title" name="title" rules={[{ max: 120 }]}>
            <Input placeholder="New conversation" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            Create session
          </Button>
        </Form>
      </Modal>
    </div>
  );
}

function ChatMessageView({ message }: { message: DisplayMessage }) {
  const assistant = message.role === 'assistant';
  return (
    <article className={`message-row ${message.role}`}>
      <Avatar icon={assistant ? <MessageOutlined /> : <UserOutlined />} />
      <div className="message-body">
        <span className="message-role">{assistant ? 'OpenEIP' : 'You'}</span>
        <p className="message-content">{message.content || (message.pending ? '...' : '')}</p>
        {message.citations && (
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
