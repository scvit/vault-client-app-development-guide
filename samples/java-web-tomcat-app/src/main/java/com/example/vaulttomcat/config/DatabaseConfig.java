package com.example.vaulttomcat.config;

import com.example.vaulttomcat.model.SecretInfo;
import com.example.vaulttomcat.service.VaultSecretService;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Database Connection Pool 설정 관리 클래스
 */
public class DatabaseConfig {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

  private static BasicDataSource dataSource;
  private static ScheduledExecutorService scheduler;
  private static VaultSecretService vaultSecretService;
  private static SecretInfo currentCredentialInfo;

  /**
   * Database Connection Pool 초기화
   */
  public static void initialize(VaultSecretService vaultService) {
    vaultSecretService = vaultService;
    String credentialSource = VaultConfig.getDatabaseCredentialSource();

    logger.info("=== Database Connection Pool 초기화 (자격증명 소스: {}) ===", credentialSource);

    try {
      switch (credentialSource.toLowerCase()) {
        case "kv":
          initializeWithKv();
          break;
        case "dynamic":
          initializeWithDynamic();
          break;
        case "static":
          initializeWithStatic();
          break;
        default:
          throw new IllegalArgumentException("지원하지 않는 자격증명 소스: " + credentialSource);
      }

      logger.info("🎉 Database Connection Pool 초기화 완료");

    } catch (Exception e) {
      logger.error("❌ Database Connection Pool 초기화 실패: {}", e.getMessage(), e);
      throw new RuntimeException("Database Connection Pool 초기화 실패", e);
    }
  }

  /**
   * KV 기반 초기화
   */
  private static void initializeWithKv() {
    logger.info("📦 KV 기반 Database 자격증명 초기화");

    // KV에서 자격증명 조회
    SecretInfo kvSecret = vaultSecretService.getKvSecret();
    Map<String, Object> data = (Map<String, Object>) kvSecret.getData();

    String username = (String) data.get("database_username");
    String password = (String) data.get("database_password");

    // Connection Pool 생성
    createDataSource(username, password, kvSecret);

    // KV 버전 변경 감지 스케줄링
    int refreshInterval = VaultConfig.getDatabaseKvRefreshInterval();
    scheduleKvVersionCheck(refreshInterval, kvSecret.getVersion());
  }

  /**
   * Dynamic 기반 초기화
   */
  private static void initializeWithDynamic() {
    logger.info("🔄 Database Dynamic Secret 기반 자격증명 초기화");

    // Dynamic Secret 조회
    SecretInfo dbSecret = vaultSecretService.getDatabaseDynamicSecret();
    Map<String, Object> data = (Map<String, Object>) dbSecret.getData();

    String username = (String) data.get("username");
    String password = (String) data.get("password");

    // Connection Pool 생성
    createDataSource(username, password, dbSecret);

    // TTL 기반 갱신 스케줄링
    if (dbSecret.getTtl() != null && dbSecret.getTtl() > 0) {
      long refreshDelay = (long) (dbSecret.getTtl() * 0.8 * 1000);
      scheduleCredentialRefresh(refreshDelay);
    }
  }

  /**
   * Static 기반 초기화
   */
  private static void initializeWithStatic() {
    logger.info("🔒 Database Static Secret 기반 자격증명 초기화");

    // Static Secret 조회
    SecretInfo staticSecret = vaultSecretService.getDatabaseStaticSecret();
    Map<String, Object> data = (Map<String, Object>) staticSecret.getData();

    String username = (String) data.get("username");
    String password = (String) data.get("password");

    // Connection Pool 생성
    createDataSource(username, password, staticSecret);

    // Static은 주기적 갱신 불필요 (Vault에서 자동 rotate)
    logger.info("ℹ️ Static Secret은 Vault에서 자동으로 rotate됩니다");
  }

