package com.example.vaulttomcat.config;

import com.example.vaulttomcat.client.VaultClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token Renewal 스케줄러
 * AppRole로 받은 orphan token의 자동 갱신을 담당합니다.
 */
public class TokenRenewalScheduler {
  private static final Logger logger = LoggerFactory.getLogger(TokenRenewalScheduler.class);
  private static ScheduledExecutorService scheduler;
  private static VaultClient vaultClient;

  /**
   * Token Renewal 스케줄러 시작
   */
  public static void start(VaultClient client) {
    if (!VaultConfig.isTokenRenewalEnabled()) {
      logger.info("Token renewal이 비활성화되어 있습니다");
      return;
    }

    vaultClient = client;
    scheduler = Executors.newSingleThreadScheduledExecutor();

    // 10초마다 token 갱신 필요 여부 확인
    scheduler.scheduleAtFixedRate(() -> {
      try {
        if (vaultClient.shouldRenew()) {
          logger.info("Token 갱신 필요 - 갱신 시도 중...");
          boolean success = vaultClient.renewToken();

          if (success) {
            logger.info("✅ Token 갱신 성공 - TTL: {}초",
                (vaultClient.getTokenExpiry() - System.currentTimeMillis()) / 1000);
          } else {
            logger.error("❌ Token 갱신 실패 - 애플리케이션 종료");
            shutdown();
            System.exit(1);
          }
        }
      } catch (Exception e) {
        logger.error("Token 갱신 스케줄러 오류: {}", e.getMessage());
      }
    }, 10, 10, TimeUnit.SECONDS);

    logger.info("Token Renewal 스케줄러 시작 (10초 간격 체크)");
  }

  /**
   * Token Renewal 스케줄러 종료
   */
  public static void shutdown() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      logger.info("Token Renewal 스케줄러 종료");
    }
  }
}
