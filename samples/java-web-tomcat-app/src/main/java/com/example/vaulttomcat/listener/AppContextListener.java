package com.example.vaulttomcat.listener;

import com.example.vaulttomcat.client.VaultClient;
import com.example.vaulttomcat.config.DatabaseConfig;
import com.example.vaulttomcat.config.TokenRenewalScheduler;
import com.example.vaulttomcat.service.DatabaseService;
import com.example.vaulttomcat.service.VaultSecretService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애플리케이션 초기화 리스너
 * 애플리케이션 시작 시 Vault 클라이언트 및 Database Connection Pool 초기화
 */
@WebListener
public class AppContextListener implements ServletContextListener {
  private static final Logger logger = LoggerFactory.getLogger(AppContextListener.class);

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    System.out.println("=== Vault Tomcat Web Application 시작 ===");
    logger.info("=== Vault Tomcat Web Application 시작 ===");

    ServletContext context = sce.getServletContext();

    try {
      // 1. Vault 클라이언트 초기화
      System.out.println("Vault 클라이언트 초기화 중...");
      logger.info("Vault 클라이언트 초기화 중...");
      VaultSecretService vaultSecretService = new VaultSecretService();
      context.setAttribute("vaultSecretService", vaultSecretService);

      // Token Renewal 스케줄러 시작
      VaultClient vaultClient = vaultSecretService.getVaultClient();
      TokenRenewalScheduler.start(vaultClient);

      System.out.println("✅ Vault 클라이언트 초기화 완료");
      logger.info("✅ Vault 클라이언트 초기화 완료");

      // 2. Database Connection Pool 초기화
      System.out.println("Database Connection Pool 초기화 중...");
      logger.info("Database Connection Pool 초기화 중...");
      try {
        DatabaseConfig.initialize(vaultSecretService);
        System.out.println("✅ Database Connection Pool 초기화 완료");
        logger.info("✅ Database Connection Pool 초기화 완료");
      } catch (Exception e) {
        System.out.println("⚠️ Database Connection Pool 초기화 실패: " + e.getMessage());
        logger.warn("⚠️ Database Connection Pool 초기화 실패: {}", e.getMessage());
        logger.warn("Database 기능이 제한됩니다");
      }

      // 3. Database 서비스 초기화
      System.out.println("Database 서비스 초기화 중...");
      logger.info("Database 서비스 초기화 중...");
      DatabaseService databaseService = new DatabaseService();
      context.setAttribute("databaseService", databaseService);
      System.out.println("✅ Database 서비스 초기화 완료");
      logger.info("✅ Database 서비스 초기화 완료");

      System.out.println("🎉 Vault Tomcat Web Application 초기화 완료");
      logger.info("🎉 Vault Tomcat Web Application 초기화 완료");

    } catch (Exception e) {
      System.out.println("❌ 애플리케이션 초기화 실패: " + e.getMessage());
      logger.error("❌ 애플리케이션 초기화 실패: {}", e.getMessage(), e);
      // 초기화 실패해도 애플리케이션은 시작하도록 함
      System.out.println("⚠️ 애플리케이션을 제한된 모드로 시작합니다");
      logger.warn("⚠️ 애플리케이션을 제한된 모드로 시작합니다");
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    logger.info("=== Vault Tomcat Web Application 종료 ===");

    ServletContext context = sce.getServletContext();

    try {
      // 1. Token Renewal 스케줄러 종료
      TokenRenewalScheduler.shutdown();

      // 2. Vault 서비스 정리
      VaultSecretService vaultSecretService = (VaultSecretService) context.getAttribute("vaultSecretService");
      if (vaultSecretService != null) {
        vaultSecretService.close();
        logger.info("✅ Vault 서비스 정리 완료");
      }

      // 3. Database Connection Pool 정리
      DatabaseConfig.shutdown();
      logger.info("✅ Database Connection Pool 정리 완료");

      logger.info("🎉 Vault Tomcat Web Application 종료 완료");
      System.out.println("🎉 Vault Tomcat Web Application 종료 완료");

    } catch (Exception e) {
      logger.error("❌ 애플리케이션 종료 중 오류: {}", e.getMessage(), e);
    }
  }
}
