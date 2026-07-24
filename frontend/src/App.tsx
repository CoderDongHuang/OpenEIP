import {
  ApartmentOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MessageOutlined,
  RobotOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  ConfigProvider,
  Drawer,
  Form,
  Input,
  Layout,
  Menu,
  Space,
  Spin,
  Tabs,
  Tooltip,
  Typography,
} from 'antd';
import { lazy, Suspense, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

import './App.css';
import { CurrentUser, currentUser, login, register } from './api';

const AgentView = lazy(() => import('./views/AgentView').then((module) => ({ default: module.AgentView })));
const ChatView = lazy(() => import('./views/ChatView').then((module) => ({ default: module.ChatView })));
const DocumentsView = lazy(() => import('./views/DocumentsView').then((module) => ({ default: module.DocumentsView })));
const KnowledgeView = lazy(() => import('./views/KnowledgeView').then((module) => ({ default: module.KnowledgeView })));
const OverviewView = lazy(() => import('./views/OverviewView').then((module) => ({ default: module.OverviewView })));
const UsersView = lazy(() => import('./views/UsersView').then((module) => ({ default: module.UsersView })));
const WorkflowView = lazy(() => import('./views/WorkflowView').then((module) => ({ default: module.WorkflowView })));

const { Header, Sider, Content } = Layout;
const { Text, Title } = Typography;
const TOKEN_KEY = 'openeip.accessToken';

const navigation = [
  { key: '/overview', icon: <HomeOutlined />, label: 'Overview' },
  { key: '/documents', icon: <FileTextOutlined />, label: 'Documents' },
  { key: '/knowledge', icon: <DatabaseOutlined />, label: 'Knowledge' },
  { key: '/chat', icon: <MessageOutlined />, label: 'Chat' },
  { key: '/agents', icon: <RobotOutlined />, label: 'Agents' },
  { key: '/workflows', icon: <ApartmentOutlined />, label: 'Workflows' },
  { key: '/users', icon: <TeamOutlined />, label: 'Access' },
];

function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) || '');
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(Boolean(token));

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    let active = true;
    currentUser(token)
      .then((value) => active && setUser(value))
      .catch(() => active && clearAuthentication())
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [token]);

  function clearAuthentication() {
    sessionStorage.removeItem(TOKEN_KEY);
    setToken('');
    setUser(null);
    setLoading(false);
  }

  async function authenticate(username: string, password: string) {
    const pair = await login(username, password);
    sessionStorage.setItem(TOKEN_KEY, pair.accessToken);
    setLoading(true);
    setToken(pair.accessToken);
  }

  if (loading)
    return (
      <div className="loading-shell">
        <Spin size="large" />
      </div>
    );
  if (!token || !user) return <AuthView onLogin={authenticate} />;

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#18745a',
          colorInfo: '#18745a',
          borderRadius: 6,
          colorBgLayout: '#f3f5f4',
          colorText: '#1c2421',
          colorTextSecondary: '#5d6a65',
          controlHeight: 38,
          fontSize: 14,
        },
        components: { Layout: { headerBg: '#171b1d', siderBg: '#202629' }, Menu: { darkItemBg: '#202629' } },
      }}
    >
      <BrowserRouter>
        <Workspace token={token} user={user} onLogout={clearAuthentication} />
      </BrowserRouter>
    </ConfigProvider>
  );
}

