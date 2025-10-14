# 🔧 C++ Vault 클라이언트 애플리케이션

`vault-app`은 C++로 구현된 Vault 클라이언트 애플리케이션입니다. KV, Database Dynamic, Database Static 시크릿 엔진을 지원하며, 실시간 시크릿 갱신과 캐싱 기능을 제공합니다.

- 예제에서는 KV, Database Dynamic, Database Static 시크릿을 이용하는 예제를 제공합니다.
- 애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.
- 예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.

## ✨ 주요 기능

- **🔐 다중 시크릿 엔진 지원**: KV v2, Database Dynamic, Database Static
- **⚡ 실시간 갱신**: 백그라운드 스레드를 통한 자동 시크릿 갱신
- **💾 효율적 캐싱**: 버전 기반 KV 캐싱, TTL 기반 Database 캐싱
- **🔄 자동 토큰 갱신**: 4/5 지점에서 자동 토큰 갱신
- **📊 메타데이터 표시**: 버전, TTL 등 유용한 정보 제공
- **🛡️ 보안**: Entity 기반 권한 관리 및 안전한 메모리 처리
- **🚀 모던 C++**: C++17 표준, RAII 패턴, 스마트 포인터 사용

## 🏗️ 프로젝트 구조

```
cpp-app/
├── CMakeLists.txt                 # CMake 빌드 설정
├── config.ini                     # 애플리케이션 설정 파일
├── README.md                      # 이 파일
├── include/
│   ├── Config.hpp                 # 설정 관리 클래스
│   ├── VaultClient.hpp           # Vault 클라이언트 클래스
│   └── HttpClient.hpp            # HTTP 클라이언트 래퍼
├── src/
│   ├── main.cpp                   # 메인 애플리케이션
│   ├── Config.cpp                 # 설정 구현
│   ├── VaultClient.cpp            # Vault 클라이언트 구현
│   └── HttpClient.cpp             # HTTP 클라이언트 구현
└── third_party/
    └── json.hpp                   # nlohmann/json (단일 헤더)
```

## 🚀 빠른 시작

### 1. 필수 라이브러리 설치

**Ubuntu/Debian**:
```bash
sudo apt-get update
sudo apt-get install -y build-essential cmake libcurl4-openssl-dev
```

**CentOS/RHEL**:
```bash
sudo yum install gcc-c++ cmake libcurl-devel
```

**macOS (Homebrew)**:
```bash
brew install cmake curl
```

### 2. 빌드 및 실행

```bash
# 빌드 디렉토리 생성
mkdir build && cd build

# CMake 설정
cmake ..

# 빌드
make

# 실행
./vault-app

# 설정 파일 지정 실행
./vault-app ../config.ini

# WSL에서 실행
wsl -e bash -c "cd /mnt/d/workspaces/vault-client-app-development-guide/cpp-app/build && ./vault-app ../config.ini"
```

### 3. 설정 파일 구성

`config.ini` 파일을 수정하여 Vault 연결 정보를 설정합니다:

```ini
[vault]
entity = my-vault-app
url = http://127.0.0.1:8200
namespace = 
role_id = your-role-id-here
secret_id = your-secret-id-here

[secret-kv]
enabled = true
kv_path = database
refresh_interval = 5

[secret-database-dynamic]
enabled = true
role_id = db-demo-dynamic

[secret-database-static]
enabled = true
role_id = db-demo-static

[http]
timeout = 30
max_response_size = 4096
```

## 📋 출력 예시

```
=== Vault C++ Client Application ===
Loading configuration from: config.ini
=== Application Configuration ===
Vault URL: http://127.0.0.1:8200
Vault Namespace: (empty)
Entity: my-vault-app
Vault Role ID: 7fb49dd0-4b87-19cd-7b72-a7e21e5c543e
Vault Secret ID: 475a6500-f9f8-fdd4-ec30-54fadcad926e

--- Secret Engines ---
KV Engine: enabled
  KV Path: database
  Refresh Interval: 5 seconds
Database Dynamic: enabled
  Role ID: db-demo-dynamic
Database Static: enabled
  Role ID: db-demo-static

--- HTTP Settings ---
HTTP Timeout: 30 seconds
Max Response Size: 4096 bytes
=====================================
Logging in to Vault...
Token TTL from Vault: 60 seconds
Login successful. Token expires in 60 seconds
Token status: 60 seconds remaining (expires in 1 minutes)
✅ Token is healthy (at 0% of TTL)
✅ KV refresh thread started (interval: 5 seconds)
✅ Database Dynamic refresh thread started (interval: 5 seconds)
✅ Database Static refresh thread started (interval: 10 seconds)

=== Fetching Secret ===
📦 KV Secret Data (version: 10):
{ "api_key": "myapp-api-key-123456", "database_url": "mysql://localhost:3306/mydb" }

🗄️ Database Dynamic Secret (TTL: 59 seconds):
  username: v-approle-db-demo-dy-0x50Hgcj5Mj
  password: AdCNFYg6wDV6p8fz-byK

🔒 Database Static Secret (TTL: 2412 seconds):
  username: my-vault-app-static
  password: sntZ-lhR2rZ9GLjgGvry

--- Token Status ---
Token status: 60 seconds remaining (expires in 1 minutes)
✅ Token is healthy (at 0% of TTL)
```

## 🔧 설정 옵션

### Vault 설정 (`[vault]`)
- `entity`: Entity 이름 (필수)
- `url`: Vault 서버 주소
- `namespace`: Vault 네임스페이스 (선택사항)
- `role_id`: AppRole Role ID
- `secret_id`: AppRole Secret ID

