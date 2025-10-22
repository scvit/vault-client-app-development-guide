package com.example.vaulttomcat.client;

import com.example.vaulttomcat.config.VaultConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Vault API 클라이언트
 */
public class VaultClient {
  private static final Logger logger = LoggerFactory.getLogger(VaultClient.class);

  private final String vaultUrl;
  private final String vaultNamespace;
  private String vaultToken; // final 제거
  private long tokenExpiry; // 추가
  private boolean renewable; // 추가
  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public VaultClient() {
    this.vaultUrl = VaultConfig.getVaultUrl();
    this.vaultNamespace = VaultConfig.getVaultNamespace();
    this.httpClient = HttpClients.createDefault();
    this.objectMapper = new ObjectMapper();

    // AppRole 인증 방식 사용
    if ("approle".equals(VaultConfig.getAuthType())) {
      authenticateWithAppRole();
    } else {
      // 기존 token 방식 fallback
      this.vaultToken = VaultConfig.getVaultToken();
    }

    logger.info("VaultClient initialized - URL: {}, Namespace: {}, Auth Type: {}",
        vaultUrl,
        vaultNamespace.isEmpty() ? "(root)" : vaultNamespace,
        VaultConfig.getAuthType());
  }

  /**
   * Vault API 호출
   */
  public Map<String, Object> callVaultApi(String path, String method, Map<String, Object> data) throws IOException {
    String url = vaultUrl + "/v1/" + path;

    if ("GET".equals(method)) {
      return callGetApi(url);
    } else if ("POST".equals(method)) {
      return callPostApi(url, data);
    } else {
      throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }
  }

