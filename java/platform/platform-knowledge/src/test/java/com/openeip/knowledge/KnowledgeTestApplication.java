package com.openeip.knowledge;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Isolated composition root for knowledge control-plane tests. */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan({"com.openeip.knowledge", "com.openeip.document"})
@EnableJpaRepositories({"com.openeip.knowledge", "com.openeip.document"})
@ComponentScan("com.openeip.knowledge")
public class KnowledgeTestApplication {}