  /**
   * Connection Pool 생성
   */
  private static void createDataSource(String username, String password, SecretInfo secretInfo) {
    logger.info("🔧 Database Connection Pool 생성 시작...");

    dataSource = new BasicDataSource();

    // Database 연결 설정
    String url = VaultConfig.getDatabaseUrl();
    String driverClass = VaultConfig.getDatabaseDriver();

    dataSource.setUrl(url);
    dataSource.setDriverClassName(driverClass);
    dataSource.setUsername(username);
    dataSource.setPassword(password);

    logger.info("🗄️ Database URL: {}", url);
    logger.info("🔌 Database Driver: {}", driverClass);
    logger.info("🔑 Database 자격증명 설정 - Username: {}, Password: ***", username);

    // Connection Pool 설정
    dataSource.setInitialSize(5);
    dataSource.setMaxTotal(20);
    dataSource.setMaxIdle(10);
    dataSource.setMinIdle(5);
    dataSource.setMaxWaitMillis(30000);
    logger.info("📊 Connection Pool 설정 - Initial: 5, MaxTotal: 20, MaxIdle: 10, MinIdle: 5");

    // Connection 유효성 검사
    dataSource.setValidationQuery("SELECT 1");
    dataSource.setTestOnBorrow(true);
    dataSource.setTestWhileIdle(true);
    dataSource.setTimeBetweenEvictionRunsMillis(30000);
    logger.info("🔍 Connection 유효성 검사 설정 완료");

    // Connection Pool 테스트
    try {
      Connection testConnection = dataSource.getConnection();
      logger.info("✅ Database Connection 테스트 성공");
      testConnection.close();
      logger.info("🔌 Database Connection 테스트 연결 종료");
    } catch (SQLException e) {
      logger.error("❌ Database Connection 테스트 실패: {}", e.getMessage());
    }

    // 현재 credential 정보 저장
    currentCredentialInfo = secretInfo;

    logger.info("🎉 Database Connection Pool 생성 완료 - Username: {}", username);
  }

