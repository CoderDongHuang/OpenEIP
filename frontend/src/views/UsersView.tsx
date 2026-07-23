import { ReloadOutlined, SaveOutlined, TeamOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Empty,
  Modal,
  Result,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { AdminUser, CurrentUser, Role, listRoles, listUsers, replaceUserRoles, setUserActive } from '../api';
import { errorMessage, formatDate } from '../format';

const { Paragraph, Text, Title } = Typography;

export function UsersView({ token, user }: { token: string; user: CurrentUser }) {
  const admin = user.roles.includes('ROLE_ADMIN');
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [draftRoles, setDraftRoles] = useState<Record<string, string[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [messageApi, contextHolder] = message.useMessage();
  const load = useCallback(async () => {
    if (!admin) return;
    setLoading(true);
    setError('');
    try {
      const [page, roleList] = await Promise.all([listUsers(token), listRoles(token)]);
      setUsers(page.items);
      setRoles(roleList);
      setDraftRoles(Object.fromEntries(page.items.map((item) => [item.id, item.roles])));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [admin, token]);
  useEffect(() => {
    void load();
  }, [load]);
  if (!admin)
    return (
      <Result
        status="403"
        title="Administrator access required"
        subTitle="User and role administration is restricted to ROLE_ADMIN."
      />
    );
  return (
    <div className="page-stack">
      {contextHolder}
      <section className="page-toolbar">
        <div>
          <Title level={3}>Users and access</Title>
          <Paragraph type="secondary">Review accounts, assign roles, and control sign-in access.</Paragraph>
        </div>
        <Tooltip title="Refresh">
          <Button icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Refresh" />
        </Tooltip>
      </section>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
      <Table
        rowKey="id"
        loading={loading}
        dataSource={users}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        scroll={{ x: 960 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No users" /> }}
        columns={[
          {
            title: 'User',
            key: 'user',
            render: (_, item) => (
              <div>
                <Text strong>{item.username}</Text>
                <small>{item.email}</small>
              </div>
            ),
          },
          {
            title: 'Roles',
            key: 'roles',
            width: 300,
            render: (_, item) => (
              <Space.Compact block>
                <Select
                  mode="multiple"
                  maxTagCount="responsive"
                  value={draftRoles[item.id]}
                  options={roles.map((role) => ({ value: role.name, label: role.name.replace('ROLE_', '') }))}
                  onChange={(value) => setDraftRoles((current) => ({ ...current, [item.id]: value }))}
                  style={{ width: '100%' }}
                />
                <Tooltip title="Save roles">
                  <Button
                    icon={<SaveOutlined />}
                    aria-label="Save roles"
                    disabled={!draftRoles[item.id]?.length}
                    onClick={async () => {
                      try {
                        await replaceUserRoles(token, item.id, draftRoles[item.id]);
                        messageApi.success('Roles updated');
                        await load();
                      } catch (reason) {
                        setError(errorMessage(reason));
                      }
                    }}
                  />
                </Tooltip>
              </Space.Compact>
            ),
          },
          {
            title: 'Status',
            key: 'status',
            render: (_, item) => (
              <Space>
                <Switch
                  checked={item.active}
                  disabled={item.id === user.id}
                  onChange={(checked) =>
                    Modal.confirm({
                      title: `${checked ? 'Enable' : 'Disable'} ${item.username}?`,
                      content: checked
                        ? 'The user will be able to sign in again.'
                        : 'Existing access and refresh tokens will stop working.',
                      okText: checked ? 'Enable' : 'Disable',
                      okButtonProps: { danger: !checked },
                      onOk: async () => {
                        try {
                          await setUserActive(token, item.id, checked);
                          await load();
                        } catch (reason) {
                          setError(errorMessage(reason));
                        }
                      },
                    })
                  }
                />
                <Tag color={item.active ? 'green' : 'default'}>{item.active ? 'ACTIVE' : 'DISABLED'}</Tag>
              </Space>
            ),
          },
          { title: 'Created', dataIndex: 'createdAt', key: 'created', render: formatDate, responsive: ['lg'] },
        ]}
      />
      <section className="role-reference">
        <Title level={4}>
          <TeamOutlined /> Role catalog
        </Title>
        <div>
          {roles.map((role) => (
            <div key={role.id}>
              <Text strong>{role.name}</Text>
              <Paragraph type="secondary">
                {role.description || 'No description'} · {role.permissions.length} permissions
              </Paragraph>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
