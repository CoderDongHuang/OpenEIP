package com.openeip.document.domain.storage;

import java.io.IOException;
import java.io.InputStream;

/** Internal port for bounded object persistence. */
public interface ObjectStorage {

  StoredObject put(String objectKey, InputStream content, long maxBytes) throws IOException;

  InputStream open(String objectKey) throws IOException;

  void delete(String objectKey) throws IOException;

  record StoredObject(long sizeBytes, String sha256) {}
}
