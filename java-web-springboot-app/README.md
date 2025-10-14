# Vault Spring Boot 웹 애플리케이션

## 📖 예제 목적 및 사용 시나리오

이 예제는 Spring Boot와 Spring Cloud Vault Config를 사용하여 Vault 시크릿을 웹 애플리케이션에서 관리하는 방법을 보여줍니다.
Spring Cloud Vault Config를 사용하여 자동으로 시크릿을 주입받고, Thymeleaf를 통해 웹 UI로 시크릿 정보를 표시합니다.

Spring Boot에서 `datasource` 설정으로 Database 연결을 관리합니다. 이 예제에서는 Vault에서 제공하는 Dynamic Secret을 사용하여 Database 연결을 관리합니다.
`DatabaseConfig` 클래스에서 `@RefreshScope` 어노테이션을 사용하여 Database 연결 정보를 실시간으로 갱신합니다.

### 🎯 주요 시나리오
- **AppRole 인증**: Role ID + Secret ID를 통한 안전한 Vault 접근
- **자동 시크릿 주입**: Spring Cloud Vault Config를 통한 자동 시크릿 관리
- **자동 Token Renewal**: Spring Cloud Vault가 Token 갱신을 자동으로 처리
- **웹 UI 제공**: Thymeleaf를 통한 시크릿 정보 시각화
- **실시간 갱신**: @RefreshScope를 통한 시크릿 자동 갱신
- **Database 연동**: Vault Dynamic Secret으로 Database 연결 및 통계 정보 조회

### 🔐 지원 시크릿 타입
- **KV v2**: 키-값 저장소 (Vault API 직접 호출)
- **Database Dynamic**: 동적 데이터베이스 자격증명 (DataSource 자동 구성)
- **Database Static**: 정적 데이터베이스 자격증명 (Vault API 직접 호출)

### 💡 개발 고려사항
- **Spring Cloud Vault Config**: Database Dynamic Secret만 자동 주입
- **@RefreshScope**: Database 연결 시 최신 Dynamic Secret 사용
- **웹 UI**: Thymeleaf를 통한 사용자 친화적 인터페이스
- **Database 연동**: Vault Dynamic Secret으로 Database 연결

## 🚀 빠른 시작

### 1. 사전 요구사항
- Java 11 이상
- Gradle 7.0 이상
- Vault 서버 (개발 서버 설정 완료)
- MySQL (Database 연동용)

### 2. 빌드 및 실행

```bash
# Gradle 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 파일 실행
java -jar build/libs/vault-web-app-1.0.0.war
```

### 3. 웹 브라우저 접속
```
http://localhost:8080/vault-web
```

## 📋 주요 기능

### 1. AppRole 인증 및 자동 Token Renewal
Spring Cloud Vault가 AppRole 인증과 Token 갱신을 자동으로 처리합니다:

```yaml
# bootstrap.yml
spring:
  cloud:
    vault:
      authentication: APPROLE
      app-role:
        role-id: 4060cafc-6cda-3bb4-a690-63177f9a5bc6
        secret-id: 715d4f2c-20ed-6aac-235d-b075b65c9d74
```

### 2. 자동 시크릿 주입
Spring Cloud Vault Config가 Database Dynamic Secret을 자동으로 주입합니다:

```yaml
# bootstrap.yml
spring:
  cloud:
    vault:
      authentication: APPROLE
      app-role:
        role-id: <setup 스크립트에서 출력된 Role ID>
        secret-id: <setup 스크립트에서 출력된 Secret ID>
      database:
        enabled: true
        backend: my-vault-app-database
        role: db-demo-dynamic
        username-property: spring.datasource.username
        password-property: spring.datasource.password
```

**중요**: Spring Cloud Vault Config는 Database Dynamic Secret만 자동 주입합니다. KV와 Database Static Secret은 Vault API를 직접 호출하여 조회합니다.

### 2. 웹 UI 기능
- **시크릿 정보 표시**: KV, Database Dynamic/Static 시크릿 정보
- **Database 연결 테스트**: Vault Dynamic Secret으로 Database 연결
- **Database 통계**: Database 연결 통계 정보
- **자동 새로고침**: 30초마다 자동 페이지 갱신
- **수동 갱신**: 시크릿 수동 갱신 기능

### 3. API 엔드포인트
- `GET /` - 메인 페이지 (index.html)
- `GET /refresh` - 시크릿 갱신

## ⚙️ 설정 옵션

### Vault 설정 (bootstrap.yml)
```yaml
spring:
  cloud:
    vault:
      host: localhost
      port: 8200
      scheme: http
      authentication: APPROLE
      app-role:
        role-id: <setup 스크립트에서 출력된 Role ID>
        secret-id: <setup 스크립트에서 출력된 Secret ID>
      database:
        enabled: true
        backend: my-vault-app-database
        role: db-demo-dynamic
        username-property: spring.datasource.username
        password-property: spring.datasource.password
```

