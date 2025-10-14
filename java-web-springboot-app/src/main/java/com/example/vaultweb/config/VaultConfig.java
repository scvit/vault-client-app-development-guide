package com.example.vaultweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Vault 설정 관리 클래스
 * Spring Cloud Vault Config를 통해 자동으로 설정값을 주입받습니다.
 */
@Configuration
@Component
@RefreshScope
@ConfigurationProperties(prefix = "vault")
public class VaultConfig {

  private String host;
  private int port;
  private String scheme;
  private String authentication;
  private AppRole appRole;
  private Kv kv;
  private Database database;
  private Generic generic;

  // Getters and Setters
  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getScheme() {
    return scheme;
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  public String getAuthentication() {
    return authentication;
  }

  public void setAuthentication(String authentication) {
    this.authentication = authentication;
  }

  public AppRole getAppRole() {
    return appRole;
  }

  public void setAppRole(AppRole appRole) {
    this.appRole = appRole;
  }

  public Kv getKv() {
    return kv;
  }

  public void setKv(Kv kv) {
    this.kv = kv;
  }

  public Database getDatabase() {
    return database;
  }

  public void setDatabase(Database database) {
    this.database = database;
  }

  public Generic getGeneric() {
    return generic;
  }

  public void setGeneric(Generic generic) {
    this.generic = generic;
  }

  // Inner Classes
  public static class AppRole {
    private String roleId;
    private String secretId;
    private String role;
    private String appRolePath;

    // Getters and Setters
    public String getRoleId() {
      return roleId;
    }

    public void setRoleId(String roleId) {
      this.roleId = roleId;
    }

    public String getSecretId() {
      return secretId;
    }

    public void setSecretId(String secretId) {
      this.secretId = secretId;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getAppRolePath() {
      return appRolePath;
    }

    public void setAppRolePath(String appRolePath) {
      this.appRolePath = appRolePath;
    }
  }

  public static class Kv {
    private boolean enabled;
    private String backend;
    private String defaultContext;
    private String applicationName;

    // Getters and Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBackend() {
      return backend;
    }

    public void setBackend(String backend) {
      this.backend = backend;
    }

    public String getDefaultContext() {
      return defaultContext;
    }

    public void setDefaultContext(String defaultContext) {
      this.defaultContext = defaultContext;
    }

    public String getApplicationName() {
      return applicationName;
    }

    public void setApplicationName(String applicationName) {
      this.applicationName = applicationName;
    }
  }

  public static class Database {
    private boolean enabled;
    private String backend;
    private String role;
    private String usernameProperty;
    private String passwordProperty;

    // Getters and Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBackend() {
      return backend;
    }

    public void setBackend(String backend) {
      this.backend = backend;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getUsernameProperty() {
      return usernameProperty;
    }

    public void setUsernameProperty(String usernameProperty) {
      this.usernameProperty = usernameProperty;
    }

    public String getPasswordProperty() {
      return passwordProperty;
    }

    public void setPasswordProperty(String passwordProperty) {
      this.passwordProperty = passwordProperty;
    }
  }

  public static class Generic {
    private boolean enabled;
    private String backend;
    private String applicationName;

    // Getters and Setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBackend() {
      return backend;
    }

    public void setBackend(String backend) {
      this.backend = backend;
    }

    public String getApplicationName() {
      return applicationName;
    }

    public void setApplicationName(String applicationName) {
      this.applicationName = applicationName;
    }
  }
}
