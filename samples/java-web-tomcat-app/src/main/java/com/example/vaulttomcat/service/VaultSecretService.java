package com.example.vaulttomcat.service;

import com.example.vaulttomcat.client.VaultClient;
import com.example.vaulttomcat.config.VaultConfig;
import com.example.vaulttomcat.model.SecretInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Vault 시크릿 조회 및 관리 서비스
 */
public class VaultSecretService {
  private static final Logger logger = LoggerFactory.getLogger(VaultSecretService.class);

  private final VaultClient vaultClient;

  // Secret 캐싱 변수들
  private SecretInfo cachedKvSecret;
  private SecretInfo cachedDbStaticSecret;
  private long lastKvRefresh = 0;
  private long lastDbStaticRefresh = 0;

  // 캐시 유효 시간 (밀리초) - 1시간
  private static final long CACHE_VALIDITY_MS = 60 * 60 * 1000;

  public VaultSecretService() {
    this.vaultClient = new VaultClient();
  }

  /**
   * KV 시크릿 조회 (캐싱 적용)
   */
  public SecretInfo getKvSecret() {
    long currentTime = System.currentTimeMillis();

    // 캐시가 유효한 경우 캐시된 값 반환
    if (cachedKvSecret != null && (currentTime - lastKvRefresh) < CACHE_VALIDITY_MS) {
      logger.info("📦 KV Secret 캐시에서 반환 (마지막 갱신: {}초 전)",
          (currentTime - lastKvRefresh) / 1000);
      return cachedKvSecret;
    }

    try {
      logger.info("=== KV Secret Refresh (Vault에서 조회) ===");

      String kvPath = VaultConfig.getDatabaseKvPath();
      Map<String, Object> response = vaultClient.getKvSecret(kvPath);

      if (response != null && response.containsKey("data")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        // KV v2에서 실제 데이터는 'data' 키 안에 있음
        @SuppressWarnings("unchecked")
        Map<String, Object> actualData = (Map<String, Object>) data.get("data");

        // 메타데이터에서 버전 정보 추출
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        String version = metadata != null ? metadata.get("version").toString() : "1";

        SecretInfo secretInfo = new SecretInfo("KV", kvPath, actualData);
        secretInfo.setVersion(version);

        // 캐시 업데이트
        cachedKvSecret = secretInfo;
        lastKvRefresh = currentTime;

        logger.info("✅ KV 시크릿 조회 성공 (버전: {}) - 캐시 업데이트", version);
        logger.info("📦 KV Secret Data (version: {}): {}", version, actualData);

        return secretInfo;
      } else {
        throw new RuntimeException("KV 시크릿을 찾을 수 없습니다");
      }

    } catch (Exception e) {
      logger.error("❌ KV 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("KV", VaultConfig.getDatabaseKvPath(), e.getMessage());
    }
  }

  /**
   * Database Dynamic 시크릿 조회
   */
  public SecretInfo getDatabaseDynamicSecret() {
    try {
      logger.info("=== Database Dynamic Secret Refresh ===");

      String role = VaultConfig.getDatabaseDynamicRole();
      Map<String, Object> response = vaultClient.getDatabaseDynamicSecret(role);

      if (response != null && response.containsKey("data")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        String path = VaultConfig.getDatabasePath() + "/creds/" + role;
        SecretInfo secretInfo = new SecretInfo("Database Dynamic", path, data);

        // Lease 정보 설정
        if (response.containsKey("lease_id")) {
          secretInfo.setLeaseId(response.get("lease_id").toString());
        }
        if (response.containsKey("lease_duration")) {
          secretInfo.setTtl(Long.valueOf(response.get("lease_duration").toString()));
        }
        if (response.containsKey("renewable")) {
          secretInfo.setRenewable(Boolean.valueOf(response.get("renewable").toString()));
        }

        logger.info("✅ Database Dynamic 시크릿 조회 성공 (TTL: {}초)", secretInfo.getTtl());
        logger.info("🗄️ Database Dynamic Secret (TTL: {}초): username: {}, password: {}",
            secretInfo.getTtl(), data.get("username"), "***");

        return secretInfo;
      } else {
        throw new RuntimeException("Database Dynamic 시크릿을 찾을 수 없습니다");
      }

    } catch (Exception e) {
      logger.error("❌ Database Dynamic 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("Database Dynamic",
          VaultConfig.getDatabasePath() + "/creds/" + VaultConfig.getDatabaseDynamicRole(), e.getMessage());
    }
  }

  /**
   * Database Static 시크릿 조회 (캐싱 적용)
   */
  public SecretInfo getDatabaseStaticSecret() {
    long currentTime = System.currentTimeMillis();

    // 캐시가 유효한 경우 캐시된 값 반환
    if (cachedDbStaticSecret != null && (currentTime - lastDbStaticRefresh) < CACHE_VALIDITY_MS) {
      logger.info("🔒 Database Static Secret 캐시에서 반환 (마지막 갱신: {}초 전)",
          (currentTime - lastDbStaticRefresh) / 1000);
      return cachedDbStaticSecret;
    }

    try {
      logger.info("=== Database Static Secret Refresh (Vault에서 조회) ===");

      String role = VaultConfig.getDatabaseStaticRole();
      Map<String, Object> response = vaultClient.getDatabaseStaticSecret(role);

      if (response != null && response.containsKey("data")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        String path = VaultConfig.getDatabasePath() + "/static-creds/" + role;
        SecretInfo secretInfo = new SecretInfo("Database Static", path, data);
        secretInfo.setTtl(3600L); // Static secret은 TTL이 길거나 고정
        secretInfo.setRenewable(false); // Static secret은 일반적으로 갱신되지 않음

        // 캐시 업데이트
        cachedDbStaticSecret = secretInfo;
        lastDbStaticRefresh = currentTime;

        logger.info("✅ Database Static 시크릿 조회 성공 (TTL: {}초) - 캐시 업데이트", secretInfo.getTtl());
        logger.info("🔒 Database Static Secret (TTL: {}초): username: {}, password: {}",
            secretInfo.getTtl(), data.get("username"), "***");

        return secretInfo;
      } else {
        throw new RuntimeException("Database Static 시크릿을 찾을 수 없습니다");
      }

    } catch (Exception e) {
      logger.error("❌ Database Static 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("Database Static",
          VaultConfig.getDatabasePath() + "/static-creds/" + VaultConfig.getDatabaseStaticRole(), e.getMessage());
    }
  }

  /**
   * 모든 시크릿 정보 조회 (Database Dynamic Secret 제외)
   */
  public Map<String, SecretInfo> getAllSecrets() {
    Map<String, SecretInfo> secrets = new HashMap<>();

    // 현재 Database 자격증명 소스 확인
    String credentialSource = VaultConfig.getDatabaseCredentialSource();

    // KV Secret은 항상 조회 (Database 자격증명과 별개)
    secrets.put("kv", getKvSecret());

    // Database Static Secret은 static 모드일 때만 조회
    if ("static".equals(credentialSource)) {
      secrets.put("dbStatic", getDatabaseStaticSecret());
    }

    // dbDynamic은 DatabaseConfig에서 관리하므로 여기서는 조회하지 않음
    return secrets;
  }

  /**
   * 표시용 시크릿 정보 조회 (Database Dynamic Secret 제외)
   */
  public Map<String, SecretInfo> getDisplaySecrets() {
    return getAllSecrets();
  }

  /**
   * 현재 사용 중인 Database Dynamic Secret 정보 조회 (새로 발급하지 않음)
   */
  public SecretInfo getCurrentDatabaseDynamicSecret() {
    return com.example.vaulttomcat.config.DatabaseConfig.getCurrentCredentialInfo();
  }

  /**
   * VaultClient 인스턴스 반환
   */
  public VaultClient getVaultClient() {
    return vaultClient;
  }

  /**
   * 시크릿 변경 감지 및 로깅
   */
  public void logSecretChanges() {
    logger.info("🔄 시크릿 변경 감지 및 로깅 (구현 예정)");
  }

  /**
   * 리소스 정리
   */
  public void close() {
    if (vaultClient != null) {
      vaultClient.close();
    }
  }

  private SecretInfo createErrorSecretInfo(String type, String path, String errorMessage) {
    Map<String, Object> errorData = new HashMap<>();
    errorData.put("error", errorMessage);
    return new SecretInfo(type, path, errorData);
  }
}
