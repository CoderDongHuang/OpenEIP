import { DeleteOutlined, DownloadOutlined, FileAddOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Alert, Button, List, Modal, Space, Table, Tag, Tooltip, Typography, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { DocumentFile, deleteFile, downloadFile, listFiles, uploadFile } from '../api';
import { errorMessage, formatBytes, formatDate, shortId } from '../format';

const { Paragraph, Text, Title } = Typography;

export function DocumentsView({ token }: { token: string }) {
  const [files, setFiles] = useState<DocumentFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [messageApi, contextHolder] = message.useMessage();
  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setFiles((await listFiles(token)).items);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [token]);
  useEffect(() => {
    void load();
  }, [load]);
  const upload: UploadProps['customRequest'] = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    try {
      await uploadFile(token, file as File);
      onSuccess?.({});
      messageApi.success('Document uploaded');
      await load();
    } catch (reason) {
      const error = reason instanceof Error ? reason : new Error('Upload failed');
      onError?.(error);
      setError(error.message);
    } finally {
      setUploading(false);
    }
  };
  return (
    <div className="page-stack">
      {contextHolder}
      <section className="page-toolbar">
        <div>
          <Title level={3}>Documents</Title>
          <Paragraph type="secondary">
            Upload text, image, PDF, Word, PowerPoint, or Excel sources for processing.
          </Paragraph>
        </div>
        <Space>
          <Tooltip title="Refresh">
            <Button icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Refresh" />
          </Tooltip>
          <Upload
            customRequest={upload}
            showUploadList={false}
            accept=".txt,.png,.jpg,.jpeg,.pdf,.docx,.pptx,.xlsx"
            multiple={false}
          >
            <Button type="primary" icon={<UploadOutlined />} loading={uploading}>
              Upload
            </Button>
          </Upload>
        </Space>
      </section>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <Table
        className="desktop-record-table"
        rowKey="id"
        loading={loading}
        dataSource={files}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        locale={{
          emptyText: (
            <div className="empty-action">
              <FileAddOutlined />
              <span>No documents uploaded</span>
            </div>
          ),
        }}
        columns={[
          {
            title: 'Name',
            dataIndex: 'originalName',
            key: 'name',
            render: (value, file) => (
              <div>
                <Text strong>{value}</Text>
                <small>{shortId(file.id)}</small>
              </div>
            ),
          },
          { title: 'Type', dataIndex: 'contentType', key: 'type', responsive: ['md'] },
          { title: 'Size', dataIndex: 'sizeBytes', key: 'size', render: formatBytes },
          {
            title: 'Availability',
            key: 'status',
            render: () => <Tag color="green">Processable</Tag>,
          },
          { title: 'Uploaded', dataIndex: 'createdAt', key: 'date', render: formatDate, responsive: ['lg'] },
          {
            title: '',
            key: 'actions',
            width: 100,
            fixed: 'right',
            render: (_, file) => (
              <Space>
                <Tooltip title="Download">
                  <Button
                    type="text"
                    icon={<DownloadOutlined />}
                    onClick={() => void downloadFile(token, file).catch((r) => setError(errorMessage(r)))}
                    aria-label="Download"
                  />
                </Tooltip>
                <Tooltip title="Delete">
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label="Delete"
                    onClick={() =>
                      Modal.confirm({
                        title: `Delete ${file.originalName}?`,
                        content: 'Knowledge-base attachments must be removed first.',
                        okText: 'Delete',
                        okButtonProps: { danger: true },
                        onOk: async () => {
                          await deleteFile(token, file.id);
                          await load();
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
      <List
        className="mobile-record-list"
        dataSource={files}
        locale={{ emptyText: 'No documents uploaded' }}
        renderItem={(file) => (
          <List.Item
            actions={[
              <Button
                key="download"
                type="text"
                icon={<DownloadOutlined />}
                onClick={() => void downloadFile(token, file).catch((reason) => setError(errorMessage(reason)))}
                aria-label="Download"
              />,
              <Button
                key="delete"
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label="Delete"
                onClick={() =>
                  Modal.confirm({
                    title: `Delete ${file.originalName}?`,
                    content: 'Knowledge-base attachments must be removed first.',
                    okText: 'Delete',
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await deleteFile(token, file.id);
                      await load();
                    },
                  })
                }
              />,
            ]}
          >
            <List.Item.Meta
              title={file.originalName}
              description={
                <Space direction="vertical" size={2}>
                  <Text type="secondary">
                    {formatBytes(file.sizeBytes)} / {formatDate(file.createdAt)}
                  </Text>
                  <Tag color="green">Processable</Tag>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </div>
  );
}
