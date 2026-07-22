package com.openeip;

import com.openeip.auth.AuthServiceApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Composition root for the modular OpenEIP Java control plane. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(
    basePackages = "com.openeip",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = AuthServiceApplication.class))
public class PlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(PlatformApplication.class, args);
  }
}
