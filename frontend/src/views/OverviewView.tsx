import { DatabaseOutlined, FileTextOutlined, MessageOutlined, RobotOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Row, Skeleton, Statistic, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { CurrentUser, listAgents, listFiles, listKnowledgeBases, listSessions } from '../api';
import { errorMessage } from '../format';

const { Paragraph, Title } = Typography;

export function OverviewView({ token, user }: { token: string; user: CurrentUser }) {
  const navigate = useNavigate();
  const [counts, setCounts] = useState<{ files: number; bases: number; sessions: number; agents: number }>();
  const [error, setError] = useState('');
  useEffect(() => {
    Promise.all([listFiles(token, 1, 1), listKnowledgeBases(token, 1, 1), listSessions(token), listAgents(token)])
      .then(([files, bases, sessions, agents]) =>
        setCounts({ files: files.total, bases: bases.total, sessions: sessions.length, agents: agents.length }),
      )
      .catch((reason) => setError(errorMessage(reason)));
  }, [token]);
  return (
    <div className="page-stack">
      <section className="page-intro">
        <Title level={3}>Welcome, {user.username}</Title>
        <Paragraph type="secondary">
          Upload source material, prepare a knowledge base, then use grounded Chat or a constrained Agent.
        </Paragraph>
      </section>
      {error && <Alert type="error" message={error} showIcon />}
      {!counts ? (
        <Skeleton active />
      ) : (
        <Row gutter={[16, 16]} className="metric-grid">
          <Col xs={12} lg={6}>
            <Statistic title="Documents" value={counts.files} prefix={<FileTextOutlined />} />
          </Col>
          <Col xs={12} lg={6}>
            <Statistic title="Knowledge bases" value={counts.bases} prefix={<DatabaseOutlined />} />
          </Col>
          <Col xs={12} lg={6}>
            <Statistic title="Chat sessions" value={counts.sessions} prefix={<MessageOutlined />} />
          </Col>
          <Col xs={12} lg={6}>
            <Statistic title="Agents" value={counts.agents} prefix={<RobotOutlined />} />
          </Col>
        </Row>
      )}
      <section className="workflow-strip">
        <Title level={4}>Workspace flow</Title>
        <div className="workflow-actions">
          <Button icon={<FileTextOutlined />} onClick={() => navigate('/documents')}>
            Upload documents
          </Button>
          <span>1</span>
          <Button icon={<DatabaseOutlined />} onClick={() => navigate('/knowledge')}>
            Build knowledge
          </Button>
          <span>2</span>
          <Button icon={<MessageOutlined />} onClick={() => navigate('/chat')}>
            Start a chat
          </Button>
          <span>3</span>
          <Button icon={<RobotOutlined />} onClick={() => navigate('/agents')}>
            Run an agent
          </Button>
        </div>
      </section>
    </div>
  );
}
