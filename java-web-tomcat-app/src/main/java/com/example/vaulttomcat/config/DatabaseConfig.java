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
    logger.info("=== 🚀 Database Connection Pool 초기화 시작 ===");

    try {
      // 1. Vault에서 Dynamic Secret 조회
      logger.info("📡 Vault에서 초기 Database Dynamic Secret 조회 중...");
      SecretInfo dbSecret = vaultSecretService.getDatabaseDynamicSecret();

      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) dbSecret.getData();
      String username = (String) data.get("username");
      logger.info("✅ 초기 Database Secret 조회 완료 - Username: {}", username);
      logger.info("⏰ 초기 TTL: {}초", dbSecret.getTtl());

      // 2. Connection Pool 생성
      logger.info("🔧 초기 Database Connection Pool 생성 중...");
      createDataSource(dbSecret);

      // 3. TTL의 80% 시점에 자격증명 갱신 스케줄링
      if (dbSecret.getTtl() != null && dbSecret.getTtl() > 0) {
        long refreshDelay = (long) (dbSecret.getTtl() * 0.8 * 1000); // 80% 시점 (밀리초)
        logger.info("⏰ 자동 갱신 스케줄링 - {}초 후 (TTL의 80% 시점)", refreshDelay / 1000);
        scheduleCredentialRefresh(refreshDelay);
      }

      logger.info("🎉 Database Connection Pool 초기화 완료");

    } catch (Exception e) {
      logger.error("❌ Database Connection Pool 초기화 실패: {}", e.getMessage(), e);
      throw new RuntimeException("Database Connection Pool 초기화 실패", e);
    }
  }

  /**
   * Connection Pool 생성
   */
  private static void createDataSource(SecretInfo dbSecret) {
    logger.info("🔧 Database Connection Pool 생성 시작...");

    dataSource = new BasicDataSource();

    // Database 연결 설정
    String url = "jdbc:mysql://127.0.0.1:3306/mydb";
    String driverClass = "com.mysql.cj.jdbc.Driver";
    dataSource.setUrl(url);
    dataSource.setDriverClassName(driverClass);
    logger.info("🗄️ Database URL: {}", url);
    logger.info("🔌 Database Driver: {}", driverClass);

    // Vault에서 받은 자격증명 설정
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) dbSecret.getData();
    String username = (String) data.get("username");
    String password = (String) data.get("password");
    dataSource.setUsername(username);
    dataSource.setPassword(password);
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
    currentCredentialInfo = dbSecret;

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
   * 자격증명 갱신
   */
  private static void refreshCredentials() {
    logger.info("=== 🔄 Database Secret 자격증명 갱신 시작 ===");

    try {
      // 1. 새로운 Dynamic Secret 조회
      logger.info("📡 Vault에서 새로운 Database Dynamic Secret 조회 중...");
      SecretInfo newSecret = vaultSecretService.getDatabaseDynamicSecret();

      @SuppressWarnings("unchecked")
      Map<String, Object> newData = (Map<String, Object>) newSecret.getData();
      String newUsername = (String) newData.get("username");
      String newPassword = (String) newData.get("password");

      logger.info("✅ 새로운 Database Secret 발급 완료");
      logger.info("🔑 새로운 자격증명 - Username: {}, Password: ***", newUsername);
      logger.info("⏰ 새로운 TTL: {}초", newSecret.getTtl());

      // 2. 기존 Connection Pool 종료
      logger.info("🔄 기존 Database Connection Pool 종료 중...");
      closeDataSource();
      logger.info("✅ 기존 Database Connection Pool 종료 완료");

      // 3. 새로운 Connection Pool 생성
      logger.info("🆕 새로운 Database Connection Pool 생성 중...");
      createDataSource(newSecret);
      logger.info("✅ 새로운 Database Connection Pool 생성 완료");

      // 4. 다음 갱신 스케줄링
      if (newSecret.getTtl() != null && newSecret.getTtl() > 0) {
        long refreshDelay = (long) (newSecret.getTtl() * 0.8 * 1000);
        logger.info("⏰ 다음 자격증명 갱신 예정: {}초 후 (TTL의 80% 시점)", refreshDelay / 1000);
        scheduleCredentialRefresh(refreshDelay);
      }

      logger.info("🎉 Database Secret 자격증명 갱신 및 Connection Pool 재생성 완료");

    } catch (Exception e) {
      logger.error("❌ Database Secret 자격증명 갱신 실패: {}", e.getMessage(), e);
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
