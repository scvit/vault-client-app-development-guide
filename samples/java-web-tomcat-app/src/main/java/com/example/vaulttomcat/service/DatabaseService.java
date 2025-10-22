package com.example.vaulttomcat.service;

import com.example.vaulttomcat.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Database 서비스 클래스
 * Vault에서 받은 자격증명으로 Database 연결 및 통계 정보 조회
 */
public class DatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

  /**
   * Database 연결 테스트
   */
  public Map<String, Object> testConnection() {
    logger.info("=== Database Connection Test Started ===");
    Map<String, Object> result = new HashMap<>();

    try (Connection connection = DatabaseConfig.getConnection()) {
      logger.info("Connection obtained successfully");

      DatabaseMetaData metaData = connection.getMetaData();

      result.put("status", "success");
      result.put("database_product", metaData.getDatabaseProductName());
      result.put("database_version", metaData.getDatabaseProductVersion());
      result.put("driver_name", metaData.getDriverName());
      result.put("driver_version", metaData.getDriverVersion());
      result.put("url", metaData.getURL());
      result.put("username", metaData.getUserName());

      logger.info("✅ Database 연결 성공");
      logger.info("🗄️ Database Info: {} {}",
          metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion());

      // 결과 데이터 로깅
      logger.info("📊 Database Connection Result: {}", result);

    } catch (SQLException e) {
      logger.error("❌ Database 연결 실패: {}", e.getMessage());
      result.put("status", "error");
      result.put("error", e.getMessage());
    } catch (Exception e) {
      logger.error("❌ Database 연결 중 예상치 못한 오류: {}", e.getMessage());
      result.put("status", "error");
      result.put("error", "Unexpected error: " + e.getMessage());
    }

    logger.info("=== Database Connection Test Completed ===");
    return result;
  }

  /**
   * Database 통계 정보 조회
   */
  public Map<String, Object> getDatabaseStats() {
    logger.info("=== Database Stats Query ===");
    Map<String, Object> stats = new HashMap<>();

    try (Connection connection = DatabaseConfig.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();

      // Connection Pool 상태
      Map<String, Object> poolStatus = DatabaseConfig.getPoolStatus();
      stats.putAll(poolStatus);

      // Database 제한 정보
      stats.put("max_connections", metaData.getMaxConnections());
      stats.put("max_columns_in_table", metaData.getMaxColumnsInTable());
      stats.put("max_columns_in_index", metaData.getMaxColumnsInIndex());
      stats.put("max_columns_in_select", metaData.getMaxColumnsInSelect());

      // 테이블 수 조회
      try (ResultSet tables = metaData.getTables(null, null, "%", new String[] { "TABLE" })) {
        int tableCount = 0;
        while (tables.next()) {
          tableCount++;
        }
        stats.put("table_count", tableCount);
      }

      logger.info("✅ Database 통계 조회 성공");
      logger.info("📊 Database Stats: {}", stats);

    } catch (SQLException e) {
      logger.error("❌ Database 통계 조회 실패: {}", e.getMessage());
      stats.put("error", e.getMessage());
    }

    return stats;
  }

  /**
   * 테이블 목록 조회
   */
  public Map<String, Object> getTables() {
    logger.info("=== Database Tables Query ===");
    Map<String, Object> result = new HashMap<>();

    try (Connection connection = DatabaseConfig.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();

      try (ResultSet tables = metaData.getTables(null, null, "%", new String[] { "TABLE" })) {
        int tableCount = 0;
        while (tables.next()) {
          tableCount++;
        }
        result.put("table_count", tableCount);
        result.put("status", "success");
      }

      logger.info("✅ Database 테이블 조회 성공");

    } catch (SQLException e) {
      logger.error("❌ Database 테이블 조회 실패: {}", e.getMessage());
      result.put("status", "error");
      result.put("error", e.getMessage());
    }

    return result;
  }
}
