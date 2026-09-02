package com.openeip.governance.application.context;

import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates W3C traceparent input and creates a trace ID when no parent was supplied. */
public final class TraceContext {
  private static final Pattern TRACEPARENT =
      Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

  private TraceContext() {}

  public static String resolveTraceId(String traceparent) {
    if (traceparent == null || traceparent.isBlank()) {
      return UUID.randomUUID().toString().replace("-", "");
    }
    Matcher matcher = TRACEPARENT.matcher(traceparent);
    if (!matcher.matches() || isAllZero(matcher.group(1)) || isAllZero(matcher.group(2))) {
      throw GovernanceAuthorizationException.invalidContext("Invalid traceparent");
    }
    return matcher.group(1);
  }

  private static boolean isAllZero(String value) {
    return value.chars().allMatch(character -> character == '0');
  }
}
