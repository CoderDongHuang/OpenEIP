package com.openeip.workflow;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.openeip.workflow")
@EnableJpaRepositories("com.openeip.workflow")
@ComponentScan("com.openeip.workflow")
public class WorkflowTestApplication {}
