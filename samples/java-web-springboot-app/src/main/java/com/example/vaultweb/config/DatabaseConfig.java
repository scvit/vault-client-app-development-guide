package com.example.vaultweb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.example.vaultweb.service.VaultSecretService;

/**
 * Database 설정 관리 클래스
 * Vault에서 받은 자격증명으로 DataSource를 구성합니다.
 */
@Configuration
@Component
@RefreshScope
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseConfig {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

  @Autowired
  private VaultSecretService vaultSecretService;

  @Autowired
  private VaultConfig vaultConfig;

  private String url;
  private String driverClassName;
  private String username;
  private String password;
  private Hikari hikari;

  @Bean
  @RefreshScope
  public DataSource dataSource() {
    logger.info("=== DatabaseConfig Debug ===");
    logger.info("URL: {}", this.url);
    logger.info("Driver: {}", this.driverClassName);
    logger.info("Username: {}", this.username);
    logger.info("Password: {}", (this.password != null ? "***" : "NULL"));

    // driverClassName이 null인 경우 기본값 설정
    if (this.driverClassName == null) {
      this.driverClassName = "com.mysql.cj.jdbc.Driver";
      logger.info("Setting default driver: {}", this.driverClassName);
    }

    // URL이 null인 경우 에러 로깅 (설정 파일에서 필수)
    if (this.url == null) {
      logger.error("❌ Database URL이 설정되지 않았습니다. application.yml에서 spring.datasource.url을 설정해주세요.");
      throw new IllegalStateException("Database URL이 설정되지 않았습니다.");
    }

    // Vault 설정에서 자격증명 소스 확인
    String credentialSource = vaultConfig.getDatabase() != null
        ? vaultConfig.getDatabase().getCredentialSource()
        : "dynamic";

    logger.info("=== Database 자격증명 소스: {} ===", credentialSource);

    String finalUsername = this.username;
    String finalPassword = this.password;

    try {
      switch (credentialSource.toLowerCase()) {
        case "kv":
          // KV Secret에서 자격증명 조회
          var kvSecret = vaultSecretService.getKvSecret();
          if (kvSecret != null && kvSecret.getData() != null) {
            // 설정에서 키 이름 가져오기
            String usernameKey = vaultConfig.getDatabase() != null
                && vaultConfig.getDatabase().getKv() != null
                && vaultConfig.getDatabase().getKv().getUsernameKey() != null
                    ? vaultConfig.getDatabase().getKv().getUsernameKey()
                    : "database_username";
            String passwordKey = vaultConfig.getDatabase() != null
                && vaultConfig.getDatabase().getKv() != null
                && vaultConfig.getDatabase().getKv().getPasswordKey() != null
                    ? vaultConfig.getDatabase().getKv().getPasswordKey()
                    : "database_password";

            finalUsername = (String) kvSecret.getData().get(usernameKey);
            finalPassword = (String) kvSecret.getData().get(passwordKey);
            logger.info("✅ KV Secret 자격증명 사용: {} (키: {}/{})", finalUsername, usernameKey, passwordKey);
          }
          break;

        case "static":
          // Database Static Secret에서 자격증명 조회
          var staticSecret = vaultSecretService.getDatabaseStaticSecret();
          if (staticSecret != null && staticSecret.getData() != null) {
            finalUsername = (String) staticSecret.getData().get("username");
            finalPassword = (String) staticSecret.getData().get("password");
            logger.info("✅ Static Secret 자격증명 사용: {}", finalUsername);
          }
          break;

        case "dynamic":
        default:
          // Database Dynamic Secret에서 자격증명 조회
          String role = vaultConfig.getDatabase() != null
              && vaultConfig.getDatabase().getDynamic() != null
              && vaultConfig.getDatabase().getDynamic().getRole() != null
                  ? vaultConfig.getDatabase().getDynamic().getRole()
                  : (vaultConfig.getDatabase() != null
                      && vaultConfig.getDatabase().getRole() != null
                          ? vaultConfig.getDatabase().getRole()
                          : "db-demo-dynamic");
          var dynamicSecret = vaultSecretService.getDatabaseCredentials(role);
          if (dynamicSecret != null && dynamicSecret.getData() != null) {
            finalUsername = (String) dynamicSecret.getData().get("username");
            finalPassword = (String) dynamicSecret.getData().get("password");
            logger.info("✅ Dynamic Secret 자격증명 사용: {} (role: {})", finalUsername, role);
          }
          break;
      }
    } catch (Exception e) {
      logger.error("❌ Vault 자격증명 조회 실패, 기본값 사용: {}", e.getMessage());
    }

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(this.url);
    config.setDriverClassName(this.driverClassName != null ? this.driverClassName : "com.mysql.cj.jdbc.Driver");
    config.setUsername(finalUsername);
    config.setPassword(finalPassword);

    if (hikari != null) {
      config.setMaximumPoolSize(hikari.getMaximumPoolSize());
      config.setMinimumIdle(hikari.getMinimumIdle());
      config.setConnectionTimeout(hikari.getConnectionTimeout());
      config.setIdleTimeout(hikari.getIdleTimeout());
      config.setMaxLifetime(hikari.getMaxLifetime());
    }

    return new HikariDataSource(config);
  }

  // Getters and Setters
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  public void setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Hikari getHikari() {
    return hikari;
  }

  public void setHikari(Hikari hikari) {
    this.hikari = hikari;
  }

  // Inner Class
  public static class Hikari {
    private int maximumPoolSize;
    private int minimumIdle;
    private long connectionTimeout;
    private long idleTimeout;
    private long maxLifetime;

    // Getters and Setters
    public int getMaximumPoolSize() {
      return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
      this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
      return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
      this.minimumIdle = minimumIdle;
    }

    public long getConnectionTimeout() {
      return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
    }

    public long getIdleTimeout() {
      return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
      this.idleTimeout = idleTimeout;
    }

    public long getMaxLifetime() {
      return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime) {
      this.maxLifetime = maxLifetime;
    }
  }
}
