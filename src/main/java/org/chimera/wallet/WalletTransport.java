package org.chimera.wallet;

import java.util.Map;

public interface WalletTransport {
  Map<String, Object> post(String path, Map<String, String> headers, Map<String, Object> payload);
}
