# Vault Tomcat Web Application

## 📖 예제 목적 및 사용 시나리오

이 예제는 **전통적인 Java Web Application**에서 Vault를 활용하여 시크릿을 안전하게 관리하는 방법을 보여줍니다.
**Servlet + JSP** 기반으로 구현하며, **AppRole 인증**과 **Token Auto-Renewal**을 통해 지속적인 보안을 유지합니다.

### 🎯 주요 시나리오
- **전통적인 Java Web**: Servlet + JSP 기반 웹 애플리케이션 (Spring Boot 없이)
- **AppRole 인증**: Vault에 안전하게 접근하기 위한 기계-기계 인증
- **Token Auto-Renewal**: 10초마다 Token 상태 체크, TTL 80% 지점에서 자동 갱신
- **Connection Pool**: Apache Commons DBCP2를 사용한 Database 연결 풀 관리
- **자동 갱신**: TTL 기반 Database 자격증명 자동 갱신

### 🔐 지원 시크릿 타입
- **KV v2**: 키-값 저장소 (Vault API 직접 호출)
- **Database Dynamic**: 동적 데이터베이스 자격증명 (Connection Pool 자동 갱신)
- **Database Static**: 정적 데이터베이스 자격증명 (Vault API 직접 호출)

### 💡 개발 고려사항
- **Tomcat 10**: Jakarta EE 9 기반 (Servlet 5.0, JSP 3.0)
- **AppRole 인증**: Role ID + Secret ID를 통한 Vault 인증
- **Token Renewal**: ScheduledExecutorService를 통한 자동 Token 갱신
- **Connection Pool**: Apache Commons DBCP2를 사용한 Database 연결 관리
- **웹 UI**: JSP + JSTL을 통한 사용자 친화적 인터페이스

## 🚀 빠른 시작

### 1. 사전 요구사항
- Java 11 이상
- Maven 3.6 이상
- Tomcat 10
- Vault 서버 (개발 서버 설정 완료)
- MySQL (Database 연동용)

### 2. 빌드 및 배포

```bash
# Maven 빌드
mvn clean package

# WAR 파일을 Tomcat webapps 디렉토리에 복사
cp target/vault-tomcat-app.war $TOMCAT_HOME/webapps/

# Tomcat 시작
$TOMCAT_HOME/bin/startup.sh
```

### 3. 웹 브라우저 접속
```
http://localhost:8080/vault-tomcat-app/
```

## 📋 주요 기능

### 1. AppRole 인증 및 Token Auto-Renewal
**AppRole 인증**을 통해 Vault에 안전하게 접근하고, **Token Auto-Renewal**로 지속적인 보안을 유지합니다:

```java
// AppRole 인증 (VaultClient.java)
private void authenticateWithAppRole() {
    String roleId = VaultConfig.getAppRoleId();
    String secretId = VaultConfig.getAppRoleSecretId();
    
    Map<String, Object> authData = new HashMap<>();
    authData.put("role_id", roleId);
    authData.put("secret_id", secretId);
    
    // Vault API 호출로 Token 획득
    Map<String, Object> response = callAppRoleLogin(authData);
    this.vaultToken = (String) auth.get("client_token");
    this.tokenExpiry = System.currentTimeMillis() + (leaseDuration * 1000L);
}

// Token Auto-Renewal (TokenRenewalScheduler.java)
scheduler.scheduleAtFixedRate(() -> {
    if (vaultClient.shouldRenew()) {
        boolean success = vaultClient.renewToken();
        if (!success) {
            System.exit(1); // 갱신 실패 시 애플리케이션 종료
        }
    }
}, 10, 10, TimeUnit.SECONDS);
```

### 2. Database Dynamic Secret 자동 갱신
Apache Commons DBCP2 Connection Pool과 ScheduledExecutorService를 사용하여 자동 갱신:

```java
// TTL의 80% 시점에 자격증명 갱신 스케줄링
long refreshDelay = (long) (dbSecret.getTtl() * 0.8 * 1000);
scheduler.schedule(() -> refreshCredentials(), delayMs, TimeUnit.MILLISECONDS);
```

