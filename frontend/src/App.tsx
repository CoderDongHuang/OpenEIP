import { Button, Layout, Typography, Space } from 'antd';
import { GithubOutlined } from '@ant-design/icons';

const { Header, Content, Footer } = Layout;
const { Title, Paragraph } = Typography;

function App() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#001529',
        }}
      >
        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          OpenEIP
        </Title>
        <Space>
          <Button
            type="link"
            icon={<GithubOutlined />}
            href="https://github.com/CoderDongHuang/OpenEIP"
            target="_blank"
          >
            GitHub
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: '80px 48px', textAlign: 'center' }}>
        <Title level={1}>OpenEIP Foundation</Title>
        <Paragraph style={{ fontSize: 18, color: '#666', maxWidth: 600, margin: '24px auto' }}>
          v0.1.0-alpha services are online.
        </Paragraph>
        <Space size="large">
          <Button type="primary" size="large" href="/api/v1/platform/info">
            Platform API
          </Button>
          <Button size="large" href="/ai/health">
            AI Engine Health
          </Button>
        </Space>
      </Content>
      <Footer style={{ textAlign: 'center' }}>OpenEIP ©2026. Licensed under Apache 2.0.</Footer>
    </Layout>
  );
}

export default App;
