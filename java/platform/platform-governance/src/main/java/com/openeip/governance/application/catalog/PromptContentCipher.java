package com.openeip.governance.application.catalog;

/** Encryption boundary for Prompt content at rest. Plaintext must not cross persistence ports. */
public interface PromptContentCipher {
  String encrypt(String plaintext);

  String digest(String plaintext);
}
