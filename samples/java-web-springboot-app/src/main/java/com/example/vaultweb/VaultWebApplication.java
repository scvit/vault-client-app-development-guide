package com.example.vaultweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot Vault 웹 애플리케이션 메인 클래스
 * 
 * 주요 기능:
 * - Spring Cloud Vault Config를 통한 시크릿 관리
 * - AppRole 인증
 * - 자동 토큰 갱신
 * - KV, Database Dynamic/Static 시크릿 조회
 * - 웹 UI 제공
 */
@SpringBootApplication
@RefreshScope
@EnableScheduling
public class VaultWebApplication {

  public static void main(String[] args) {
    System.setProperty("spring.profiles.active", "dev");
    SpringApplication.run(VaultWebApplication.class, args);
  }
}
