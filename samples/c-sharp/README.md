# Vault C# Client Application

[아래 원본 한국어 섹션으로 이동](#vault-c-클라이언트-애플리케이션)

## 📖 Example Purpose and Usage Scenarios

This example is a reference application for Vault integration development.
If needed only for initial application startup, it makes an API call once and then utilizes the cache for subsequent runs to reduce memory usage.
The example is implemented to periodically fetch and renew secrets.
It is designed using the VaultSharp library rather than being implemented as a library solely for Vault.

### 🎯 Key Scenarios
- **Initial Startup**: Fetches secrets from Vault only once when the application starts.
- **Real-time Renewal**: Periodically renews secrets to maintain the latest state.
- **Cache Utilization**: Minimizes unnecessary API calls through version/TTL-based caching.

### 🔐 Supported Secret Types
- **KV v2**: Key-value store (version-based caching).
- **Database Dynamic**: Dynamic database credentials (TTL-based renewal).
- **Database Static**: Static database credentials (time-based caching).

### 💡 Development Considerations
- **Memory Management**: Optimizes memory usage through secret caching.
- **Error Handling**: Handles exceptions such as network errors and authentication failures.
- **Security**: Meets security requirements like token renewal and secret encryption.
- **Performance**: Optimizes performance through async/await and background scheduler pattern.

## 🚀 Quick Start

### 1. Prerequisites
- .NET 10.0 SDK or higher.
- Vault server (with development server setup completed).

### 2. Install Dependencies

Dependencies are automatically downloaded by `dotnet run`. No manual installation is needed.

```bash
# Dependencies are restored automatically on first run
dotnet run
```

### 3. Modify Configuration File
You can change the Vault connection settings by modifying the `config.ini` file.

### 4. Run the Application

```bash
# Build and run (dependencies are restored automatically)
dotnet run
```

## 📋 Example Output

```
🚀 Vault C# 클라이언트 애플리케이션 시작
✅ Vault 로그인 성공
⚙️ 현재 설정:
- Entity: my-vault-app
- Vault URL: http://127.0.0.1:8200
- Namespace: (없음)
- KV Enabled: true
- Database Dynamic Enabled: true
- Database Static Enabled: true

📖 예제 목적 및 사용 시나리오
이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.
애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.
예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.

🔧 지원 기능:
- KV v2 시크릿 엔진 (버전 기반 캐싱)
- Database Dynamic 시크릿 엔진 (TTL 기반 갱신)
- Database Static 시크릿 엔진 (시간 기반 캐싱)
- 자동 토큰 갱신
- Entity 기반 권한 관리
- Vault Namespace 지원

🔄 시크릿 갱신 시작... (Ctrl+C로 종료)

=== KV Secret Refresh ===
✅ KV 시크릿 조회 성공
📦 KV Secret Data:
{
  "api_key": "myapp-api-key-123456",
  "database_url": "mysql://localhost:3306/mydb"
}

=== Database Dynamic Secret Refresh ===
✅ Database Dynamic 시크릿 조회 성공 (TTL: 60초)
🗄️ Database Dynamic Secret (TTL: 60초):
  username: v-approle-db-demo-dy-JRHTDBobE5o
  password: qLteLnVHZdBcmR-sJS1b

=== Database Static Secret Refresh ===
✅ Database Static 시크릿 조회 성공 (TTL: 3600초)
🔒 Database Static Secret (TTL: 3600초):
  username: my-vault-app-static
  password: OfK6S-6R2PiWA0C8Fqxj
```

## ⚙️ Configuration Options

### Configuration Priority

Generally, predefined configurations are defined in the `config.ini` file. Since the `secret_id` used for Vault authentication expires after issuance, it is implemented to be overridden by an environment variable at runtime.

1. **Environment Variables** - Highest priority
2. **config.ini file** - Default value

### How to Use Environment Variables
```bash
# Override settings with environment variables at runtime
export VAULT_ROLE_ID=your-role-id
export VAULT_SECRET_ID=your-secret-id
export VAULT_URL=http://your-vault-server:8200
dotnet run

# Or individual settings
export VAULT_SECRET_ID=3ee5080b-c9b3-2714-799c-f8d45a715625
dotnet run
```

### Vault Server Settings
```ini
[vault]
# Entity name (required)
entity = my-vault-app
# Vault server address
url = http://127.0.0.1:8200
# Vault namespace (optional) - Leave empty for Community Edition
namespace =
# AppRole authentication info (required)
role_id = 7fb49dd0-4b87-19cd-7b72-a7e21e5c543e
secret_id = 475a6500-f9f8-fdd4-ec30-54fadcad926e
```

### Secret Engine Settings
```ini
[kv_secret]
# KV Secret settings
enabled = true
path = database
refresh_interval = 5

[database_dynamic]
# Database Dynamic Secret settings
enabled = true
role_id = db-demo-dynamic

[database_static]
# Database Static Secret settings
enabled = true
role_id = db-demo-static
```

### HTTP Settings
```ini
[http]
# HTTP request timeout (seconds)
timeout = 30
# Maximum response size (bytes)
max_response_size = 10485760
```

## 🏗️ Architecture

### File Structure
```
csharp-app/
├── README.md                      # Usage guide
├── VaultCsharpApp.csproj          # .NET project file (NuGet dependencies)
├── config.ini                     # Configuration file
├── Program.cs                     # Application entry point
├── VaultApplication.cs            # Main application logic and schedulers
├── VaultClient.cs                 # Vault API client, secret retrieval, caching
└── ConfigLoader.cs                # INI file parser and environment variable override
```

### Key Components
- **VaultApplication**: Main application logic, background scheduler management.
- **ConfigLoader**: Loads and manages INI configuration with environment variable override.
- **VaultClient**: Vault API integration via VaultSharp, secret retrieval, caching.

### Caching Strategy
- **KV v2**: Version-based caching (5-minute interval).
- **Database Dynamic**: TTL-based caching (10-second threshold).
- **Database Static**: Time-based caching (5-minute interval).

### Real-time TTL Calculation
- Displays the real-time decrease of the TTL for Database Dynamic/Static Secrets.
- Calculates the remaining TTL by subtracting the elapsed time from the cached TTL.
- Prevents negative values with `Math.Max(0, remainingTtl)`.

### Async/Await Pattern
- Background schedulers run as independent `Task` instances using `async/await`.
- `CancellationToken` is used to gracefully stop all schedulers on `Ctrl+C`.
- Each scheduler (KV, Dynamic, Static) runs concurrently without blocking each other.

## 🛠️ Developer Guide

### 1. Project Structure
```
csharp-app/
├── Program.cs                     # Entry point
├── VaultApplication.cs            # Main application
├── VaultClient.cs                 # Vault client
├── ConfigLoader.cs                # Configuration class
└── config.ini                     # Configuration file
```

### 2. Key Features
- **Authentication**: AppRole-based Vault authentication.
- **Token Management**: Automatic token renewal.
- **Secret Retrieval**: Fetches KV, Database Dynamic, and Static secrets.
- **Caching**: Efficient secret caching strategy.
- **TTL Management**: Real-time TTL calculation and display.
- **Namespace Support**: Vault Enterprise namespace support (leave empty for Community Edition).

### 3. Extensible Structure
- Add new secret engines.
- Implement custom caching strategies.
- Enhance monitoring and logging.

## 🔧 Build and Run

```bash
# Run the application (dependencies are restored automatically)
dotnet run

# Build only (without running)
dotnet build
```

## 🐛 Troubleshooting

1. **Vault Connection Failure**: Check URL and namespace settings in `config.ini`.
2. **Authentication Failure**: Check Role ID and Secret ID.
3. **Permission Error**: Check Entity policies in Vault.
4. **Secret Retrieval Failure**: Check if the secret engine is enabled and the mount path matches.
5. **Database Dynamic Hanging**: Vault requires a live database connection to create dynamic credentials. Ensure the database is running and the Vault database role is configured.
   ```bash
   # Check if Vault can reach the database
   vault read <mount-path>/config/<connection-name>
   ```
6. **.NET SDK Not Found**:
   ```bash
   # Install .NET SDK from https://dotnet.microsoft.com/download
   dotnet --version
   ```

## 📚 References

- [Vault API Documentation](https://developer.hashicorp.com/vault/api-docs)
- [AppRole Auth Method](https://developer.hashicorp.com/vault/docs/auth/approle)
- [KV v2 Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2)
- [Database Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/databases)
- [VaultSharp .NET Library](https://github.com/rajanadar/VaultSharp)
- [.NET SDK Download](https://dotnet.microsoft.com/download)

---

# Vault C# 클라이언트 애플리케이션

## 📖 예제 목적 및 사용 시나리오

이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.
애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.
예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.
Vault만을 위한 라이브러리로 구현하지 않고 VaultSharp 라이브러리를 사용하는 방식으로 설계되었습니다.

### 🎯 주요 시나리오
- **초기 구동**: 애플리케이션 시작 시 Vault에서 시크릿을 한 번만 조회
- **실시간 갱신**: 주기적으로 시크릿을 갱신하여 최신 상태 유지
- **캐시 활용**: 버전/TTL 기반 캐싱으로 불필요한 API 호출 최소화

### 🔐 지원 시크릿 타입
- **KV v2**: 키-값 저장소 (버전 기반 캐싱)
- **Database Dynamic**: 동적 데이터베이스 자격증명 (TTL 기반 갱신)
- **Database Static**: 정적 데이터베이스 자격증명 (시간 기반 캐싱)

### 💡 개발 고려사항
- **메모리 관리**: 시크릿 캐싱으로 메모리 사용량 최적화
- **오류 처리**: 네트워크 오류, 인증 실패 등 예외 상황 처리
- **보안**: 토큰 갱신, 시크릿 암호화 등 보안 요구사항 충족
- **성능**: async/await 및 백그라운드 스케줄러 패턴으로 성능 최적화

## 🚀 빠른 시작

### 1. 사전 요구사항
- .NET 10.0 SDK 이상
- Vault 서버 (개발 서버 설정 완료)

### 2. 의존성 설치

`dotnet run` 실행 시 NuGet 패키지가 자동으로 다운로드됩니다. 별도 설치 과정이 필요 없습니다.

```bash
# 첫 실행 시 의존성이 자동으로 복원됩니다
dotnet run
```

### 3. 설정 파일 수정
`config.ini` 파일을 수정하여 Vault 연결 설정을 변경할 수 있습니다.

### 4. 애플리케이션 실행

```bash
# 빌드 및 실행 (의존성 자동 복원)
dotnet run
```

## 📋 출력 예제

```
🚀 Vault C# 클라이언트 애플리케이션 시작
✅ Vault 로그인 성공
⚙️ 현재 설정:
- Entity: my-vault-app
- Vault URL: http://127.0.0.1:8200
- Namespace: (없음)
- KV Enabled: true
- Database Dynamic Enabled: true
- Database Static Enabled: true

📖 예제 목적 및 사용 시나리오
이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.
애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.
예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.

🔧 지원 기능:
- KV v2 시크릿 엔진 (버전 기반 캐싱)
- Database Dynamic 시크릿 엔진 (TTL 기반 갱신)
- Database Static 시크릿 엔진 (시간 기반 캐싱)
- 자동 토큰 갱신
- Entity 기반 권한 관리
- Vault Namespace 지원

🔄 시크릿 갱신 시작... (Ctrl+C로 종료)

=== KV Secret Refresh ===
✅ KV 시크릿 조회 성공
📦 KV Secret Data:
{
  "api_key": "myapp-api-key-123456",
  "database_url": "mysql://localhost:3306/mydb"
}

=== Database Dynamic Secret Refresh ===
✅ Database Dynamic 시크릿 조회 성공 (TTL: 60초)
🗄️ Database Dynamic Secret (TTL: 60초):
  username: v-approle-db-demo-dy-JRHTDBobE5o
  password: qLteLnVHZdBcmR-sJS1b

=== Database Static Secret Refresh ===
✅ Database Static 시크릿 조회 성공 (TTL: 3600초)
🔒 Database Static Secret (TTL: 3600초):
  username: my-vault-app-static
  password: OfK6S-6R2PiWA0C8Fqxj
```

## ⚙️ 설정 옵션

### 설정 우선순위

일반적으로 사전 정의되는 구성은 `config.ini` 파일에 정의됩니다. Vault 인증을 위해 사용되는 `secret_id`의 경우 발급 이후 만료되므로, 실행 시 환경변수로 오버라이드 할 수 있도록 구현되었습니다.

1. **환경변수** - 최우선
2. **config.ini 파일** - 기본값

### 환경변수 사용법
```bash
# 실행 시 환경변수로 설정 오버라이드
export VAULT_ROLE_ID=your-role-id
export VAULT_SECRET_ID=your-secret-id
export VAULT_URL=http://your-vault-server:8200
dotnet run

# 또는 개별 설정
export VAULT_SECRET_ID=3ee5080b-c9b3-2714-799c-f8d45a715625
dotnet run
```

### Vault 서버 설정
```ini
[vault]
# Entity 이름 (필수)
entity = my-vault-app
# Vault 서버 주소
url = http://127.0.0.1:8200
# Vault 네임스페이스 (선택사항) - Enterprise 환경에서만 사용, 없으면 빈값으로 둡니다
namespace =
# AppRole 인증 정보 (필수)
role_id = 7fb49dd0-4b87-19cd-7b72-a7e21e5c543e
secret_id = 475a6500-f9f8-fdd4-ec30-54fadcad926e
```

### 시크릿 엔진 설정
```ini
[kv_secret]
# KV Secret 설정
enabled = true
path = database
refresh_interval = 5

[database_dynamic]
# Database Dynamic Secret 설정
enabled = true
role_id = db-demo-dynamic

[database_static]
# Database Static Secret 설정
enabled = true
role_id = db-demo-static
```

### HTTP 설정
```ini
[http]
# HTTP 요청 타임아웃 (초)
timeout = 30
# 최대 응답 크기 (바이트)
max_response_size = 10485760
```

## 🏗️ 아키텍처

### 파일 구조
```
csharp-app/
├── README.md                      # 사용법 가이드
├── VaultCsharpApp.csproj          # .NET 프로젝트 파일 (NuGet 의존성)
├── config.ini                     # 설정 파일
├── Program.cs                     # 애플리케이션 진입점
├── VaultApplication.cs            # 메인 애플리케이션 로직 및 스케줄러
├── VaultClient.cs                 # Vault API 클라이언트, 시크릿 조회, 캐싱
└── ConfigLoader.cs                # INI 파일 파싱 및 환경변수 오버라이드
```

### 주요 컴포넌트
- **VaultApplication**: 메인 애플리케이션 로직, 백그라운드 스케줄러 관리
- **ConfigLoader**: INI 설정 파일 로드 및 환경변수 오버라이드 처리
- **VaultClient**: VaultSharp를 통한 Vault API 연동, 시크릿 조회, 캐싱

### 캐싱 전략
- **KV v2**: 버전 기반 캐싱 (5분 간격)
- **Database Dynamic**: TTL 기반 캐싱 (10초 임계값)
- **Database Static**: 시간 기반 캐싱 (5분 간격)

### 실시간 TTL 계산
- Database Dynamic/Static Secret의 TTL이 실시간으로 감소하는 것을 표시
- 캐시된 TTL에서 경과 시간을 빼서 현재 남은 TTL 계산
- `Math.Max(0, remainingTtl)`로 음수 방지

### Async/Await 패턴
- 백그라운드 스케줄러는 `async/await`를 사용한 독립적인 `Task`로 실행
- `CancellationToken`을 통해 `Ctrl+C` 시 모든 스케줄러를 안전하게 종료
- KV, Dynamic, Static 각 스케줄러가 서로 블로킹 없이 동시에 실행

## 🛠️ 개발자 가이드

### 1. 프로젝트 구조 이해
```
csharp-app/
├── Program.cs                     # 진입점
├── VaultApplication.cs            # 메인 애플리케이션
├── VaultClient.cs                 # Vault 클라이언트
├── ConfigLoader.cs                # 설정 클래스
└── config.ini                     # 설정 파일
```

### 2. 주요 기능 구현
- **인증**: AppRole 기반 Vault 인증
- **토큰 관리**: 자동 토큰 갱신
- **시크릿 조회**: KV, Database Dynamic/Static 시크릿 조회
- **캐싱**: 효율적인 시크릿 캐싱 전략
- **TTL 관리**: 실시간 TTL 계산 및 표시
- **네임스페이스**: Vault Enterprise 네임스페이스 지원 (Community Edition은 빈값)

### 3. 확장 가능한 구조
- 새로운 시크릿 엔진 추가
- 커스텀 캐싱 전략 구현
- 모니터링 및 로깅 강화

## 🔧 빌드 및 실행

```bash
# 애플리케이션 실행 (의존성 자동 복원)
dotnet run

# 빌드만 수행 (실행 없이)
dotnet build
```

## 🐛 문제 해결

1. **Vault 연결 실패**: `config.ini`의 URL 및 네임스페이스 설정 확인
2. **인증 실패**: Role ID, Secret ID 확인
3. **권한 오류**: Vault의 Entity 정책 확인
4. **시크릿 조회 실패**: 시크릿 엔진 활성화 여부 및 마운트 경로 확인
5. **Database Dynamic 응답 없음**: Vault가 DB에 접속해야 동적 자격증명을 생성합니다. DB가 실행 중인지, Vault에 DB 역할이 설정되어 있는지 확인하세요.
   ```bash
   # Vault에서 DB 연결 설정 확인
   vault read <mount-path>/config/<connection-name>
   ```
6. **.NET SDK 없음**:
   ```bash
   # https://dotnet.microsoft.com/download 에서 설치 후 확인
   dotnet --version
   ```

## 📚 참고 자료

- [Vault API 문서](https://developer.hashicorp.com/vault/api-docs)
- [AppRole 인증 방법](https://developer.hashicorp.com/vault/docs/auth/approle)
- [KV v2 시크릿 엔진](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2)
- [Database 시크릿 엔진](https://developer.hashicorp.com/vault/docs/secrets/databases)
- [VaultSharp .NET 라이브러리](https://github.com/rajanadar/VaultSharp)
- [.NET SDK 다운로드](https://dotnet.microsoft.com/download)
