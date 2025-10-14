package com.example.vaultweb.config;

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

  @Autowired
  private VaultSecretService vaultSecretService;

  private String url;
  private String driverClassName;
  private String username;
  private String password;
  private Hikari hikari;

  @Bean
  @RefreshScope
  public DataSource dataSource() {
    System.out.println("=== DatabaseConfig Debug ===");
    System.out.println("URL: " + this.url);
    System.out.println("Driver: " + this.driverClassName);
    System.out.println("Username: " + this.username);
    System.out.println("Password: " + (this.password != null ? "***" : "NULL"));
    System.out.println("=============================");

    // driverClassName이 null인 경우 기본값 설정
    if (this.driverClassName == null) {
      this.driverClassName = "com.mysql.cj.jdbc.Driver";
      System.out.println("Setting default driver: " + this.driverClassName);
    }

    // URL이 null인 경우 기본값 설정
    if (this.url == null) {
      this.url = "jdbc:mysql://127.0.0.1:3306/mydb";
      System.out.println("Setting default URL: " + this.url);
    }

    // Vault에서 동적 데이터베이스 자격 증명 가져오기 (매번 최신 credential 조회)
    String finalUsername = this.username;
    String finalPassword = this.password;

    try {
      var dbCredentials = vaultSecretService.getDatabaseCredentials("db-demo-dynamic");
      if (dbCredentials != null && dbCredentials.getData() != null) {
        String vaultUsername = (String) dbCredentials.getData().get("username");
        String vaultPassword = (String) dbCredentials.getData().get("password");

        if (vaultUsername != null && vaultPassword != null) {
          System.out.println("Using Vault credentials: " + vaultUsername);
          finalUsername = vaultUsername;
          finalPassword = vaultPassword;
        }
      }
    } catch (Exception e) {
      System.out.println("Failed to get Vault credentials, using default: " + e.getMessage());
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
