package com.example.vaultweb.service;

import com.example.vaultweb.model.SecretInfo;
import com.example.vaultweb.config.VaultConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Vault 시크릿 서비스 클래스
 * Spring Cloud Vault Config를 통해 시크릿을 조회하고 관리합니다.
 */
@Service
@RefreshScope
public class VaultSecretService {

  private static final Logger logger = LoggerFactory.getLogger(VaultSecretService.class);

  private final VaultTemplate vaultTemplate;

  @Autowired
  private VaultConfig vaultConfig;

  // KV 시크릿 (Spring Cloud Vault Config에서 자동 주입)
  @Value("${vault.kv.api_key:}")
  private String kvApiKey;

  @Value("${vault.kv.database_url:}")
  private String kvDatabaseUrl;

  // Database Dynamic 시크릿 (Spring Cloud Vault Config에서 자동 주입)
  @Value("${spring.datasource.username:}")
  private String dbDynamicUsername;

  @Value("${spring.datasource.password:}")
  private String dbDynamicPassword;

  public VaultSecretService(VaultTemplate vaultTemplate) {
    this.vaultTemplate = vaultTemplate;
  }

  /**
   * KV 시크릿 조회
   */
  public SecretInfo getKvSecret() {
    try {
      logger.info("=== KV Secret Refresh ===");

      // Vault에서 직접 KV 데이터 조회
      VaultResponse response = vaultTemplate.read("my-vault-app-kv/data/database");
      Map<String, Object> kvData = new HashMap<>();

      if (response != null && response.getData() != null) {
        // KV v2에서 실제 데이터는 'data' 키 안에 있음
        Object dataObj = response.getData().get("data");
        if (dataObj instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> actualData = (Map<String, Object>) dataObj;
          kvData.putAll(actualData);
        } else {
          kvData.putAll(response.getData());
        }

        String version = "1";
        if (response.getMetadata() != null && response.getMetadata().get("version") != null) {
          version = response.getMetadata().get("version").toString();
        }
        logger.info("✅ KV 시크릿 조회 성공 (버전: {})", version);
        logger.info("📦 KV Secret Data (version: {}): {}", version, kvData);

        SecretInfo secretInfo = new SecretInfo("KV", "my-vault-app-kv/data/database", kvData);
        secretInfo.setVersion(version);
        return secretInfo;
      } else {
        // Spring Cloud Vault Config에서 주입된 값 사용
        kvData.put("api_key", kvApiKey != null ? kvApiKey : "");
        kvData.put("database_url", kvDatabaseUrl != null ? kvDatabaseUrl : "");
        logger.info("📦 KV Secret Data (from config): {}", kvData);

        SecretInfo secretInfo = new SecretInfo("KV", "my-vault-app-kv/data/database", kvData);
        secretInfo.setVersion("1");
        return secretInfo;
      }

    } catch (Exception e) {
      logger.error("❌ KV 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("KV", "my-vault-app-kv/data/database", e.getMessage());
    }
  }

  /**
   * Database Dynamic 시크릿 조회
   */
  public SecretInfo getDatabaseDynamicSecret() {
    try {
      logger.info("=== Database Dynamic Secret Refresh ===");

      // Vault API 직접 호출로 최신 credential 조회
      VaultResponse response = vaultTemplate.read("my-vault-app-database/creds/db-demo-dynamic");

      if (response != null && response.getData() != null) {
        Map<String, Object> dbData = new HashMap<>();
        dbData.put("username", response.getData().get("username"));
        dbData.put("password", response.getData().get("password"));

        SecretInfo secretInfo = new SecretInfo("Database Dynamic",
            "my-vault-app-database/creds/db-demo-dynamic", dbData);

        // Lease 정보 설정
        if (response.getLeaseId() != null) {
          secretInfo.setLeaseId(response.getLeaseId());
        }
        if (response.getLeaseDuration() > 0) {
          secretInfo.setTtl(response.getLeaseDuration());
        }
        secretInfo.setRenewable(response.isRenewable());

        logger.info("✅ Database Dynamic 시크릿 조회 성공 (TTL: {}초)", secretInfo.getTtl());
        logger.info("🗄️ Database Dynamic Secret (TTL: {}초): username: {}, password: {}",
            secretInfo.getTtl(), response.getData().get("username"), "***");

        return secretInfo;
      } else {
        throw new RuntimeException("Database Dynamic 시크릿을 찾을 수 없습니다");
      }

    } catch (Exception e) {
      logger.error("❌ Database Dynamic 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("Database Dynamic",
          "my-vault-app-database/creds/db-demo-dynamic", e.getMessage());
    }
  }

  /**
   * Database Dynamic 시크릿 조회 (Vault API 직접 호출)
   */
  public VaultResponse getDatabaseCredentials(String roleName) {
    try {
      logger.info("=== Database Dynamic Secret API Call ===");

      String path = "my-vault-app-database/creds/" + roleName;
      VaultResponse response = vaultTemplate.read(path);

      if (response != null && response.getData() != null) {
        logger.info("✅ Database Dynamic 시크릿 API 호출 성공: {}", path);
        return response;
      } else {
        throw new RuntimeException("Database Dynamic 시크릿을 찾을 수 없습니다: " + path);
      }

    } catch (Exception e) {
      logger.error("❌ Database Dynamic 시크릿 API 호출 실패: {}", e.getMessage());
      throw new RuntimeException("Database Dynamic 시크릿 조회 실패: " + e.getMessage());
    }
  }

  /**
   * Database Static 시크릿 조회
   */
  public SecretInfo getDatabaseStaticSecret() {
    try {
      logger.info("=== Database Static Secret Refresh ===");

      // Vault API를 직접 호출하여 Static 시크릿 조회
      VaultResponse response = vaultTemplate.read("my-vault-app-database/static-creds/db-demo-static");

      if (response != null && response.getData() != null) {
        Map<String, Object> dbData = (Map<String, Object>) response.getData();

        SecretInfo secretInfo = new SecretInfo("Database Static",
            "my-vault-app-database/static-creds/db-demo-static", dbData);
        secretInfo.setTtl(3600L);
        secretInfo.setRenewable(false);

        logger.info("✅ Database Static 시크릿 조회 성공 (TTL: {}초)", secretInfo.getTtl());
        logger.info("🔒 Database Static Secret (TTL: {}초): {}",
            secretInfo.getTtl(), dbData);

        return secretInfo;
      } else {
        throw new RuntimeException("Database Static 시크릿을 찾을 수 없습니다.");
      }

    } catch (Exception e) {
      logger.error("❌ Database Static 시크릿 조회 실패: {}", e.getMessage());
      return createErrorSecretInfo("Database Static",
          "my-vault-app-database/static-creds/db-demo-static", e.getMessage());
    }
  }

  /**
   * 모든 시크릿 정보 조회
   */
  public Map<String, SecretInfo> getAllSecrets() {
    Map<String, SecretInfo> secrets = new HashMap<>();

    // 자격증명 소스 확인
    String credentialSource = vaultConfig.getDatabase() != null
        ? vaultConfig.getDatabase().getCredentialSource()
        : "dynamic";

    logger.info("🔍 현재 자격증명 소스: {}", credentialSource);

    // KV Secret은 항상 조회 (Database 자격증명과 별개)
    secrets.put("kv", getKvSecret());

    // Database 자격증명은 설정된 소스에 따라 조회
    switch (credentialSource.toLowerCase()) {
      case "kv":
        // KV Secret에서 Database 자격증명 사용 (별도 표시 안 함)
        logger.info("📦 Database 자격증명: KV Secret 사용");
        break;
      case "static":
        secrets.put("database_static", getDatabaseStaticSecret());
        break;
      case "dynamic":
      default:
        secrets.put("database_dynamic", getDatabaseDynamicSecret());
        break;
    }

    return secrets;
  }

  /**
   * 에러 시크릿 정보 생성
   */
  private SecretInfo createErrorSecretInfo(String type, String path, String errorMessage) {
    SecretInfo secretInfo = new SecretInfo(type, path, new HashMap<>());
    secretInfo.getData().put("error", errorMessage);
    secretInfo.getData().put("status", "failed");
    return secretInfo;
  }

  /**
   * 시크릿 변경 감지 및 로깅
   */
  public void logSecretChanges() {
    logger.info("🔄 시크릿 갱신 시작... (자동 갱신 활성화)");

    // 모든 시크릿 조회하여 변경사항 감지
    getAllSecrets().forEach((key, secretInfo) -> {
      logger.info("📊 {} 시크릿 상태: {}", key, secretInfo.getType());
    });
  }
}