  private Map<String, Object> callGetApi(String url) throws IOException {
    HttpGet request = new HttpGet(url);
    request.setHeader("X-Vault-Token", vaultToken);
    request.setHeader("Content-Type", "application/json");

    // Namespace 헤더 추가
    if (vaultNamespace != null && !vaultNamespace.isEmpty()) {
      request.setHeader("X-Vault-Namespace", vaultNamespace);
    }

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      return parseResponse(response);
    }
  }

  private Map<String, Object> callPostApi(String url, Map<String, Object> data) throws IOException {
    HttpPost request = new HttpPost(url);
    request.setHeader("X-Vault-Token", vaultToken);
    request.setHeader("Content-Type", "application/json");

    // Namespace 헤더 추가
    if (vaultNamespace != null && !vaultNamespace.isEmpty()) {
      request.setHeader("X-Vault-Namespace", vaultNamespace);
    }

    if (data != null && !data.isEmpty()) {
      String jsonData = objectMapper.writeValueAsString(data);
      request.setEntity(new StringEntity(jsonData, ContentType.APPLICATION_JSON));
    }

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      return parseResponse(response);
    }
  }

  private Map<String, Object> parseResponse(CloseableHttpResponse response) throws IOException {
    int statusCode = response.getCode();

    if (statusCode >= 200 && statusCode < 300) {
      JsonNode jsonNode = objectMapper.readTree(response.getEntity().getContent());
      return objectMapper.convertValue(jsonNode, Map.class);
    } else {
      logger.error("Vault API call failed with status: {}", statusCode);
      throw new IOException("Vault API call failed with status: " + statusCode);
    }
  }

  /**
   * KV v2 시크릿 조회
   */
  public Map<String, Object> getKvSecret(String path) throws IOException {
    logger.info("Getting KV secret from path: {}", path);
    return callVaultApi(path, "GET", null);
  }

  /**
   * Database Dynamic 시크릿 조회
   */
  public Map<String, Object> getDatabaseDynamicSecret(String role) throws IOException {
    String path = VaultConfig.getDatabasePath() + "/creds/" + role;
    logger.info("Getting Database Dynamic secret for role: {}", role);
    return callVaultApi(path, "GET", null);
  }

  /**
   * Database Static 시크릿 조회
   */
  public Map<String, Object> getDatabaseStaticSecret(String role) throws IOException {
    String path = VaultConfig.getDatabasePath() + "/static-creds/" + role;
    logger.info("Getting Database Static secret for role: {}", role);
    return callVaultApi(path, "GET", null);
  }

  /**
   * 토큰 정보 조회
   */
  public Map<String, Object> getTokenInfo() throws IOException {
    logger.info("Getting token info");
    return callVaultApi("auth/token/lookup-self", "GET", null);
  }

  /**
   * Lease 갱신
   */
  public Map<String, Object> renewLease(String leaseId) throws IOException {
    logger.info("Renewing lease: {}", leaseId);
    Map<String, Object> data = new HashMap<>();
    data.put("lease_id", leaseId);
    return callVaultApi("sys/leases/renew", "POST", data);
  }

  /**
   * AppRole 인증
   */
  private void authenticateWithAppRole() {
    try {
      String roleId = VaultConfig.getAppRoleId();
      String secretId = VaultConfig.getAppRoleSecretId();

      Map<String, Object> authData = new HashMap<>();
      authData.put("role_id", roleId);
      authData.put("secret_id", secretId);

      Map<String, Object> response = callAppRoleLogin(authData);

      Map<String, Object> auth = (Map<String, Object>) response.get("auth");
      this.vaultToken = (String) auth.get("client_token");

      // lease_duration (period 값)
      Integer leaseDuration = (Integer) auth.get("lease_duration");
      this.tokenExpiry = System.currentTimeMillis() + (leaseDuration * 1000L);
      this.renewable = (Boolean) auth.get("renewable");

      logger.info("AppRole 인증 성공 - Token TTL: {}초, Renewable: {}",
          leaseDuration, renewable);
    } catch (Exception e) {
      logger.error("AppRole 인증 실패: {}", e.getMessage());
      throw new RuntimeException("AppRole authentication failed", e);
    }
  }

  /**
   * AppRole 로그인 API 호출
   */
  private Map<String, Object> callAppRoleLogin(Map<String, Object> authData) throws IOException {
    String url = vaultUrl + "/v1/auth/approle/login";
    HttpPost request = new HttpPost(url);
    request.setHeader("Content-Type", "application/json");

    // Namespace 헤더 추가
    if (vaultNamespace != null && !vaultNamespace.isEmpty()) {
      request.setHeader("X-Vault-Namespace", vaultNamespace);
    }

    String jsonData = objectMapper.writeValueAsString(authData);
    request.setEntity(new StringEntity(jsonData, ContentType.APPLICATION_JSON));

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      return parseResponse(response);
    }
  }

  /**
   * Token 갱신
   */
  public boolean renewToken() {
    if (!renewable) {
      logger.warn("Token is not renewable");
      return false;
    }

    try {
      String url = vaultUrl + "/v1/auth/token/renew-self";
      HttpPost request = new HttpPost(url);
      request.setHeader("X-Vault-Token", vaultToken);
      request.setHeader("Content-Type", "application/json");

      try (CloseableHttpResponse response = httpClient.execute(request)) {
        Map<String, Object> result = parseResponse(response);
        Map<String, Object> auth = (Map<String, Object>) result.get("auth");

        Integer leaseDuration = (Integer) auth.get("lease_duration");
        this.tokenExpiry = System.currentTimeMillis() + (leaseDuration * 1000L);

        // Token 갱신 성공 (로깅은 TokenRenewalScheduler에서 처리)
        return true;
      }
    } catch (Exception e) {
      logger.error("Token 갱신 실패: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Token 만료 시간 조회
   */
  public long getTokenExpiry() {
    return tokenExpiry;
  }

  /**
   * Token 갱신 가능 여부
   */
  public boolean isRenewable() {
    return renewable;
  }

  /**
   * Token 갱신 필요 여부 확인
   */
  public boolean shouldRenew() {
    if (!renewable)
      return false;

    long now = System.currentTimeMillis();
    long remainingTtl = tokenExpiry - now;
    long threshold = (long) ((tokenExpiry - (now - remainingTtl)) * VaultConfig.getTokenRenewalThreshold());

    return remainingTtl <= threshold;
  }

  /**
   * 리소스 정리
   */
  public void close() {
    try {
      httpClient.close();
    } catch (IOException e) {
      logger.error("Error closing HTTP client", e);
    }
  }
}
