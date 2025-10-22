# Vault Development Guide

[아래 원본 한국어 섹션으로 이동](#vault-개발-가이드)

## 📖 Overview

This guide covers how to securely manage secrets in applications using Vault. It provides examples for developers on how to dynamically fetch and automatically renew secrets by directly integrating with the Vault API.

### Key Scenarios
- **AppRole Authentication**: Secure Vault access via Role ID + Secret ID.
- **Automatic Secret Injection**: Automatic secret management via Spring Cloud Vault Config.
- **Automatic Token Renewal**: Spring Cloud Vault handles token renewal automatically.
- **Web UI Provision**: Visualization of secret information using Thymeleaf.
- **Real-time Updates**: Automatic secret renewal via @RefreshScope.
- **Database Integration**: Database connection and statistics retrieval using Vault Dynamic Secrets.

### Mandatory Operational Requirements

- The C, C++, Java, Python, Spring Boot, and Tomcat examples use AppRole authentication.
- After AppRole authentication, the app receives a token from Vault.
- The token has specific permissions and an expiration time, similar to a web session.
- The token must be renewed before it expires, much like a browser session is renewed upon activity.
- If renewal fails, access to Vault is denied.

### Token Renewal Exceptions

- Spring Cloud Vault Config, used in the Spring Boot example, handles token renewal automatically.
- For scripts, where token renewal is difficult to implement, Vault Proxy is used as a substitute.

## 🗂️ Directory Structure

```
Development-Guide/
├── README.md                           # This file (Overall Guide Overview)
├── 01.개요.md                          # Vault Integration Development Overview and Methodology
├── 02.동작시퀀스.md                     # Detailed Operational Sequence and Implementation
├── 03.Vault개발서버.md                 # Vault Development Server Setup Guide
├── setup-vault-for-my-vault-app.sh     # Vault Server Auto-Setup Script
├── c-app/                              # C Vault Client Example
│   ├── README.md                       # C Example Usage
│   ├── src/                            # Source Code
│   │   ├── main.c                      # Main Application
│   │   ├── vault_client.c              # Vault Client Implementation
│   │   ├── vault_client.h              # Vault Client Header
│   │   └── config.c                    # Configuration Management
│   ├── config.ini                      # Configuration File
│   ├── config.h                        # Configuration Header
│   └── Makefile                        # Build Configuration
├── cpp-app/                            # C++ Vault Client Example
│   ├── README.md                       # C++ Example Usage
│   ├── CMakeLists.txt                  # CMake Build Configuration
│   ├── config.ini                      # Configuration File (same as C version)
│   ├── include/                        # Header Files
│   │   ├── Config.hpp                  # Configuration Management Class
│   │   ├── HttpClient.hpp              # HTTP Client Wrapper
│   │   └── VaultClient.hpp             # Vault Client Class
│   ├── src/                            # Source Files
│   │   ├── main.cpp                    # Main Application
│   │   ├── Config.cpp                  # Configuration Implementation
│   │   ├── HttpClient.cpp              # HTTP Client Implementation
│   │   └── VaultClient.cpp             # Vault Client Implementation
│   └── third_party/                    # Third-party Libraries
│       └── json.hpp                    # nlohmann/json header
├── java-pure-app/                      # Java Vault Client Example
│   ├── README.md                       # Java Example Usage
│   ├── src/main/java/com/example/vault/ # Java Source Code
│   │   ├── VaultApplication.java        # Main Application
│   │   ├── client/VaultClient.java      # Vault Client
│   │   └── config/VaultConfig.java      # Configuration Management
│   ├── src/main/resources/             # Resource Files
│   │   ├── config.properties            # Configuration File
│   │   └── logback.xml                  # Logging Configuration
│   └── pom.xml                          # Maven Configuration
├── python-app/                         # Python Vault Client Example
│   ├── README.md                       # Python Example Usage
│   ├── requirements.txt                # Python Package Dependencies
│   ├── config.ini                      # Configuration File
│   ├── vault_app.py                    # Main Application
│   ├── vault_client.py                 # Vault Client Class
│   └── config_loader.py                # Configuration Loader
├── java-web-springboot-app/           # Spring Boot Web Application Example
│   ├── README.md                       # Spring Boot Example Usage
│   ├── build.gradle                    # Gradle Build Configuration
│   ├── src/main/java/com/example/vaultweb/ # Spring Boot Source Code
│   │   ├── VaultWebApplication.java     # Spring Boot Main Application
│   │   ├── config/                      # Configuration Classes
│   │   │   ├── VaultConfig.java         # Vault Configuration
│   │   │   └── DatabaseConfig.java      # Database Configuration
│   │   ├── controller/                  # Web Controllers
│   │   │   └── HomeController.java      # Main Controller
│   │   ├── service/                     # Business Logic
│   │   │   ├── VaultSecretService.java  # Vault Secret Service
│   │   │   └── DatabaseService.java     # Database Service
│   │   └── model/                       # Data Models
│   │       └── SecretInfo.java          # Secret Info Model
│   ├── src/main/resources/             # Resource Files
│   │   ├── application.yml              # Spring Boot Configuration
│   │   ├── bootstrap.yml                # Vault Bootstrap Configuration
│   │   ├── logback-spring.xml           # Logging Configuration
│   │   └── templates/                   # Thymeleaf Templates
│   │       └── index.html               # Main Web Page
│   └── src/test/java/                  # Test Code
├── java-web-tomcat-app/               # Tomcat Web Application Example
│   ├── README.md                       # Tomcat Example Usage
│   ├── pom.xml                         # Maven Build Configuration
│   ├── src/main/java/com/example/vaulttomcat/ # Tomcat Source Code
│   │   ├── config/                      # Configuration Classes
│   │   │   ├── VaultConfig.java         # Vault Configuration
│   │   │   ├── DatabaseConfig.java      # Database Connection Pool Configuration
│   │   │   └── TokenRenewalScheduler.java # Token Auto-Renewal Scheduler
│   │   ├── client/                      # Vault API Client
│   │   │   └── VaultClient.java         # Vault API Client
│   │   ├── service/                     # Business Logic
│   │   │   ├── VaultSecretService.java  # Vault Secret Service
│   │   │   └── DatabaseService.java     # Database Service
│   │   ├── servlet/                     # Web Servlets
│   │   │   ├── HomeServlet.java         # Main Page Servlet
│   │   │   └── RefreshServlet.java     # Secret Renewal Servlet
│   │   ├── listener/                    # Application Listeners
│   │   │   └── AppContextListener.java  # Application Initialization Listener
│   │   └── model/                       # Data Models
│   │       └── SecretInfo.java         # Secret Info Model
│   ├── src/main/resources/             # Resource Files
│   │   ├── vault.properties             # Vault Configuration File
│   │   └── logback.xml                  # Logging Configuration
│   ├── src/main/webapp/                 # Web Application Resources
│   │   ├── WEB-INF/
│   │   │   ├── web.xml                  # Web Application Deployment Descriptor
│   │   │   └── jsp/
│   │   │       └── index.jsp            # Main JSP Page
│   │   └── css/
│   │       └── style.css                # Stylesheet
│   └── tomcat/                          # Tomcat 10 Execution Environment
│       ├── bin/                         # Tomcat Execution Scripts
│       ├── webapps/                     # WAR Deployment Directory
│       └── logs/                        # Log Files
└── script-sample/                      # Vault Proxy Integration Script Examples
    ├── README.md                       # Script Usage Guide
    ├── get_kv_secret_proxy.sh          # Get KV Secret (Proxy)
    ├── get_db_dynamic_secret_proxy.sh   # Get Database Dynamic Secret (Proxy)
    ├── get_db_static_secret_proxy.sh   # Get Database Static Secret (Proxy)
    ├── get_ssh_otp_proxy.sh            # Generate SSH OTP (Proxy)
    ├── get_ssh_signed_cert_proxy.sh    # Generate SSH Signed Certificate (Proxy)
    ├── get_ssh_kv_proxy.sh             # Get SSH KV Credentials (Proxy)
    ├── get_aws_userpass.sh             # Get AWS Credentials (Userpass)
    ├── get_aws_oidc.sh                 # Get AWS Credentials (OIDC)
    └── vault-proxy-demo/               # Vault Proxy Demo Environment
        ├── README.md                   # Vault Proxy Usage
        ├── vault-proxy.hcl             # Vault Proxy Configuration File
        ├── setup-token.sh              # Token File Creation Script
        ├── start-proxy.sh              # Proxy Start Script
        └── stop-proxy.sh               # Proxy Stop Script
```

## 📚 Document Guide

### 1. [Overview and Methodology](./01.개요.md)
- **Target**: All Developers
- **Content**: Basic concepts of Vault integration and three different approaches.
- **Core**: Comparison of Direct API Integration, Vault Proxy, and Vault Agent.

### 2. [Operational Sequence](./02.동작시퀀스.md)
- **Target**: Implementation Developers
- **Content**: Detailed processes for authentication, token renewal, and secret retrieval.
- **Core**: Step-by-step explanation of all stages required for actual implementation.

### 3. [Vault Development Server](./03.Vault개발서버.md)
- **Target**: Vault Administrators and Developers
- **Content**: How to set up a Vault server in a development environment.
- **Core**: Configuration of AppRole, Entities, and Secret Engines.

## 🛠️ Code Examples

### C Example ([c-app/](./c-app/))
- **Language**: C (libcurl + json-c)
- **Features**:
  - Supports KV v2, Database Dynamic, and Database Static secret engines.
  - Real-time renewal, version-based caching, TTL-based renewal.
  - Entity-based permissions, automatic token renewal.
- **Build**: Simple build with `make` command.
- **Run**: Execute `./vault-app`.

### C++ Example ([cpp-app/](./cpp-app/))
- **Language**: C++17 (libcurl + nlohmann/json)
- **Features**:
  - Modern C++17 style, RAII pattern, smart pointers.
  - CMake-based cross-platform build.
  - Thread safety (std::mutex, std::atomic).
  - Same features and configuration compatibility as the C version.
- **Build**: `mkdir build && cd build && cmake .. && make`
- **Run**: `./vault-app`

### Java Example ([java-pure-app/](./java-pure-app/))
- **Language**: Java 11+ (Apache HttpClient + Jackson)
- **Features**:
  - Maven-based project structure.
  - Real-time TTL calculation, multi-threaded renewal.
  - System property override support.
- **Build**: `mvn clean package`
- **Run**: `java -jar target/vault-java-app.jar`

### Python Example ([python-app/](./python-app/))
- **Language**: Python 3.7+ (hvac + threading)
- **Features**:
  - Uses hvac 2.3.0, a dedicated Vault library.
  - Virtual environment-based package management.
  - Environment variable override support.
  - Real-time TTL calculation, multi-threaded renewal.
- **Install**: `pip install -r requirements.txt`
- **Run**: `python vault_app.py`

### Spring Boot Web Example ([java-web-springboot-app/](./java-web-springboot-app/))
- **Language**: Java 11+ (Spring Boot + Spring Cloud Vault Config)
- **Features**:
  - Vault access via AppRole authentication.
  - Automatic Token Renewal by Spring Cloud Vault.
  - Automatic injection of Database Dynamic Secrets via Spring Cloud Vault Config.
  - Web UI provided through Thymeleaf.
  - Real-time secret updates via @RefreshScope.
  - Direct Vault API calls to fetch KV and Static Secrets.
  - MySQL integration and display of database statistics.
  - **Credential Source Selection**: Choose between KV, Dynamic, and Static (testing complete).
- **Build**: `./gradlew build`
- **Run**: `./gradlew bootRun`
- **Web Access**: `http://localhost:8080/vault-web`

### Tomcat Web Example ([java-web-tomcat-app/](./java-web-tomcat-app/))
- **Language**: Java 11+ (Servlet + JSP + Apache Commons DBCP2)
- **Features**:
  - Traditional Java Web Application (Servlet + JSP).
  - Vault access via AppRole authentication.
  - Token Auto-Renewal (checks every 10s, renews at 80% of TTL).
  - Automatic renewal of Apache Commons DBCP2 Connection Pool.
  - Web UI provided through JSP + JSTL.
  - MySQL integration and display of database statistics.
  - **Credential Source Selection**: Choose between KV, Dynamic, and Static.
- **Build**: `mvn clean package`
- **Run**: Deploy WAR to Tomcat 10.
- **Web Access**: `http://localhost:8080/vault-tomcat-app`

## 🔌 Vault API Types and Usage

This section introduces the main Vault APIs used in this guide.

### 1. **Authentication APIs**

#### AppRole Authentication
- **Endpoint**: `POST /v1/auth/approle/login`
- **Usage**: Allows an application to log in to Vault and obtain a client token.
- **Request Data**: `role_id`, `secret_id`
- **Response**: `client_token`, `lease_duration`

```json
{
  "role_id": "your-role-id",
  "secret_id": "your-secret-id"
}
```

#### Token Renewal
- **Endpoint**: `POST /v1/auth/token/renew-self`
- **Usage**: Renews a client token before it expires.
- **Header**: `X-Vault-Token: <client_token>`
- **Response**: New `lease_duration`

### 2. **Secret Engine APIs**

#### KV v2 Secrets Engine
- **Endpoint**: `GET /v1/{mount-path}/data/{secret-path}`
- **Usage**: Stores and retrieves static key-value secrets.
- **Features**: Versioning, includes metadata.
- **Response Structure**:
```json
{
  "data": {
    "data": {
      "username": "dbuser",
      "password": "secretpassword"
    },
    "metadata": {
      "version": 1,
      "created_time": "2024-01-01T00:00:00Z"
    }
  }
}
```

#### Database Dynamic Secrets Engine
- **Endpoint**: `GET /v1/{mount-path}/creds/{role-name}`
- **Usage**: Generates temporary, dynamically created database credentials.
- **Features**: TTL-based automatic expiration, Lease ID management.
- **Response Structure**:
```json
{
  "data": {
    "username": "v-approle-db-demo-dy-abc123",
    "password": "xyz789"
  },
  "lease_id": "lease-abc123",
  "lease_duration": 3600
}
```

#### Database Static Secrets Engine
- **Endpoint**: `GET /v1/{mount-path}/static-creds/{role-name}`
- **Usage**: Manages statically defined database credentials.
- **Features**: Manual renewal, long-term validity.
- **Response Structure**:
```json
{
  "data": {
    "username": "myapp-static-user",
    "password": "static-password"
  },
  "ttl": 3600
}
```

### 3. **Lease Management APIs**

#### Check Lease Status
- **Endpoint**: `GET /v1/sys/leases/lookup`
- **Usage**: Checks the status and TTL of a specific lease.
- **Header**: `X-Vault-Token: <client_token>`
- **Request Data**: `lease_id`

#### Renew Lease
- **Endpoint**: `PUT /v1/sys/leases/renew`
- **Usage**: Renews the lease of a Database Dynamic secret.
- **Header**: `X-Vault-Token: <client_token>`
- **Request Data**: `lease_id`, `increment` (optional)

### 4. **System APIs**

#### Check Vault Health
- **Endpoint**: `GET /v1/sys/health`
- **Usage**: Checks the health and availability of the Vault server.
- **Response**: Server status, version information.

#### Look Up Token Information
- **Endpoint**: `GET /v1/auth/token/lookup-self`
- **Usage**: Checks the information and expiration time of the current token.
- **Header**: `X-Vault-Token: <client_token>`

## 🚀 Quick Start

### 1. Set Up Vault Server
```bash
# Automatically set up the Vault development server
./setup-vault-for-my-vault-app.sh
```

### 2. Run Examples

#### Programming Language Examples
```bash
# C Example
cd c-app
make
./vault-app

# C++ Example
cd cpp-app
mkdir build && cd build
cmake ..
make
./vault-app

# Java Example
cd java-pure-app
mvn clean package
java -jar target/vault-java-app.jar

# Python Example
cd python-app
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python vault_app.py

# Spring Boot Web Example
cd java-web-springboot-app
./gradlew bootRun
# Access in web browser: http://localhost:8080/vault-web

# Tomcat Web Example
cd java-web-tomcat-app
mvn clean package
cp target/vault-tomcat-app.war tomcat/webapps/
cd tomcat && bin/catalina.sh start
# Access in web browser: http://localhost:8080/vault-tomcat-app
```

#### Script Examples (with Vault Proxy)
```bash
# Set up and run Vault Proxy
cd script-sample/vault-proxy-demo
./setup-token.sh
./start-proxy.sh

# Move to the parent directory and run scripts
cd ..
./get_kv_secret_proxy.sh
./get_db_dynamic_secret_proxy.sh
./get_db_static_secret_proxy.sh
./get_ssh_otp_proxy.sh
./get_ssh_signed_cert_proxy.sh
./get_ssh_kv_proxy.sh

# Stop Vault Proxy
cd vault-proxy-demo
./stop-proxy.sh
```

### Script Examples (script-sample/)
- **Bash Scripts**: Direct Vault API calls and Vault Proxy integration.
- **Supported Secrets**: KV, Database Dynamic/Static, SSH (KV/OTP/Signed Certificate).
- **Features**: No Vault CLI required, uses pure curl, supports JSON parsing.

# Vault 개발 가이드

## 📖 개요

이 가이드는 Vault를 활용하여 애플리케이션에서 시크릿을 안전하게 관리하는 방법을 다룹니다. 개발자들이 Vault API를 직접 연계하여 시크릿을 동적으로 가져오고, 자동 갱신하는 방법을 개발하기 위한 예시를 제공합니다.

### 주요 시나리오
- **AppRole 인증**: Role ID + Secret ID를 통한 안전한 Vault 접근
- **자동 시크릿 주입**: Spring Cloud Vault Config를 통한 자동 시크릿 관리
- **자동 Token Renewal**: Spring Cloud Vault가 Token 갱신을 자동으로 처리
- **웹 UI 제공**: Thymeleaf를 통한 시크릿 정보 시각화
- **실시간 갱신**: @RefreshScope를 통한 시크릿 자동 갱신
- **Database 연동**: Vault Dynamic Secret으로 Database 연결 및 통계 정보 조회

### 필수 동작 요구사항

- C, C++, Java, Python, Spring Boot, Tomcat 예제는 AppRole 인증을 사용합니다.
- AppRole 인증 후 App은 Vault로부터 Token을 받습니다.
- Token은 고유 권한이 부여되어 있으며, Web session처럼 만료 시간이 있습니다.
- Token은 만료되기 전에, 마치 브라우저에서 갱신하거나 특정 동작을 하면 Session 시간이 초기회 되는 것처럼 Token에 대해 Renewal 해야 합니다.
- Renewal이 실패하면 Vault로의 접근이 불가능합니다.

### Token Renewal 예외 사항

- Spring Boot에서 사용된 Spring Cloud Vault Config는 Token Renewal을 자동으로 처리합니다.
- Script의 경우 Token Renewal 처리가 어려운 관계로, 이를 대신하는 Vault Proxy를 사용합니다.

## 🗂️ 디렉토리 구조

```
Development-Guide/
├── README.md                           # 이 파일 (전체 가이드 개요)
├── 01.개요.md                          # Vault 연동 개발 개요 및 방법론
├── 02.동작시퀀스.md                     # 상세한 동작 시퀀스 및 구현 방법
├── 03.Vault개발서버.md                 # Vault 개발 서버 설정 가이드
├── setup-vault-for-my-vault-app.sh     # Vault 서버 자동 설정 스크립트
├── c-app/                              # C언어 Vault 클라이언트 예제
│   ├── README.md                       # C언어 예제 사용법
│   ├── src/                            # 소스 코드
│   │   ├── main.c                      # 메인 애플리케이션
│   │   ├── vault_client.c              # Vault 클라이언트 구현
│   │   ├── vault_client.h              # Vault 클라이언트 헤더
│   │   └── config.c                    # 설정 관리
│   ├── config.ini                      # 설정 파일
│   ├── config.h                        # 설정 헤더
│   └── Makefile                        # 빌드 설정
├── cpp-app/                            # C++ Vault 클라이언트 예제
│   ├── README.md                       # C++ 예제 사용법
│   ├── CMakeLists.txt                  # CMake 빌드 설정
│   ├── config.ini                      # 설정 파일 (C 버전과 동일)
│   ├── include/                        # 헤더 파일들
│   │   ├── Config.hpp                  # 설정 관리 클래스
│   │   ├── HttpClient.hpp              # HTTP 클라이언트 래퍼
│   │   └── VaultClient.hpp             # Vault 클라이언트 클래스
│   ├── src/                            # 소스 파일들
│   │   ├── main.cpp                    # 메인 애플리케이션
│   │   ├── Config.cpp                  # 설정 구현
│   │   ├── HttpClient.cpp              # HTTP 클라이언트 구현
│   │   └── VaultClient.cpp             # Vault 클라이언트 구현
│   └── third_party/                    # 서드파티 라이브러리
│       └── json.hpp                    # nlohmann/json 헤더
├── java-pure-app/                      # Java Vault 클라이언트 예제
│   ├── README.md                       # Java 예제 사용법
│   ├── src/main/java/com/example/vault/ # Java 소스 코드
│   │   ├── VaultApplication.java        # 메인 애플리케이션
│   │   ├── client/VaultClient.java      # Vault 클라이언트
│   │   └── config/VaultConfig.java      # 설정 관리
│   ├── src/main/resources/             # 리소스 파일
│   │   ├── config.properties            # 설정 파일
│   │   └── logback.xml                  # 로깅 설정
│   └── pom.xml                          # Maven 설정
├── python-app/                         # Python Vault 클라이언트 예제
│   ├── README.md                       # Python 예제 사용법
│   ├── requirements.txt                # Python 패키지 의존성
│   ├── config.ini                      # 설정 파일
│   ├── vault_app.py                    # 메인 애플리케이션
│   ├── vault_client.py                 # Vault 클라이언트 클래스
│   └── config_loader.py                # 설정 로더
├── java-web-springboot-app/           # Spring Boot 웹 애플리케이션 예제
│   ├── README.md                       # Spring Boot 예제 사용법
│   ├── build.gradle                    # Gradle 빌드 설정
│   ├── src/main/java/com/example/vaultweb/ # Spring Boot 소스 코드
│   │   ├── VaultWebApplication.java     # Spring Boot 메인 애플리케이션
│   │   ├── config/                      # 설정 클래스들
│   │   │   ├── VaultConfig.java         # Vault 설정
│   │   │   └── DatabaseConfig.java      # Database 설정
│   │   ├── controller/                  # 웹 컨트롤러
│   │   │   └── HomeController.java      # 메인 컨트롤러
│   │   ├── service/                     # 비즈니스 로직
│   │   │   ├── VaultSecretService.java  # Vault 시크릿 서비스
│   │   │   └── DatabaseService.java     # Database 서비스
│   │   └── model/                       # 데이터 모델
│   │       └── SecretInfo.java          # 시크릿 정보 모델
│   ├── src/main/resources/             # 리소스 파일
│   │   ├── application.yml              # Spring Boot 설정
│   │   ├── bootstrap.yml                # Vault 부트스트랩 설정
│   │   ├── logback-spring.xml           # 로깅 설정
│   │   └── templates/                   # Thymeleaf 템플릿
│   │       └── index.html               # 메인 웹 페이지
│   └── src/test/java/                  # 테스트 코드
├── java-web-tomcat-app/               # Tomcat 웹 애플리케이션 예제
│   ├── README.md                       # Tomcat 예제 사용법
│   ├── pom.xml                         # Maven 빌드 설정
│   ├── src/main/java/com/example/vaulttomcat/ # Tomcat 소스 코드
│   │   ├── config/                      # 설정 클래스들
│   │   │   ├── VaultConfig.java         # Vault 설정
│   │   │   ├── DatabaseConfig.java      # Database Connection Pool 설정
│   │   │   └── TokenRenewalScheduler.java # Token 자동 갱신 스케줄러
│   │   ├── client/                      # Vault API 클라이언트
│   │   │   └── VaultClient.java         # Vault API 클라이언트
│   │   ├── service/                     # 비즈니스 로직
│   │   │   ├── VaultSecretService.java  # Vault 시크릿 서비스
│   │   │   └── DatabaseService.java     # Database 서비스
│   │   ├── servlet/                     # 웹 서블릿
│   │   │   ├── HomeServlet.java         # 메인 페이지 서블릿
│   │   │   └── RefreshServlet.java     # 시크릿 갱신 서블릿
│   │   ├── listener/                    # 애플리케이션 리스너
│   │   │   └── AppContextListener.java  # 애플리케이션 초기화 리스너
│   │   └── model/                       # 데이터 모델
│   │       └── SecretInfo.java         # 시크릿 정보 모델
│   ├── src/main/resources/             # 리소스 파일
│   │   ├── vault.properties             # Vault 설정 파일
│   │   └── logback.xml                  # 로깅 설정
│   ├── src/main/webapp/                 # 웹 애플리케이션 리소스
│   │   ├── WEB-INF/
│   │   │   ├── web.xml                  # 웹 애플리케이션 배포 설정
│   │   │   └── jsp/
│   │   │       └── index.jsp            # 메인 JSP 페이지
│   │   └── css/
│   │       └── style.css                # 스타일시트
│   └── tomcat/                          # Tomcat 10 실행 환경
│       ├── bin/                         # Tomcat 실행 스크립트
│       ├── webapps/                     # WAR 배포 디렉토리
│       └── logs/                        # 로그 파일들
└── script-sample/                      # Vault Proxy 연계 스크립트 예제
    ├── README.md                       # 스크립트 사용법 가이드
    ├── get_kv_secret_proxy.sh          # KV 시크릿 조회 (Proxy)
    ├── get_db_dynamic_secret_proxy.sh   # Database Dynamic 시크릿 조회 (Proxy)
    ├── get_db_static_secret_proxy.sh   # Database Static 시크릿 조회 (Proxy)
    ├── get_ssh_otp_proxy.sh            # SSH OTP 생성 (Proxy)
    ├── get_ssh_signed_cert_proxy.sh    # SSH Signed Certificate 생성 (Proxy)
    ├── get_ssh_kv_proxy.sh             # SSH KV 자격증명 조회 (Proxy)
    ├── get_aws_userpass.sh             # AWS 자격증명 조회 (Userpass)
    ├── get_aws_oidc.sh                 # AWS 자격증명 조회 (OIDC)
    └── vault-proxy-demo/               # Vault Proxy 데모 환경
        ├── README.md                   # Vault Proxy 사용법
        ├── vault-proxy.hcl             # Vault Proxy 설정 파일
        ├── setup-token.sh              # Token 파일 생성 스크립트
        ├── start-proxy.sh              # Proxy 실행 스크립트
        └── stop-proxy.sh               # Proxy 중지 스크립트
```

## 📚 문서 가이드

### 1. [개요 및 방법론](./01.개요.md)
- **대상**: 모든 개발자
- **내용**: Vault 연동의 기본 개념과 3가지 접근 방법
- **핵심**: API 직접 연계, Vault Proxy, Vault Agent 비교

### 2. [동작 시퀀스](./02.동작시퀀스.md)
- **대상**: 구현 담당 개발자
- **내용**: 상세한 인증, 토큰 갱신, 시크릿 조회 과정
- **핵심**: 실제 구현 시 필요한 모든 단계별 설명

### 3. [Vault 개발 서버](./03.Vault개발서버.md)
- **대상**: Vault 관리자 및 개발자
- **내용**: 개발 환경에서 Vault 서버 설정 방법
- **핵심**: AppRole, Entity, 시크릿 엔진 설정

## 🛠️ 코드 예제

### C언어 예제 ([c-app/](./c-app/))
- **언어**: C (libcurl + json-c)
- **특징**: 
  - KV v2, Database Dynamic, Database Static 시크릿 엔진 지원
  - 실시간 갱신, 버전 기반 캐싱, TTL 기반 갱신
  - Entity 기반 권한, 자동 토큰 갱신
- **빌드**: `make` 명령어로 간단 빌드
- **실행**: `./vault-app` 실행

### C++ 예제 ([cpp-app/](./cpp-app/))
- **언어**: C++17 (libcurl + nlohmann/json)
- **특징**:
  - 모던 C++17 스타일, RAII 패턴, 스마트 포인터
  - CMake 기반 크로스 플랫폼 빌드
  - 스레드 안전성 (std::mutex, std::atomic)
  - C 버전과 동일한 기능 및 설정 호환성
- **빌드**: `mkdir build && cd build && cmake .. && make`
- **실행**: `./vault-app`

### Java 예제 ([java-pure-app/](./java-pure-app/))
- **언어**: Java 11+ (Apache HttpClient + Jackson)
- **특징**:
  - Maven 기반 프로젝트 구조
  - 실시간 TTL 계산, 멀티스레드 갱신
  - 시스템 프로퍼티 오버라이드 지원
- **빌드**: `mvn clean package`
- **실행**: `java -jar target/vault-java-app.jar`

### Python 예제 ([python-app/](./python-app/))
- **언어**: Python 3.7+ (hvac + threading)
- **특징**:
  - hvac 2.3.0 Vault 전용 라이브러리 사용
  - 가상환경 기반 패키지 관리
  - 환경변수 오버라이드 지원
  - 실시간 TTL 계산, 멀티스레드 갱신
- **설치**: `pip install -r requirements.txt`
- **실행**: `python vault_app.py`

### Spring Boot 웹 예제 ([java-web-springboot-app/](./java-web-springboot-app/))
- **언어**: Java 11+ (Spring Boot + Spring Cloud Vault Config)
- **특징**:
  - AppRole 인증을 통한 Vault 접근
  - Spring Cloud Vault의 자동 Token Renewal
  - Spring Cloud Vault Config를 통한 Database Dynamic Secret 자동 주입
  - Thymeleaf를 통한 웹 UI 제공
  - @RefreshScope를 통한 실시간 시크릿 갱신
  - Vault API 직접 호출로 KV, Static Secret 조회
  - MySQL 연동 및 Database 통계 정보 표시
  - **자격증명 소스 선택**: KV, Dynamic, Static 중 선택 가능 (테스트 완료)
- **빌드**: `./gradlew build`
- **실행**: `./gradlew bootRun`
- **웹 접속**: `http://localhost:8080/vault-web`

### Tomcat 웹 예제 ([java-web-tomcat-app/](./java-web-tomcat-app/))
- **언어**: Java 11+ (Servlet + JSP + Apache Commons DBCP2)
- **특징**:
  - 전통적인 Java Web Application (Servlet + JSP)
  - AppRole 인증을 통한 Vault 접근
  - Token Auto-Renewal (10초 간격 체크, TTL 80% 지점에서 갱신)
  - Apache Commons DBCP2 Connection Pool 자동 갱신
  - JSP + JSTL을 통한 웹 UI 제공
  - MySQL 연동 및 Database 통계 정보 표시
  - **자격증명 소스 선택**: KV, Dynamic, Static 중 선택 가능
- **빌드**: `mvn clean package`
- **실행**: Tomcat 10에 WAR 배포
- **웹 접속**: `http://localhost:8080/vault-tomcat-app`

## 🔌 Vault API 종류 및 용도

이 가이드에서 사용되는 주요 Vault API들을 소개합니다.

### 1. **인증 API (Authentication APIs)**

#### AppRole 인증
- **엔드포인트**: `POST /v1/auth/approle/login`
- **용도**: 애플리케이션이 Vault에 로그인하여 클라이언트 토큰 획득
- **요청 데이터**: `role_id`, `secret_id`
- **응답**: `client_token`, `lease_duration`

```json
{
  "role_id": "your-role-id",
  "secret_id": "your-secret-id"
}
```

#### 토큰 갱신
- **엔드포인트**: `POST /v1/auth/token/renew-self`
- **용도**: 만료되기 전에 클라이언트 토큰 갱신
- **헤더**: `X-Vault-Token: <client_token>`
- **응답**: 새로운 `lease_duration`

### 2. **시크릿 엔진 API (Secret Engine APIs)**

#### KV v2 시크릿 엔진
- **엔드포인트**: `GET /v1/{mount-path}/data/{secret-path}`
- **용도**: 정적 키-값 시크릿 저장 및 조회
- **특징**: 버전 관리, 메타데이터 포함
- **응답 구조**:
```json
{
  "data": {
    "data": {
      "username": "dbuser",
      "password": "secretpassword"
    },
    "metadata": {
      "version": 1,
      "created_time": "2024-01-01T00:00:00Z"
    }
  }
}
```

#### Database Dynamic 시크릿 엔진
- **엔드포인트**: `GET /v1/{mount-path}/creds/{role-name}`
- **용도**: 동적으로 생성되는 임시 데이터베이스 자격증명
- **특징**: TTL 기반 자동 만료, Lease ID 관리
- **응답 구조**:
```json
{
  "data": {
    "username": "v-approle-db-demo-dy-abc123",
    "password": "xyz789"
  },
  "lease_id": "lease-abc123",
  "lease_duration": 3600
}
```

#### Database Static 시크릿 엔진
- **엔드포인트**: `GET /v1/{mount-path}/static-creds/{role-name}`
- **용도**: 정적으로 관리되는 데이터베이스 자격증명
- **특징**: 수동 갱신, 장기간 유효
- **응답 구조**:
```json
{
  "data": {
    "username": "myapp-static-user",
    "password": "static-password"
  },
  "ttl": 3600
}
```

### 3. **Lease 관리 API (Lease Management APIs)**

#### Lease 상태 확인
- **엔드포인트**: `GET /v1/sys/leases/lookup`
- **용도**: 특정 Lease의 상태 및 TTL 확인
- **헤더**: `X-Vault-Token: <client_token>`
- **요청 데이터**: `lease_id`

#### Lease 갱신
- **엔드포인트**: `PUT /v1/sys/leases/renew`
- **용도**: Database Dynamic 시크릿의 Lease 갱신
- **헤더**: `X-Vault-Token: <client_token>`
- **요청 데이터**: `lease_id`, `increment` (선택사항)

### 4. **시스템 API (System APIs)**

#### Vault 상태 확인
- **엔드포인트**: `GET /v1/sys/health`
- **용도**: Vault 서버 상태 및 가용성 확인
- **응답**: 서버 상태, 버전 정보

#### 토큰 정보 조회
- **엔드포인트**: `GET /v1/auth/token/lookup-self`
- **용도**: 현재 토큰의 정보 및 만료 시간 확인
- **헤더**: `X-Vault-Token: <client_token>`

## 🚀 빠른 시작

### 1. Vault 서버 설정
```bash
# Vault 개발 서버 자동 설정
./setup-vault-for-my-vault-app.sh
```

### 2. 예제 실행

#### 프로그래밍 언어 예제
```bash
# C언어 예제
cd c-app
make
./vault-app

# C++ 예제
cd cpp-app
mkdir build && cd build
cmake ..
make
./vault-app

# Java 예제
cd java-pure-app
mvn clean package
java -jar target/vault-java-app.jar

# Python 예제
cd python-app
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python vault_app.py

# Spring Boot 웹 예제
cd java-web-springboot-app
./gradlew bootRun
# 웹 브라우저에서 http://localhost:8080/vault-web 접속

# Tomcat 웹 예제
cd java-web-tomcat-app
mvn clean package
cp target/vault-tomcat-app.war tomcat/webapps/
cd tomcat && bin/catalina.sh start
# 웹 브라우저에서 http://localhost:8080/vault-tomcat-app 접속
```

#### 스크립트 예제 (Vault Proxy 연계)
```bash
# Vault Proxy 설정 및 실행
cd script-sample/vault-proxy-demo
./setup-token.sh
./start-proxy.sh

# 상위 디렉토리로 이동하여 스크립트 실행
cd ..
./get_kv_secret_proxy.sh
./get_db_dynamic_secret_proxy.sh
./get_db_static_secret_proxy.sh
./get_ssh_otp_proxy.sh
./get_ssh_signed_cert_proxy.sh
./get_ssh_kv_proxy.sh

# Vault Proxy 중지
cd vault-proxy-demo
./stop-proxy.sh
```

### 스크립트 예제 (script-sample/)
- **Bash 스크립트**: Vault API 직접 호출 및 Vault Proxy 연동
- **지원 시크릿**: KV, Database Dynamic/Static, SSH (KV/OTP/Signed Certificate)
- **특징**: Vault CLI 불필요, 순수 curl 사용, JSON 파싱 지원