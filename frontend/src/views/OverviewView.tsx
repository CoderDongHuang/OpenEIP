import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  InboxOutlined,
  MessageOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { Alert, Button, Col, Progress, Row, Skeleton, Statistic, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { CurrentUser, listAgents, listFiles, listKnowledgeBases, listKnowledgeDocuments, listSessions } from '../api';
import { errorMessage } from '../format';

const { Paragraph, Title } = Typography;

export function OverviewView({ token, user }: { token: string; user: CurrentUser }) {
  const navigate = useNavigate();
  const [counts, setCounts] = useState<{
    files: number;
    bases: number;
    sessions: number;
    agents: number;
    searchable: number;
    attention: number;
    processing: number;
    processable: number;
    storedOnly: number;
  }>();
  const [error, setError] = useState('');
  useEffect(() => {
    Promise.all([listFiles(token), listKnowledgeBases(token), listSessions(token), listAgents(token)])
      .then(async ([files, bases, sessions, agents]) => {
        const documents = (await Promise.all(bases.items.map((base) => listKnowledgeDocuments(token, base.id)))).flat();
        const storedOnlyIds = new Set(
          files.items.filter((file) => file.contentType === 'application/pdf').map((file) => file.id),
        );
        const processable = documents.filter((document) => !storedOnlyIds.has(document.documentId));
        setCounts({
          files: files.total,
          bases: bases.total,
          sessions: sessions.length,
          agents: agents.length,
          searchable: processable.filter((document) => document.status === 'READY').length,
          attention: processable.filter((document) => document.status === 'FAILED').length,
          processing: processable.filter((document) => !['READY', 'FAILED'].includes(document.status)).length,
          processable: processable.length,
          storedOnly: documents.length - processable.length,
        });
      })
      .catch((reason) => setError(errorMessage(reason)));
  }, [token]);
  return (
    <div className="page-stack">
      <section className="page-intro overview-intro">
        <span className="section-kicker">Operational workspace</span>
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
      {counts && (
        <section className="processing-health">
          <div className="health-heading">
            <div>
              <Title level={4}>Knowledge processing</Title>
              <Paragraph type="secondary">Current state across attached sources.</Paragraph>
            </div>
            <Progress
              type="circle"
              size={58}
              percent={counts.processable ? Math.round((counts.searchable / counts.processable) * 100) : 0}
              strokeColor="#18745a"
            />
          </div>
          <div className="health-grid">
            <div>
              <CheckCircleOutlined />
              <span>Searchable</span>
              <strong>{counts.searchable}</strong>
            </div>
            <div>
              <ClockCircleOutlined />
              <span>Waiting or processing</span>
              <strong>{counts.processing}</strong>
            </div>
            <div className={counts.attention ? 'needs-attention' : ''}>
              <ExclamationCircleOutlined />
              <span>Needs attention</span>
              <strong>{counts.attention}</strong>
            </div>
            <div>
              <InboxOutlined />
              <span>Stored only</span>
              <strong>{counts.storedOnly}</strong>
            </div>
          </div>
        </section>
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
