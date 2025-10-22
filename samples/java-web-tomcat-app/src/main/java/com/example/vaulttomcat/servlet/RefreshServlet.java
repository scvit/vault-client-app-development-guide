package com.example.vaulttomcat.servlet;

import com.example.vaulttomcat.model.SecretInfo;
import com.example.vaulttomcat.service.DatabaseService;
import com.example.vaulttomcat.service.VaultSecretService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 시크릿 갱신 Servlet
 */
public class RefreshServlet extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(RefreshServlet.class);

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    logger.info("🔄 시크릿 갱신 요청");

    try {
      // 서비스 객체 가져오기
      VaultSecretService vaultSecretService = (VaultSecretService) getServletContext()
          .getAttribute("vaultSecretService");
      DatabaseService databaseService = (DatabaseService) getServletContext().getAttribute("databaseService");

      // Vault 시크릿 정보 갱신
      if (vaultSecretService != null) {
        try {
          Map<String, SecretInfo> secrets = vaultSecretService.getAllSecrets();
          request.setAttribute("secrets", secrets);
          logger.info("✅ Vault 시크릿 정보 갱신 완료: {} 시크릿", secrets.size());
        } catch (Exception e) {
          logger.warn("⚠️ Vault 시크릿 갱신 실패: {}", e.getMessage());
          request.setAttribute("vaultError", "Vault 시크릿 갱신 실패: " + e.getMessage());
        }
      } else {
        logger.warn("⚠️ VaultSecretService가 초기화되지 않았습니다");
        request.setAttribute("vaultError", "Vault 서비스가 초기화되지 않았습니다");
      }

      // Database 연결 테스트 갱신
      if (databaseService != null) {
        try {
          logger.info("🔍 Database 연결 테스트 갱신 시작");
          Map<String, Object> dbConnection = databaseService.testConnection();
          logger.info("🔍 Database 연결 테스트 갱신 완료: {}", dbConnection.get("status"));
          request.setAttribute("dbConnection", dbConnection);

          // Database 통계 갱신
          logger.info("🔍 Database 통계 갱신 시작");
          Map<String, Object> dbStats = databaseService.getDatabaseStats();
          logger.info("🔍 Database 통계 갱신 완료");
          request.setAttribute("dbStats", dbStats);
        } catch (Exception e) {
          logger.warn("⚠️ Database 연결 테스트 갱신 실패: {}", e.getMessage());
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

      // 갱신 시간 추가
      request.setAttribute("refreshTime", new java.util.Date());
      logger.info("✅ 시크릿 갱신 완료");

    } catch (Exception e) {
      logger.error("❌ 시크릿 갱신 실패: {}", e.getMessage());
      request.setAttribute("error", e.getMessage());
    }

    // JSP로 포워딩
    request.getRequestDispatcher("/WEB-INF/jsp/index.jsp").forward(request, response);
  }
}
