package com.openeip.document;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Isolated Spring composition root for document integration tests. */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.openeip.document")
@EnableJpaRepositories("com.openeip.document")
@ComponentScan("com.openeip.document")
public class DocumentTestApplication {}
