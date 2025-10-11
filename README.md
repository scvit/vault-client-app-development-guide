# Vault 개발 가이드

## 📖 개요

이 가이드는 Vault를 활용하여 애플리케이션에서 시크릿을 안전하게 관리하는 방법을 다룹니다. 개발자들이 Vault API를 직접 연계하여 시크릿을 동적으로 가져오고, 자동 갱신하는 방법을 개발하기 위한 예시를 제공합니다.

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
└── pure-java-app/                      # Java Vault 클라이언트 예제
    ├── README.md                       # Java 예제 사용법
    ├── src/main/java/com/example/vault/ # Java 소스 코드
    │   ├── VaultApplication.java        # 메인 애플리케이션
    │   ├── client/VaultClient.java      # Vault 클라이언트
    │   └── config/VaultConfig.java      # 설정 관리
    ├── src/main/resources/             # 리소스 파일
    │   ├── config.properties            # 설정 파일
    │   └── logback.xml                  # 로깅 설정
    └── pom.xml                          # Maven 설정
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

### Java 예제 ([pure-java-app/](./pure-java-app/))
- **언어**: Java 11+ (Apache HttpClient + Jackson)
- **특징**:
  - Maven 기반 프로젝트 구조
  - 실시간 TTL 계산, 멀티스레드 갱신
  - 시스템 프로퍼티 오버라이드 지원
- **빌드**: `mvn clean package`
- **실행**: `java -jar target/vault-java-app.jar`

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
cd pure-java-app
mvn clean package
java -jar target/vault-java-app.jar
```
