package com.example.vaulttomcat.servlet;

import com.example.vaulttomcat.model.SecretInfo;
import com.example.vaulttomcat.service.DatabaseService;
import com.example.vaulttomcat.service.VaultSecretService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 메인 페이지 Servlet
 */
public class HomeServlet extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(HomeServlet.class);

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    logger.info("🚀 Vault Tomcat 웹 애플리케이션 시작");

    try {
      // 서비스 객체 가져오기
      VaultSecretService vaultSecretService = (VaultSecretService) getServletContext()
          .getAttribute("vaultSecretService");
      DatabaseService databaseService = (DatabaseService) getServletContext().getAttribute("databaseService");

      // Vault 시크릿 정보 조회
      if (vaultSecretService != null) {
        try {
          Map<String, SecretInfo> secrets = vaultSecretService.getDisplaySecrets();

          // 현재 사용 중인 Database Dynamic Secret 정보 추가 (새로 발급하지 않음)
          SecretInfo currentDbDynamic = vaultSecretService.getCurrentDatabaseDynamicSecret();
          if (currentDbDynamic != null) {
            secrets.put("dbDynamic", currentDbDynamic);
          }

          request.setAttribute("secrets", secrets);
          logger.info("✅ Vault 시크릿 정보 로드 완료: {} 시크릿 (Database Dynamic Secret 포함)", secrets.size());
        } catch (Exception e) {
          logger.warn("⚠️ Vault 시크릿 조회 실패: {}", e.getMessage());
          request.setAttribute("vaultError", "Vault 시크릿 조회 실패: " + e.getMessage());
        }
      } else {
        logger.warn("⚠️ VaultSecretService가 초기화되지 않았습니다");
        request.setAttribute("vaultError", "Vault 서비스가 초기화되지 않았습니다");
      }

      // Database 연결 테스트
      if (databaseService != null) {
        try {
          logger.info("🔍 Database 연결 테스트 시작");
          Map<String, Object> dbConnection = databaseService.testConnection();
          logger.info("🔍 Database 연결 테스트 완료: {}", dbConnection.get("status"));
          request.setAttribute("dbConnection", dbConnection);

          // Database 통계
          logger.info("🔍 Database 통계 조회 시작");
          Map<String, Object> dbStats = databaseService.getDatabaseStats();
          logger.info("🔍 Database 통계 조회 완료");
          request.setAttribute("dbStats", dbStats);
        } catch (Exception e) {
          logger.warn("⚠️ Database 연결 테스트 실패: {}", e.getMessage());
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("status", "error");
          errorResult.put("error", e.getMessage());
          request.setAttribute("dbConnection", errorResult);
        }
      } else {
        logger.warn("⚠️ DatabaseService가 초기화되지 않았습니다");
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("status", "error");
        errorResult.put("error", "Database 서비스가 초기화되지 않았습니다");
        request.setAttribute("dbConnection", errorResult);
      }

      logger.info("✅ 메인 페이지 데이터 로드 완료");

    } catch (Exception e) {
      logger.error("❌ 메인 페이지 데이터 로드 실패: {}", e.getMessage());
      request.setAttribute("error", e.getMessage());
    }

    // JSP로 포워딩
    request.getRequestDispatcher("/WEB-INF/jsp/index.jsp").forward(request, response);
  }
}