### 3. 웹 UI 기능
- **시크릿 정보 표시**: KV, Database Dynamic/Static 시크릿 정보
- **Database 연결 테스트**: Vault Dynamic Secret으로 Database 연결
- **Database 통계**: Database 연결 통계 정보
- **자동 새로고침**: 30초마다 자동 페이지 갱신
- **수동 갱신**: 시크릿 수동 갱신 기능

### 4. Servlet 엔드포인트
- `GET /` - 메인 페이지 (index.jsp)
- `GET /refresh` - 시크릿 갱신

## ⚙️ 설정 옵션

### Vault 설정 (vault.properties)
```properties
# Vault 서버 설정
vault.url=http://127.0.0.1:8200

# AppRole 인증 설정
vault.auth.type=approle
vault.approle.role_id=4060cafc-6cda-3bb4-a690-63177f9a5bc6
vault.approle.secret_id=715d4f2c-20ed-6aac-235d-b075b65c9d74

# Token renewal 설정
vault.token.renewal.enabled=true
vault.token.renewal.threshold=0.8

# KV 시크릿 엔진 설정
vault.kv.path=my-vault-app-kv

# Database 시크릿 엔진 설정
vault.database.path=my-vault-app-database
vault.database.dynamic.role=db-demo-dynamic
vault.database.static.role=db-demo-static
```

### 환경변수 설정
```bash
# Vault 서버 설정
export VAULT_URL=http://127.0.0.1:8200

# AppRole 인증 설정 (vault.properties에서 설정하는 것을 권장)
export VAULT_AUTH_TYPE=approle
export VAULT_APPROLE_ROLE_ID=4060cafc-6cda-3bb4-a690-63177f9a5bc6
export VAULT_APPROLE_SECRET_ID=715d4f2c-20ed-6aac-235d-b075b65c9d74
```

## 🏗️ 아키텍처

### 디렉토리 구조
```
java-web-tomcat-app/
├── pom.xml                                    # Maven 빌드 설정
├── src/
│   ├── main/
│   │   ├── java/com/example/vaulttomcat/
│   │   │   ├── config/
│   │   │   │   ├── VaultConfig.java          # Vault 설정
│   │   │   │   └── DatabaseConfig.java        # Database Connection Pool 설정
│   │   │   ├── client/
│   │   │   │   └── VaultClient.java          # Vault API 클라이언트
│   │   │   ├── service/
│   │   │   │   ├── VaultSecretService.java   # Vault 시크릿 서비스
│   │   │   │   └── DatabaseService.java      # Database 서비스
│   │   │   ├── servlet/
│   │   │   │   ├── HomeServlet.java          # 메인 페이지 Servlet
│   │   │   │   └── RefreshServlet.java       # 시크릿 갱신 Servlet
│   │   │   ├── listener/
│   │   │   │   └── AppContextListener.java   # 애플리케이션 초기화 리스너
│   │   │   └── model/
│   │   │       └── SecretInfo.java           # 시크릿 정보 모델
│   │   ├── resources/
│   │   │   ├── vault.properties               # Vault 설정 파일
│   │   │   └── logback.xml                    # 로깅 설정
│   │   └── webapp/
│   │       ├── WEB-INF/
│   │       │   ├── web.xml                    # 웹 애플리케이션 배포 설정
│   │       │   └── jsp/
│   │       │       └── index.jsp              # 메인 JSP 페이지
│   │       └── css/
│   │           └── style.css                  # 스타일시트
│   └── test/
│       └── java/com/example/vaulttomcat/
│           └── VaultClientTest.java
└── README.md                                   # 사용법 가이드
```