### KV 시크릿 설정 (`[secret-kv]`)
- `enabled`: KV 엔진 활성화 여부
- `kv_path`: KV 시크릿 경로
- `refresh_interval`: 갱신 간격 (초)

### Database Dynamic 설정 (`[secret-database-dynamic]`)
- `enabled`: Database Dynamic 엔진 활성화 여부
- `role_id`: Database Dynamic Role ID

### Database Static 설정 (`[secret-database-static]`)
- `enabled`: Database Static 엔진 활성화 여부
- `role_id`: Database Static Role ID

### HTTP 설정 (`[http]`)
- `timeout`: HTTP 요청 타임아웃 (초)
- `max_response_size`: 최대 응답 크기 (바이트)

## 🏗️ 아키텍처

### 스레드 구조
- **메인 스레드**: 시크릿 조회 및 출력
- **토큰 갱신 스레드**: 10초마다 토큰 상태 확인, 4/5 지점에서 갱신
- **KV 갱신 스레드**: 설정된 간격마다 KV 시크릿 갱신
- **Database Dynamic 갱신 스레드**: 설정된 간격마다 Dynamic 시크릿 갱신
- **Database Static 갱신 스레드**: 2배 간격으로 Static 시크릿 갱신

### 캐싱 전략
- **KV 시크릿**: 버전 기반 캐싱 (버전 변경 시에만 갱신)
- **Database Dynamic**: TTL 기반 캐싱 (10초 이하 시 갱신)
- **Database Static**: 시간 기반 캐싱 (5분마다 갱신)

### 보안 기능
- **Entity 기반 권한**: `{entity}-{engine}` 경로 패턴 사용
- **자동 토큰 갱신**: 토큰 만료 전 자동 갱신
- **메모리 보안**: 스마트 포인터로 자동 메모리 관리
- **에러 처리**: 네트워크 오류, 토큰 만료 시 재시도

## 🔍 개발자 가이드

### 핵심 구현 포인트

**1. 메모리 관리**
```cpp
// ✅ 올바른 방법: 스마트 포인터 사용
std::unique_ptr<HttpClient> http_client_;
std::shared_ptr<nlohmann::json> cached_secret_;

// ✅ RAII 패턴으로 자동 리소스 정리
class HttpClient {
    ~HttpClient() {
        if (curl_) {
            curl_easy_cleanup(curl_);
        }
    }
};
```

**2. 토큰 갱신 로직**
```cpp
// 4/5 지점에서 갱신 (토큰 TTL의 80% 경과 시)
auto total_ttl = get_duration_seconds(token_issued_, token_expiry_);
auto elapsed = get_duration_seconds(token_issued_, now());
auto renewal_point = total_ttl * 4 / 5;
if (elapsed >= renewal_point) {
    renew_token();
}
```

**3. 스레드 안전성**
```cpp
// 뮤텍스로 동기화
mutable std::mutex token_mutex_;
std::lock_guard<std::mutex> lock(token_mutex_);

// 원자적 변수로 종료 플래그 관리
std::atomic<bool> should_exit{false};
```

### 주요 클래스

**ConfigLoader 클래스**
- INI 파일 파싱
- 설정 검증
- 기본값 관리

**HttpClient 클래스**
- libcurl의 C++ 래퍼
- RAII 패턴으로 리소스 관리
- GET/POST 메서드 지원

**VaultClient 클래스**
- AppRole 인증
- 토큰 자동 갱신
- 시크릿 캐싱 및 갱신
- 스레드 안전 메서드

## 🐛 문제 해결

### 빌드 오류
- **라이브러리 누락**: `sudo apt-get install libcurl4-openssl-dev` (Ubuntu)
- **CMake 버전**: CMake 3.10 이상 필요
- **컴파일러**: C++17 지원 컴파일러 필요 (GCC 7+, Clang 5+)

### 실행 오류
- **Vault 연결 실패**: Vault 서버 상태 및 URL 확인
- **인증 실패**: Role ID, Secret ID 유효성 확인
- **권한 오류**: Entity 정책 및 경로 권한 확인

### 성능 최적화
- **메모리 사용량**: 불필요한 시크릿 갱신 방지
- **네트워크 호출**: 캐싱 전략 최적화
- **스레드 관리**: 적절한 갱신 간격 설정

## 📚 참고 자료

- [Vault API 문서](https://developer.hashicorp.com/vault/api-docs)
- [Database Secrets Engine](https://developer.hashicorp.com/vault/api-docs/secret/databases)
- [KV Secrets Engine](https://developer.hashicorp.com/vault/api-docs/secret/kv)
- [AppRole Auth Method](https://developer.hashicorp.com/vault/api-docs/auth/approle)
- [nlohmann/json 라이브러리](https://github.com/nlohmann/json)
- [libcurl 문서](https://curl.se/libcurl/)

## 🔄 C 버전과의 차이점

### 개선사항
1. **타입 안전성**: C++ 타입 시스템 활용
2. **메모리 관리**: 스마트 포인터로 자동 정리
3. **에러 처리**: 예외 처리 추가
4. **코드 가독성**: 클래스 기반 구조
5. **빌드 시스템**: CMake로 의존성 관리 개선

### 유지사항
1. **동일한 기능**: KV, Database Dynamic/Static 지원
2. **동일한 설정**: config.ini 형식 유지
3. **동일한 동작**: 토큰 갱신, 캐싱 전략 동일
4. **동일한 출력**: 사용자 경험 일관성
