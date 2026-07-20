package com.openeip.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenEIPApplicationTest {

  @Test
  void exposesFoundationMetadata() {
    assertThat(OpenEIPApplication.NAME).isEqualTo("OpenEIP Platform");
    assertThat(OpenEIPApplication.VERSION).isEqualTo("0.1.0-alpha");
  }
}
