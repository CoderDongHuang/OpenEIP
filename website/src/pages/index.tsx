import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import type {ReactNode} from 'react';

import styles from './index.module.css';

export default function Home(): ReactNode {
  return (
    <Layout title="MVP" description="OpenEIP MVP engineering and architecture documentation">
      <main className={styles.main}>
        <section className={styles.intro}>
          <p className={styles.release}>v0.2.0-alpha MVP</p>
          <Heading as="h1">Open Enterprise Intelligence Platform</Heading>
          <p>从认证、文档和知识库到流式 RAG 与约束 Agent 的单节点 MVP。</p>
          <div className={styles.actions}>
            <Link className="button button--primary" to="/docs/intro">
              查看文档
            </Link>
            <Link className="button button--secondary" href="https://github.com/CoderDongHuang/OpenEIP">
              查看源码
            </Link>
          </div>
        </section>
      </main>
    </Layout>
  );
}
