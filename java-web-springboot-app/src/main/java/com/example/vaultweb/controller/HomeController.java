package com.example.vaultweb.controller;

import com.example.vaultweb.model.SecretInfo;
import com.example.vaultweb.service.DatabaseService;
import com.example.vaultweb.service.VaultSecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메인 컨트롤러 클래스
 * 웹 페이지 요청을 처리하고 Vault 시크릿 정보를 화면에 표시합니다.
 */
@Controller
@RequestMapping("/")
public class HomeController {

  private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

  @Autowired
  private VaultSecretService vaultSecretService;

  @Autowired
  private DatabaseService databaseService;

  /**
   * 메인 페이지 (index.jsp)
   */
  @GetMapping
  public String index(Model model) {
    logger.info("🚀 Vault Spring Boot 웹 애플리케이션 시작");

    try {
      // Vault 시크릿 정보 조회
      Map<String, SecretInfo> secrets = vaultSecretService.getAllSecrets();
      model.addAttribute("secrets", secrets);

      // Database 연결 테스트
      logger.info("🔍 Database 연결 테스트 시작");
      try {
        Map<String, Object> dbConnection = databaseService.testConnection();
        logger.info("🔍 Database 연결 테스트 완료: {}", dbConnection.get("status"));
        logger.info("🔍 Database 연결 결과 데이터: {}", dbConnection);
        model.addAttribute("dbConnection", dbConnection);
      } catch (Exception e) {
        logger.error("❌ Database 연결 테스트 중 오류 발생: {}", e.getMessage(), e);
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("status", "error");
        errorResult.put("error", e.getMessage());
        model.addAttribute("dbConnection", errorResult);
      }

      // Database 통계
      logger.info("🔍 Database 통계 조회 시작");
      Map<String, Object> dbStats = databaseService.getDatabaseStats();
      logger.info("🔍 Database 통계 조회 완료");
      model.addAttribute("dbStats", dbStats);

      logger.info("✅ 메인 페이지 데이터 로드 완료");
      logger.info("📊 로드된 데이터: {} 시크릿",
          secrets.size());

    } catch (Exception e) {
      logger.error("❌ 메인 페이지 데이터 로드 실패: {}", e.getMessage());
      model.addAttribute("error", e.getMessage());
    }

    return "index";
  }

  /**
   * 시크릿 갱신 API
   */
  @GetMapping("/refresh")
  public String refresh(Model model) {
    logger.info("🔄 시크릿 갱신 요청");

    try {
      // 시크릿 변경 감지 및 로깅
      vaultSecretService.logSecretChanges();

      // 갱신된 시크릿 정보 조회
      Map<String, SecretInfo> secrets = vaultSecretService.getAllSecrets();
      model.addAttribute("secrets", secrets);
      model.addAttribute("refreshTime", java.time.LocalDateTime.now());

      // Database 연결 테스트 추가
      logger.info("🔍 Database 연결 테스트 시작");
      try {
        Map<String, Object> dbConnection = databaseService.testConnection();
        logger.info("🔍 Database 연결 테스트 완료: {}", dbConnection.get("status"));
        model.addAttribute("dbConnection", dbConnection);
      } catch (Exception e) {
        logger.error("❌ Database 연결 테스트 중 오류 발생: {}", e.getMessage(), e);
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("status", "error");
        errorResult.put("error", e.getMessage());
        model.addAttribute("dbConnection", errorResult);
      }

      // Database 통계 추가
      logger.info("🔍 Database 통계 조회 시작");
      Map<String, Object> dbStats = databaseService.getDatabaseStats();
      logger.info("🔍 Database 통계 조회 완료");
      model.addAttribute("dbStats", dbStats);

      logger.info("✅ 시크릿 갱신 완료");

    } catch (Exception e) {
      logger.error("❌ 시크릿 갱신 실패: {}", e.getMessage());
      model.addAttribute("error", e.getMessage());
    }

    return "index";
  }

  /**
   * Database 정보 API
   */
  @GetMapping("/database")
  public String database(Model model) {
    logger.info("🗄️ Database 정보 조회 요청");

    try {
      // Database 연결 테스트
      Map<String, Object> dbConnection = databaseService.testConnection();
      model.addAttribute("dbConnection", dbConnection);

      // Database 테이블 정보
      List<Map<String, Object>> tables = databaseService.getTables();
      model.addAttribute("tables", tables);

      // Database 통계
      Map<String, Object> dbStats = databaseService.getDatabaseStats();
      model.addAttribute("dbStats", dbStats);

      logger.info("✅ Database 정보 조회 완료");

    } catch (Exception e) {
      logger.error("❌ Database 정보 조회 실패: {}", e.getMessage());
      model.addAttribute("error", e.getMessage());
    }

    return "database";
  }

  /**
   * Health Check API
   */
  @GetMapping("/health")
  public String health(Model model) {
    logger.info("🏥 Health Check 요청");

    Map<String, Object> health = new java.util.HashMap<>();
    health.put("status", "UP");
    health.put("timestamp", java.time.LocalDateTime.now());
    health.put("application", "Vault Spring Boot Web App");

    try {
      // Vault 연결 상태 확인
      Map<String, SecretInfo> secrets = vaultSecretService.getAllSecrets();
      health.put("vault_status", "CONNECTED");
      health.put("secrets_count", secrets.size());

      // Database 연결 상태 확인
      Map<String, Object> dbConnection = databaseService.testConnection();
      health.put("database_status", dbConnection.get("status"));

      logger.info("✅ Health Check 완료: Vault={}, Database={}",
          health.get("vault_status"), health.get("database_status"));

    } catch (Exception e) {
      logger.error("❌ Health Check 실패: {}", e.getMessage());
      health.put("status", "DOWN");
      health.put("error", e.getMessage());
    }

    model.addAttribute("health", health);
    return "health";
  }
}
