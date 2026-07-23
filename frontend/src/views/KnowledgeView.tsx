import {
  DeleteOutlined,
  EditOutlined,
  FileAddOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  DocumentFile,
  KnowledgeBase,
  KnowledgeDocument,
  attachKnowledgeDocument,
  createKnowledgeBase,
  deleteKnowledgeBase,
  detachKnowledgeDocument,
  listFiles,
  listKnowledgeBases,
  listKnowledgeDocuments,
  processKnowledgeDocument,
  updateKnowledgeBase,
} from '../api';
import { errorMessage, formatDate, shortId } from '../format';

const { Paragraph, Text, Title } = Typography;

export function KnowledgeView({ token }: { token: string }) {
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [files, setFiles] = useState<DocumentFile[]>([]);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState('');
  const [error, setError] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<KnowledgeBase | null>(null);
  const [form] = Form.useForm();
  const [messageApi, contextHolder] = message.useMessage();
  const selected = bases.find((base) => base.id === selectedId);

  const loadBases = useCallback(async () => {
    const [basePage, filePage] = await Promise.all([listKnowledgeBases(token), listFiles(token)]);
    setBases(basePage.items);
    setFiles(filePage.items);
    setSelectedId((current) =>
      current && basePage.items.some((item) => item.id === current) ? current : basePage.items[0]?.id || '',
    );
  }, [token]);
  const loadDocuments = useCallback(
    async (baseId: string) => {
      setDocuments(baseId ? await listKnowledgeDocuments(token, baseId) : []);
    },
    [token],
  );
  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      await loadBases();
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [loadBases]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    if (!selectedId) {
      setDocuments([]);
      return;
    }
    setLoading(true);
    loadDocuments(selectedId)
      .catch((reason) => setError(errorMessage(reason)))
      .finally(() => setLoading(false));
  }, [selectedId, loadDocuments]);

  const fileMap = useMemo(() => new Map(files.map((file) => [file.id, file])), [files]);
  const attached = useMemo(() => new Set(documents.map((document) => document.documentId)), [documents]);
  const available = files.filter((file) => !attached.has(file.id));

  async function saveBase(values: { name: string; description?: string }) {
    if (editing) await updateKnowledgeBase(token, editing.id, values.name.trim(), values.description?.trim() || '');
    else await createKnowledgeBase(token, values.name.trim(), values.description?.trim() || '');
    setEditorOpen(false);
    setEditing(null);
    form.resetFields();
    await loadBases();
  }
  function openEditor(base?: KnowledgeBase) {
    setEditing(base || null);
    form.setFieldsValue(base ? { name: base.name, description: base.description } : {});
    setEditorOpen(true);
  }
  return (
    <div className="page-stack knowledge-page">
      {contextHolder}
      <section className="page-toolbar">
        <div>
          <Title level={3}>Knowledge bases</Title>
          <Paragraph type="secondary">
            Attach uploaded sources and process them into searchable chunks and vectors.
          </Paragraph>
        </div>
        <Space>
          <Tooltip title="Refresh">
            <Button icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Refresh" />
          </Tooltip>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>
            New base
          </Button>
        </Space>
      </section>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <div className="knowledge-layout">
        <aside className="knowledge-list">
          <List
            loading={loading && !bases.length}
            dataSource={bases}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No knowledge bases" /> }}
            renderItem={(base) => (
              <List.Item className={base.id === selectedId ? 'selected' : ''} onClick={() => setSelectedId(base.id)}>
                <List.Item.Meta title={base.name} description={`${base.role} · ${shortId(base.id)}`} />
              </List.Item>
            )}
          />
        </aside>
        <section className="knowledge-detail">
          {!selected ? (
            <Empty description="Create a knowledge base to continue" />
          ) : (
            <>
              <div className="detail-heading">
                <div>
                  <Title level={4}>{selected.name}</Title>
                  <Paragraph type="secondary">{selected.description || 'No description'}</Paragraph>
                </div>
                <Space>
                  {selected.role !== 'VIEWER' && (
                    <Tooltip title="Edit">
                      <Button icon={<EditOutlined />} onClick={() => openEditor(selected)} aria-label="Edit" />
                    </Tooltip>
                  )}
                  {selected.role === 'OWNER' && (
                    <Tooltip title="Delete">
                      <Button
                        danger
                        icon={<DeleteOutlined />}
                        aria-label="Delete"
                        onClick={() =>
                          Modal.confirm({
                            title: `Delete ${selected.name}?`,
                            content: 'All documents must be detached before deleting the base.',
                            okText: 'Delete',
                            okButtonProps: { danger: true },
                            onOk: async () => {
                              await deleteKnowledgeBase(token, selected.id);
                              await loadBases();
                            },
                          })
                        }
                      />
                    </Tooltip>
                  )}
                </Space>
              </div>
              {selected.role !== 'VIEWER' && (
                <Form
                  className="attach-row"
                  onFinish={async (values) => {
                    try {
                      await attachKnowledgeDocument(token, selected.id, values.documentId);
                      messageApi.success('Document attached');
                      await loadDocuments(selected.id);
                    } catch (reason) {
                      setError(errorMessage(reason));
                    }
                  }}
                >
                  <Form.Item name="documentId" rules={[{ required: true }]} noStyle>
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Select an uploaded document"
                      options={available.map((file) => ({ value: file.id, label: file.originalName }))}
                    />
                  </Form.Item>
                  <Button htmlType="submit" icon={<FileAddOutlined />} disabled={!available.length}>
                    Attach
                  </Button>
                </Form>
              )}
              <Table
                rowKey="documentId"
                loading={loading}
                dataSource={documents}
                pagination={false}
                scroll={{ x: 760 }}
                locale={{ emptyText: 'No documents attached' }}
                columns={[
                  {
                    title: 'Document',
                    key: 'document',
                    render: (_, item) => (
                      <div>
                        <Text strong>{fileMap.get(item.documentId)?.originalName || shortId(item.documentId)}</Text>
                        <small>{shortId(item.documentId)}</small>
                      </div>
                    ),
                  },
                  {
                    title: 'Status',
                    dataIndex: 'status',
                    key: 'status',
                    render: (value, item) => (
                      <Space>
                        <Tag color={statusColor(value)}>{value}</Tag>
                        {item.failureCode && <Text type="danger">{item.failureCode}</Text>}
                      </Space>
                    ),
                  },
                  { title: 'Updated', dataIndex: 'updatedAt', key: 'updated', render: formatDate, responsive: ['md'] },
                  {
                    title: '',
                    key: 'actions',
                    width: 108,
                    fixed: 'right',
                    render: (_, item) =>
                      selected.role === 'VIEWER' ? null : (
                        <Space>
                          <Tooltip title={item.status === 'READY' ? 'Already ready' : 'Process'}>
                            <Button
                              type="text"
                              icon={<PlayCircleOutlined />}
                              disabled={item.status === 'READY' || item.status === 'FAILED'}
                              loading={processing === item.documentId}
                              aria-label="Process"
                              onClick={async () => {
                                setProcessing(item.documentId);
                                setError('');
                                try {
                                  const result = await processKnowledgeDocument(token, selected.id, item.documentId);
                                  messageApi.success(
                                    `Ready: ${result.chunkCount} chunks, ${result.vectorCount} vectors`,
                                  );
                                  await loadDocuments(selected.id);
                                } catch (reason) {
                                  setError(errorMessage(reason));
                                  await loadDocuments(selected.id);
                                } finally {
                                  setProcessing('');
                                }
                              }}
                            />
                          </Tooltip>
                          <Tooltip title="Detach">
                            <Button
                              type="text"
                              danger
                              icon={<DeleteOutlined />}
                              aria-label="Detach"
                              onClick={() =>
                                Modal.confirm({
                                  title: 'Detach this document?',
                                  content: 'The uploaded file remains available in Documents.',
                                  okText: 'Detach',
                                  okButtonProps: { danger: true },
                                  onOk: async () => {
                                    await detachKnowledgeDocument(token, selected.id, item.documentId);
                                    await loadDocuments(selected.id);
                                  },
                                })
                              }
                            />
                          </Tooltip>
                        </Space>
                      ),
                  },
                ]}
              />
            </>
          )}
        </section>
      </div>
      <Modal
        title={editing ? 'Edit knowledge base' : 'New knowledge base'}
        open={editorOpen}
        onCancel={() => {
          setEditorOpen(false);
          setEditing(null);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(values) => void saveBase(values)}>
          <Form.Item label="Name" name="name" rules={[{ required: true }, { max: 120 }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Description" name="description" rules={[{ max: 1000 }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function statusColor(status: string): string {
  if (status === 'READY') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'PROCESSING') return 'blue';
  return 'default';
}
