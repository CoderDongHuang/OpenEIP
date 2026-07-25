import { ApiOutlined, DeleteOutlined, PauseCircleOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import {
  ConnectorInstance,
  ConnectorCatalogEntry,
  ConnectorConfigField,
  ConnectorType,
  createConnector,
  deleteConnector,
  listConnectors,
  listConnectorCatalog,
  setConnectorStatus,
} from '../api';

const types: ConnectorType[] = [
  'MYSQL',
  'POSTGRESQL',
  'ORACLE',
  'SAP',
  'REDIS',
  'KAFKA',
  'GITHUB',
  'GITLAB',
  'FEISHU',
  'WECOM',
  'JIRA',
  'CONFLUENCE',
  'MINIO',
  'OSS',
  'EMAIL',
  'WEBHOOK',
];

export function ConnectorsView({ token }: { token: string }) {
  const [items, setItems] = useState<ConnectorInstance[]>([]);
  const [catalog, setCatalog] = useState<ConnectorCatalogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [page, entries] = await Promise.all([listConnectors(token), listConnectorCatalog(token)]);
      setItems(page.items);
      setCatalog(entries);
      setError('');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to load connectors');
    } finally {
      setLoading(false);
    }
  }, [token]);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function submit(values: Record<string, unknown>) {
    try {
      const type = values.type as ConnectorType;
      const schema = catalog.find((entry) => entry.metadata.type === type)?.configSchema || [];
      const config = Object.fromEntries(
        schema
          .filter((field) => !field.secret && values[field.name] !== undefined && values[field.name] !== '')
          .map((field) => [field.name, values[field.name]]),
      );
      const created = await createConnector(
        token,
        values.name as string,
        type,
        config,
        values.credentialRef as string | undefined,
      );
      setItems((current) => [created, ...current]);
      setOpen(false);
      form.resetFields();
      message.success('Connector created');
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : 'Unable to create connector');
    }
  }

  async function toggle(item: ConnectorInstance) {
    try {
      const updated = await setConnectorStatus(token, item.id, item.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE');
      setItems((current) => current.map((value) => (value.id === item.id ? updated : value)));
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : 'Unable to update connector');
    }
  }

  async function remove(item: ConnectorInstance) {
    try {
      await deleteConnector(token, item.id);
      setItems((current) => current.filter((value) => value.id !== item.id));
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : 'Unable to delete connector');
    }
  }

  return (
    <div className="view-stack">
      <Card>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Space style={{ justifyContent: 'space-between', width: '100%' }}>
            <Space>
              <ApiOutlined />
              <Typography.Title level={3} style={{ margin: 0 }}>
                Connectors
              </Typography.Title>
            </Space>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
              Add connector
            </Button>
          </Space>
          {error && <Alert type="error" message={error} showIcon />}
          <Table
            rowKey="id"
            loading={loading}
            dataSource={items}
            pagination={false}
            columns={[
              { title: 'Name', dataIndex: 'name' },
              { title: 'Type', dataIndex: 'type', render: (value: ConnectorType) => <Tag>{value}</Tag> },
              {
                title: 'Status',
                dataIndex: 'status',
                render: (value: string) => (
                  <Tag color={value === 'ACTIVE' ? 'green' : value === 'ERROR' ? 'red' : 'default'}>{value}</Tag>
                ),
              },
              {
                title: 'Credential',
                dataIndex: 'credentialRef',
                render: (value: string | null) => value || 'Not configured',
              },
              {
                title: '',
                key: 'actions',
                render: (_: unknown, item: ConnectorInstance) => (
                  <Space>
                    <Button
                      size="small"
                      icon={item.status === 'ACTIVE' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                      onClick={() => void toggle(item)}
                      aria-label={item.status === 'ACTIVE' ? 'Pause' : 'Activate'}
                    />
                    <Button
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => void remove(item)}
                      aria-label="Delete"
                    />
                  </Space>
                ),
              },
            ]}
          />
        </Space>
      </Card>
      <Modal
        title="Add connector"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        okText="Create"
      >
        <Form form={form} layout="vertical" onFinish={submit} initialValues={{ type: 'MYSQL' }}>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 120 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="Type" rules={[{ required: true }]}>
            <Select options={types.map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(previous, current) => previous.type !== current.type}>
            {({ getFieldValue }) => {
              const schema = catalog.find((entry) => entry.metadata.type === getFieldValue('type'))?.configSchema || [];
              return schema
                .filter((field) => !field.secret)
                .map((field) => <SchemaField key={field.name} field={field} />);
            }}
          </Form.Item>
          <Form.Item name="credentialRef" label="Credential reference">
            <Input placeholder="secret://..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
function SchemaField({ field }: { field: ConnectorConfigField }) {
  const rules = [{ required: field.required }];
  if (field.type === 'BOOLEAN')
    return (
      <Form.Item
        name={field.name}
        label={field.label}
        valuePropName="checked"
        initialValue={field.defaultValue === 'true'}
        rules={rules}
      >
        <Select
          options={[
            { value: true, label: 'Enabled' },
            { value: false, label: 'Disabled' },
          ]}
        />
      </Form.Item>
    );
  if (field.type === 'SELECT')
    return (
      <Form.Item name={field.name} label={field.label} initialValue={field.defaultValue || undefined} rules={rules}>
        <Select options={field.options.map((value) => ({ value, label: value }))} />
      </Form.Item>
    );
  return (
    <Form.Item name={field.name} label={field.label} initialValue={field.defaultValue || undefined} rules={rules}>
      <Input type={field.type === 'NUMBER' ? 'number' : 'text'} />
    </Form.Item>
  );
}