### 주요 컴포넌트
- **AppContextListener**: 애플리케이션 초기화 및 종료 처리
- **VaultClient**: Vault API 클라이언트 (Apache HttpClient + AppRole 인증)
- **TokenRenewalScheduler**: Token 자동 갱신 스케줄러 (10초 간격 체크)
- **DatabaseConfig**: Apache Commons DBCP2 Connection Pool 관리
- **VaultSecretService**: Vault 시크릿 조회 서비스
- **DatabaseService**: Database 연결 및 통계 정보 조회
- **HomeServlet/RefreshServlet**: 웹 요청 처리
- **index.jsp**: 메인 웹 페이지 (JSP + JSTL)

### AppRole 인증 및 Connection Pool 자동 갱신
- **AppRole 인증**: Role ID + Secret ID로 Vault에 안전하게 접근
- **Token Auto-Renewal**: 10초마다 Token 상태 체크, TTL 80% 지점에서 자동 갱신
- **초기화**: Vault Dynamic Secret으로 Connection Pool 생성
- **스케줄링**: TTL의 80% 시점에 자격증명 갱신 스케줄링
- **갱신**: 기존 Pool 종료 → 새 Secret 조회 → 새 Pool 생성
- **반복**: 새로운 TTL로 다음 갱신 스케줄링

## 🛠️ 개발자 가이드

### 1. 프로젝트 구조 이해
```
src/main/java/com/example/vaulttomcat/
├── config/                           # 설정 클래스들
├── client/                           # Vault API 클라이언트
├── service/                          # 비즈니스 로직
├── servlet/                          # 웹 서블릿
├── listener/                         # 애플리케이션 리스너
└── model/                           # 데이터 모델
```

### 2. 주요 기능 구현
- **AppRole 인증**: Role ID + Secret ID를 통한 Vault 인증
- **Token Auto-Renewal**: ScheduledExecutorService를 통한 Token 자동 갱신
- **Vault 연동**: Apache HttpClient를 사용한 Vault API 호출
- **Connection Pool**: Apache Commons DBCP2를 사용한 Database 연결 관리
- **자동 갱신**: ScheduledExecutorService를 통한 자격증명 갱신
- **웹 UI**: JSP + JSTL을 통한 시크릿 정보 표시

### 3. 확장 가능한 구조
- 새로운 시크릿 엔진 추가
- REST API 엔드포인트 추가
- 웹 UI 개선 및 기능 추가

## 🔧 빌드 및 실행

```bash
# Maven 빌드
mvn clean package

# WAR 파일 생성 확인
ls -la target/vault-tomcat-app.war

# Tomcat 배포
cp target/vault-tomcat-app.war $TOMCAT_HOME/webapps/

# Tomcat 시작
$TOMCAT_HOME/bin/startup.sh

# 로그 확인
tail -f $TOMCAT_HOME/logs/catalina.out

# 테스트 실행
mvn test
```

## 🐛 문제 해결

1. **AppRole 인증 실패**: vault.properties의 Role ID와 Secret ID 확인
2. **Token 갱신 실패**: Vault API 권한 및 네트워크 연결 확인
3. **Vault 연결 실패**: vault.properties의 Vault 설정 확인
4. **Database Connection Pool 초기화 실패**: MySQL 설정 및 Vault Dynamic Secret 확인
5. **Tomcat 배포 실패**: Java 버전 및 Tomcat 버전 호환성 확인
6. **JSP 렌더링 실패**: JSTL 의존성 및 설정 확인
7. **자격증명 갱신 실패**: Vault API 권한 및 네트워크 연결 확인

## 📚 참고 자료

- [Vault AppRole 인증](https://developer.hashicorp.com/vault/docs/auth/approle)
- [Vault Token 갱신](https://developer.hashicorp.com/vault/docs/auth/token)
- [Jakarta Servlet 공식 문서](https://jakarta.ee/specifications/servlet/)
- [Jakarta Server Pages 공식 문서](https://jakarta.ee/specifications/pages/)
- [Apache Commons DBCP2](https://commons.apache.org/proper/commons-dbcp/)
- [Apache HttpClient 5](https://hc.apache.org/httpcomponents-client-5.2.x/)
- [Maven 공식 문서](https://maven.apache.org/)
- [Tomcat 10 공식 문서](https://tomcat.apache.org/tomcat-10.0-doc/)
