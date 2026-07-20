package com.openeip.auth.api;

import com.openeip.common.OpenEIPApplication;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides immutable information about the running foundation service. */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformInfoController {

  @GetMapping("/info")
  public Map<String, String> info() {
    return Map.of("name", OpenEIPApplication.NAME, "version", OpenEIPApplication.VERSION);
  }
}
