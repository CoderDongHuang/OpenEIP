import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import type {ReactNode} from 'react';

import styles from './index.module.css';

export default function Home(): ReactNode {
  return (
    <Layout title="Foundation" description="OpenEIP engineering and architecture documentation">
      <main className={styles.main}>
        <section className={styles.intro}>
          <p className={styles.release}>v0.1.0-alpha Foundation</p>
          <Heading as="h1">Open Enterprise Intelligence Platform</Heading>
          <p>工程治理、架构基线和可运行脚手架已经形成一个可验证的起点。</p>
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