### 애플리케이션 설정 (application.yml)
```yaml
server:
  port: 8080
  servlet:
    context-path: /vault-web

spring:
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mydb
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 환경변수 설정
```bash
# AppRole 인증 설정 (선택사항)
export SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID=4060cafc-6cda-3bb4-a690-63177f9a5bc6
export SPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=715d4f2c-20ed-6aac-235d-b075b65c9d74
```

## 🏗️ 아키텍처

### 디렉토리 구조
```
java-web-springboot-app/
├── build.gradle                        # Gradle 빌드 설정
├── settings.gradle                     # Gradle 프로젝트 설정
├── src/
│   ├── main/
│   │   ├── java/com/example/vaultweb/
│   │   │   ├── VaultWebApplication.java       # Spring Boot 메인
│   │   │   ├── config/
│   │   │   │   ├── VaultConfig.java           # Vault 설정
│   │   │   │   └── DatabaseConfig.java        # Database 설정
│   │   │   ├── controller/
│   │   │   │   └── HomeController.java        # 메인 컨트롤러
│   │   │   ├── service/
│   │   │   │   ├── VaultSecretService.java    # Vault 시크릿 서비스
│   │   │   │   └── DatabaseService.java       # Database 서비스
│   │   │   └── model/
│   │   │       └── SecretInfo.java            # 시크릿 정보 모델
│   │   ├── resources/
│   │   │   ├── application.yml                # Spring Boot 설정
│   │   │   ├── bootstrap.yml                  # Vault 부트스트랩 설정
│   │   │   ├── logback-spring.xml             # 로깅 설정
│   │   │   └── templates/
│   │   │       └── index.html                  # 메인 Thymeleaf 페이지
│   └── test/
│       └── java/com/example/vaultweb/
│           └── VaultWebApplicationTest.java
└── README.md                           # 사용법 가이드
```

### 주요 컴포넌트
- **VaultWebApplication**: Spring Boot 메인 애플리케이션
- **VaultConfig**: Vault 설정 관리
- **DatabaseConfig**: Database 설정 관리 (@RefreshScope로 Dynamic Secret 갱신)
- **VaultSecretService**: Vault 시크릿 조회 서비스 (KV, Static Secret 직접 호출)
- **DatabaseService**: Database 연결 및 통계 정보 조회
- **HomeController**: 웹 요청 처리
- **index.html**: 메인 Thymeleaf 페이지

### Spring Cloud Vault Config 활용
- **Database Dynamic Secret 자동 주입**: DataSource에 자동 주입
- **@RefreshScope**: Database 연결 시 최신 Dynamic Secret 사용
- **VaultTemplate**: KV, Static Secret 직접 호출
- **자동 토큰 갱신**: Spring Cloud Vault가 자동 처리

## 🛠️ 개발자 가이드

### 1. 프로젝트 구조 이해
```
src/main/java/com/example/vaultweb/
├── VaultWebApplication.java          # Spring Boot 메인
├── config/                           # 설정 클래스들
├── controller/                       # 웹 컨트롤러
├── service/                          # 비즈니스 로직
└── model/                           # 데이터 모델
```

### 2. 주요 기능 구현
- **AppRole 인증**: Spring Cloud Vault가 자동으로 AppRole 인증 및 Token 갱신 처리
- **Vault 연동**: Spring Cloud Vault Config (Dynamic Secret) + Vault API (KV, Static Secret)
- **시크릿 조회**: KV, Database Dynamic/Static 시크릿 조회
- **웹 UI**: Thymeleaf를 통한 시크릿 정보 표시
- **Database 연동**: Vault Dynamic Secret으로 Database 연결

### 3. 확장 가능한 구조
- 새로운 시크릿 엔진 추가
- REST API 엔드포인트 추가
- 웹 UI 개선 및 기능 추가

## 🔧 빌드 및 실행

```bash
# Gradle 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# WAR 파일 생성
./gradlew war

# 테스트 실행
./gradlew test
```

## 🐛 문제 해결

1. **Vault 연결 실패**: bootstrap.yml의 Vault 설정 확인
2. **AppRole 인증 실패**: Role ID와 Secret ID가 올바른지 확인, Vault 서버에서 AppRole이 활성화되어 있는지 확인
3. **Database Dynamic Secret 주입 실패**: Vault AppRole 권한 및 Database 설정 확인
4. **Database 연결 실패**: MySQL 설정 및 Vault Dynamic Secret 확인
5. **Thymeleaf 렌더링 실패**: Thymeleaf 의존성 및 설정 확인
6. **KV/Static Secret 조회 실패**: VaultTemplate 설정 및 Vault API 권한 확인

## 📚 참고 자료

- [Spring Cloud Vault 공식 문서](https://spring.io/projects/spring-cloud-vault)
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Vault AppRole 인증 방법](https://www.vaultproject.io/docs/auth/approle)
- [Thymeleaf 템플릿 엔진](https://www.thymeleaf.org/)
- [Gradle 빌드 도구](https://gradle.org/)
- [MySQL Connector/J](https://dev.mysql.com/doc/connector-j/8.0/en/)
