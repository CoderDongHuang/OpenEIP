package com.openeip.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.shared.WorkflowException;
import org.junit.jupiter.api.Test;

class WorkflowGraphValidatorTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final WorkflowGraphValidator validator = new WorkflowGraphValidator(mapper);

  @Test
  void canonicalizesValidAcyclicGraphAndProducesStableDigest() throws Exception {
    var graph =
        mapper.readTree(
            """
        {"edges":[{"targetPort":"in","target":"end","sourcePort":"out","source":"start","id":"edge"}],
        "nodes":[{"config":{},"position":{"y":0,"x":0},"schemaVersion":1,"type":"START","id":"start"},
        {"config":{},"position":{"x":1,"y":0},"schemaVersion":1,"type":"END","id":"end"}],"schemaVersion":1}
        """);
    String canonical = validator.canonical(graph);

    assertThat(validator.validate(graph).valid()).isTrue();
    assertThat(canonical).startsWith("{\"edges\"");
    assertThat(WorkflowGraphValidator.sha256(canonical)).hasSize(64);
  }

  @Test
  void rejectsSchemaLimitsDuplicatesUnknownEdgesUnreachableNodesAndCycles() throws Exception {
    assertThat(validator.validate(mapper.readTree("{}")).valid()).isFalse();
    assertThat(
            validator
                .validate(
                    mapper.readTree(
                        """
        {"schemaVersion":1,"nodes":[],"edges":[]}
        """))
                .valid())
        .isFalse();
    assertThat(
            validator
                .validate(
                    mapper.readTree(
                        """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"start","type":"END","schemaVersion":1,"position":{"x":1,"y":0},"config":{}}],
        "edges":[{"id":"bad","source":"missing","sourcePort":"out","target":"start","targetPort":"in"}]}
        """))
                .valid())
        .isFalse();
    var unreachable =
        mapper.readTree(
            """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":1,"y":0},"config":{}},
        {"id":"extra","type":"TOOL","schemaVersion":1,"position":{"x":2,"y":0},"config":{}}],
        "edges":[{"id":"edge","source":"start","sourcePort":"out","target":"end","targetPort":"in"}]}
        """);
    assertThat(validator.validate(unreachable).errors()).extracting("code").contains("WF-V-008");
    assertThatThrownBy(() -> validator.canonical(unreachable))
        .isInstanceOf(WorkflowException.class);
  }

  @Test
  void rejectsUnknownFieldsAndInvalidPortsOrPositions() throws Exception {
    var unknownGraphField =
        mapper.readTree(
            """
        {"schemaVersion":1,"unknown":true,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":1,"y":0},"config":{}}],
        "edges":[{"id":"edge","source":"start","sourcePort":"out","target":"end","targetPort":"in"}]}
        """);
    assertThat(validator.validate(unknownGraphField).errors())
        .extracting("code")
        .contains("WF-V-001");

    var invalidNodeAndEdge =
        mapper.readTree(
            """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,
        "position":{"x":100001,"y":0,"z":1},"config":{},"unknown":true},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":1,"y":0},"config":{}}],
        "edges":[{"id":"edge","source":"start","sourcePort":"bad port",
        "target":"end","targetPort":"in","unknown":true}]}
        """);
    assertThat(validator.validate(invalidNodeAndEdge).errors())
        .extracting("code")
        .contains("WF-V-003", "WF-V-006");
  }
}