function Workspace({ token, user, onLogout }: { token: string; user: CurrentUser; onLogout: () => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileNav, setMobileNav] = useState(false);
  const pageTitle = navigation.find((item) => location.pathname.startsWith(item.key))?.label || 'Overview';
  const menu = (
    <Menu
      theme="dark"
      mode="inline"
      selectedKeys={[location.pathname]}
      items={navigation}
      onClick={({ key }) => {
        navigate(key);
        setMobileNav(false);
      }}
    />
  );
  return (
    <Layout className="app-shell">
      <Sider className="desktop-sider" width={228}>
        <div className="brand-lockup">
          <span className="brand-symbol">O</span>
          <span>
            <strong>OpenEIP</strong>
            <small>Enterprise AI</small>
          </span>
        </div>
        <span className="navigation-label">Workspace</span>
        {menu}
        <div className="sider-release">
          <span className="release-dot" />
          <span>
            <strong>v0.4 alpha</strong>
            <small>Single-node profile</small>
          </span>
        </div>
      </Sider>
      <Layout>
        <Header className="app-header">
          <Space>
            <Button
              className="mobile-menu-button"
              type="text"
              icon={<MenuFoldOutlined />}
              onClick={() => setMobileNav(true)}
              aria-label="Open navigation"
            />
            <Title level={2}>{pageTitle}</Title>
          </Space>
          <Space size="middle">
            <Button className="account" type="text" icon={<UserOutlined />} onClick={() => navigate('/users')}>
              <Text>{user.username}</Text>
            </Button>
            <Tooltip title="Log out">
              <Button type="text" danger icon={<LogoutOutlined />} onClick={onLogout} aria-label="Log out" />
            </Tooltip>
          </Space>
        </Header>
        <Content className="page-content">
          <Suspense
            fallback={
              <div className="centered">
                <Spin />
              </div>
            }
          >
            <Routes>
              <Route path="/overview" element={<OverviewView token={token} user={user} />} />
              <Route path="/documents" element={<DocumentsView token={token} />} />
              <Route path="/knowledge" element={<KnowledgeView token={token} />} />
              <Route path="/chat" element={<ChatView token={token} />} />
              <Route path="/agents" element={<AgentView token={token} />} />
              <Route path="/workflows" element={<WorkflowView token={token} />} />
              <Route path="/users" element={<UsersView token={token} user={user} />} />
              <Route path="*" element={<Navigate to="/overview" replace />} />
            </Routes>
          </Suspense>
        </Content>
      </Layout>
      <Drawer
        title="OpenEIP"
        placement="left"
        width={260}
        open={mobileNav}
        onClose={() => setMobileNav(false)}
        className="mobile-drawer"
      >
        {menu}
      </Drawer>
    </Layout>
  );
}

function AuthView({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(action: () => Promise<void>) {
    setError('');
    setBusy(true);
    try {
      await action();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Authentication failed');
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-shell">
      <section className="login-panel">
        <span className="login-mark" />
        <Title level={1}>OpenEIP</Title>
        <Text type="secondary">Enterprise intelligence workspace</Text>
        {error && <Alert type="error" message={error} showIcon closable onClose={() => setError('')} />}
        <Tabs
          defaultActiveKey="login"
          items={[
            {
              key: 'login',
              label: 'Sign in',
              children: (
                <Form
                  layout="vertical"
                  requiredMark={false}
                  onFinish={(v) => void submit(() => onLogin(v.username, v.password))}
                >
                  <Form.Item label="Username" name="username" rules={[{ required: true }]}>
                    <Input prefix={<UserOutlined />} maxLength={64} autoComplete="username" />
                  </Form.Item>
                  <Form.Item label="Password" name="password" rules={[{ required: true }]}>
                    <Input.Password maxLength={72} autoComplete="current-password" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={busy} block>
                    Sign in
                  </Button>
                </Form>
              ),
            },
            {
              key: 'register',
              label: 'Create account',
              children: (
                <Form
                  layout="vertical"
                  requiredMark={false}
                  onFinish={(v) =>
                    void submit(async () => {
                      await register(v.username, v.email, v.password);
                      await onLogin(v.username, v.password);
                    })
                  }
                >
                  <Form.Item
                    label="Username"
                    name="username"
                    rules={[{ required: true }, { min: 3 }, { pattern: /^[A-Za-z0-9_.-]+$/ }]}
                  >
                    <Input maxLength={64} autoComplete="username" />
                  </Form.Item>
                  <Form.Item label="Email" name="email" rules={[{ required: true }, { type: 'email' }]}>
                    <Input maxLength={255} autoComplete="email" />
                  </Form.Item>
                  <Form.Item label="Password" name="password" rules={[{ required: true }, { min: 8 }]}>
                    <Input.Password maxLength={72} autoComplete="new-password" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={busy} block>
                    Create account
                  </Button>
                </Form>
              ),
            },
          ]}
        />
      </section>
      <aside className="login-context">
        <div className="login-story">
          <span className="login-kicker">Open enterprise intelligence platform</span>
          <h2>From governed source files to grounded answers.</h2>
          <p>One focused workspace for knowledge preparation, streaming chat, and bounded agent execution.</p>
          <div className="login-visual" aria-hidden="true">
            <div className="visual-topbar">
              <span />
              <i />
              <i />
              <i />
            </div>
            <div className="visual-body">
              <div className="visual-rail">
                <b />
                <span />
                <span />
                <span />
                <span />
              </div>
              <div className="visual-content">
                <div className="visual-title" />
                <div className="visual-metrics">
                  <span />
                  <span />
                  <span />
                </div>
                <div className="visual-panel">
                  <div>
                    <b /> <span />
                  </div>
                  <div>
                    <b /> <span />
                  </div>
                  <div>
                    <b /> <span />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>
  );
}

export default App;