  /**
   * 자격증명 갱신 스케줄링
   */
  private static void scheduleCredentialRefresh(long delayMs) {
    logger.info("⏰ Database Secret 자격증명 갱신 스케줄링 - {}초 후 실행", delayMs / 1000);

    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "vault-credential-refresh");
      t.setDaemon(true);
      return t;
    });

    scheduler.schedule(() -> {
      logger.info("🔔 스케줄된 Database Secret 갱신 시간 도달 - 갱신 시작");
      try {
        refreshCredentials();
      } catch (Exception e) {
        logger.error("❌ 스케줄된 자격증명 갱신 실패: {}", e.getMessage(), e);
      }
    }, delayMs, TimeUnit.MILLISECONDS);

    logger.info("✅ Database Secret 갱신 스케줄링 완료");
  }

  /**
   * KV 버전 체크 스케줄러
   */
  private static void scheduleKvVersionCheck(int intervalSeconds, String currentVersion) {
    logger.info("⏰ KV 버전 체크 스케줄링 - {}초마다 실행", intervalSeconds);

    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "vault-kv-version-check");
      t.setDaemon(true);
      return t;
    });

    scheduler.scheduleAtFixedRate(() -> {
      try {
        SecretInfo newKvSecret = vaultSecretService.getKvSecret();
        String newVersion = newKvSecret.getVersion();

        if (!currentVersion.equals(newVersion)) {
          logger.info("🔔 KV 버전 변경 감지 ({}→{}) - Connection Pool 재생성",
              currentVersion, newVersion);
          refreshCredentialsFromKv(newKvSecret);
        } else {
          logger.debug("✅ KV 버전 변경 없음 (version: {})", currentVersion);
        }
      } catch (Exception e) {
        logger.error("❌ KV 버전 체크 실패: {}", e.getMessage(), e);
      }
    }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
  }

  /**
   * KV 자격증명 갱신
   */
  private static void refreshCredentialsFromKv(SecretInfo newKvSecret) {
    logger.info("=== 🔄 KV 자격증명 갱신 시작 ===");

    try {
      Map<String, Object> data = (Map<String, Object>) newKvSecret.getData();
      String username = (String) data.get("database_username");
      String password = (String) data.get("database_password");

      logger.info("✅ 새로운 KV 자격증명 조회 완료 - Username: {}", username);

      // Connection Pool 재생성
      closeDataSource();
      createDataSource(username, password, newKvSecret);

      // 다음 버전 체크 스케줄링
      int refreshInterval = VaultConfig.getDatabaseKvRefreshInterval();
      scheduleKvVersionCheck(refreshInterval, newKvSecret.getVersion());

      logger.info("🎉 KV 자격증명 갱신 및 Connection Pool 재생성 완료");

    } catch (Exception e) {
      logger.error("❌ KV 자격증명 갱신 실패: {}", e.getMessage(), e);
    }
  }

  /**
   * 자격증명 갱신
   */
  private static void refreshCredentials() {
    String credentialSource = VaultConfig.getDatabaseCredentialSource();

    logger.info("=== Database 자격증명 갱신 (소스: {}) ===", credentialSource);

    try {
      SecretInfo newSecret = null;
      String username = null;
      String password = null;

      switch (credentialSource.toLowerCase()) {
        case "kv":
          // KV는 버전 체크에서 처리하므로 여기서는 호출되지 않음
          logger.warn("⚠️ KV 자격증명은 버전 체크에서 갱신됩니다");
          return;
        case "dynamic":
          newSecret = vaultSecretService.getDatabaseDynamicSecret();
          Map<String, Object> dynamicData = (Map<String, Object>) newSecret.getData();
          username = (String) dynamicData.get("username");
          password = (String) dynamicData.get("password");
          break;
        case "static":
          newSecret = vaultSecretService.getDatabaseStaticSecret();
          Map<String, Object> staticData = (Map<String, Object>) newSecret.getData();
          username = (String) staticData.get("username");
          password = (String) staticData.get("password");
          break;
      }

      // Connection Pool 재생성
      closeDataSource();
      createDataSource(username, password, newSecret);

      // Dynamic인 경우 다음 갱신 스케줄링
      if ("dynamic".equals(credentialSource) && newSecret.getTtl() != null && newSecret.getTtl() > 0) {
        long refreshDelay = (long) (newSecret.getTtl() * 0.8 * 1000);
        scheduleCredentialRefresh(refreshDelay);
      }

      logger.info("🎉 Database 자격증명 갱신 완료");

    } catch (Exception e) {
      logger.error("❌ Database 자격증명 갱신 실패: {}", e.getMessage(), e);
    }
  }

  /**
   * Connection Pool 종료
   */
  private static void closeDataSource() {
    if (dataSource != null) {
      try {
        // Connection Pool 상태 로깅
        logger.info("📊 기존 Connection Pool 상태 - Active: {}, Idle: {}",
            dataSource.getNumActive(), dataSource.getNumIdle());

        logger.info("🔄 기존 Database Connection Pool 종료 중...");
        dataSource.close();
        logger.info("✅ 기존 Database Connection Pool 종료 완료");
      } catch (SQLException e) {
        logger.error("❌ Database Connection Pool 종료 중 오류: {}", e.getMessage());
      } finally {
        dataSource = null;
        logger.info("🧹 DataSource 참조 정리 완료");
      }
    } else {
      logger.info("ℹ️ 종료할 DataSource가 없습니다");
    }
  }

  /**
   * Database 연결 획득
   */
  public static Connection getConnection() throws SQLException {
    if (dataSource == null) {
      throw new SQLException("DataSource가 초기화되지 않았습니다");
    }
    return dataSource.getConnection();
  }

  /**
   * Connection Pool 상태 확인
   */
  public static Map<String, Object> getPoolStatus() {
    Map<String, Object> status = new HashMap<>();

    if (dataSource != null) {
      status.put("initialSize", dataSource.getInitialSize());
      status.put("maxTotal", dataSource.getMaxTotal());
      status.put("maxIdle", dataSource.getMaxIdle());
      status.put("minIdle", dataSource.getMinIdle());
      status.put("numActive", dataSource.getNumActive());
      status.put("numIdle", dataSource.getNumIdle());
    } else {
      status.put("status", "not_initialized");
    }

    return status;
  }

  /**
   * 현재 사용 중인 Database Dynamic Secret 정보 조회
   */
  public static SecretInfo getCurrentCredentialInfo() {
    return currentCredentialInfo;
  }

  /**
   * 리소스 정리
   */
  public static void shutdown() {
    logger.info("=== DatabaseConfig 종료 ===");

    // Scheduler 종료
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // DataSource 종료
    closeDataSource();

    logger.info("✅ DatabaseConfig 종료 완료");
  }
}
