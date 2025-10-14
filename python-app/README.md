# Vault Python 클라이언트 애플리케이션

## 📖 예제 목적 및 사용 시나리오

이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.
애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.
예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.
Vault만을 위한 라이브러리로 구현하지 않고 hvac 라이브러리를 사용하는 방식으로 설계되었습니다.

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
- **성능**: 비동기 처리, 연결 풀링 등 성능 최적화

## 🚀 빠른 시작

### 1. 사전 요구사항
- Python 3.7 이상
- pip (Python 패키지 관리자)
- Vault 서버 (개발 서버 설정 완료)

### 2. 가상환경 생성 및 의존성 설치

```bash
# Python 가상환경 생성
python3 -m venv venv

# 가상환경 활성화
source venv/bin/activate

# Python 패키지 설치
pip install -r requirements.txt
```

**macOS 사용자 주의사항**: 
macOS에서는 시스템 Python 환경에 패키지를 직접 설치할 수 없습니다. 반드시 가상환경을 사용하세요.

### 3. 설정 파일 수정
`config.ini` 파일을 수정하여 Vault 연결 설정을 변경할 수 있습니다.

### 4. 애플리케이션 실행

```bash
# 가상환경이 활성화된 상태에서 실행
python vault_app.py

# 또는 직접 실행
python3 vault_app.py
```

### 5. 가상환경 비활성화

```bash
# 작업 완료 후 가상환경 비활성화
deactivate
```

## 📋 출력 예제

```
🚀 Vault Python 클라이언트 애플리케이션 시작
✅ Vault 로그인 성공
⚙️ 현재 설정:
- Entity: my-vault-app
- Vault URL: http://127.0.0.1:8200
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

일반적으로 사전 정의되는 구성은 `config.ini` 파일에 정의됩니다. Vault 인증을 위해 사용되는 `secret_id`의 경우 발급 시간 이후 만료가되므로, 실행 시 적용할 수 있도록 환경변수로 오버라이드 할 수 있도록 구현되었습니다.

1. **환경변수** - 최우선
2. **config.ini 파일** - 기본값

### 환경변수 사용법
```bash
# 실행 시 환경변수로 설정 오버라이드
export VAULT_ROLE_ID=your-role-id
export VAULT_SECRET_ID=your-secret-id
export VAULT_URL=http://your-vault-server:8200
python vault_app.py

# 또는 개별 설정
export VAULT_SECRET_ID=3ee5080b-c9b3-2714-799c-f8d45a715625
python vault_app.py
```

### Vault 서버 설정
```ini
[vault]
# Entity 이름 (필수)
entity = my-vault-app
# Vault 서버 주소
url = http://127.0.0.1:8200
# Vault 네임스페이스 (선택사항)
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
max_response_size = 4096
```

## 🏗️ 아키텍처

### 파일 구조
```
python-app/
├── README.md                      # 사용법 가이드
├── requirements.txt               # Python 패키지 의존성
├── config.ini                     # 설정 파일
├── vault_app.py                   # 메인 애플리케이션
├── vault_client.py                # Vault 클라이언트 클래스
└── config_loader.py               # 설정 로더
```

### 주요 컴포넌트
- **VaultApplication**: 메인 애플리케이션 로직, 스케줄러 관리
- **VaultConfig**: 설정 파일 로드 및 관리
- **VaultClient**: Vault API 연동, 시크릿 조회, 캐싱

### 캐싱 전략
- **KV v2**: 버전 기반 캐싱 (5분 간격)
- **Database Dynamic**: TTL 기반 캐싱 (10초 임계값)
- **Database Static**: 시간 기반 캐싱 (5분 간격)

### 실시간 TTL 계산
- Database Dynamic/Static Secret의 TTL이 실시간으로 감소하는 것을 표시
- 캐시된 TTL에서 경과 시간을 빼서 현재 남은 TTL 계산
- `max(0, remaining_ttl)`로 음수 방지

## 🛠️ 개발자 가이드

### 1. 프로젝트 구조 이해
```
python-app/
├── vault_app.py                   # 메인 애플리케이션
├── vault_client.py                # Vault 클라이언트
├── config_loader.py               # 설정 클래스
└── config.ini                     # 설정 파일
```

### 2. 주요 기능 구현
- **인증**: AppRole 기반 Vault 인증
- **토큰 관리**: 자동 토큰 갱신
- **시크릿 조회**: KV, Database Dynamic/Static 시크릿 조회
- **캐싱**: 효율적인 시크릿 캐싱 전략
- **TTL 관리**: 실시간 TTL 계산 및 표시

### 3. 확장 가능한 구조
- 새로운 시크릿 엔진 추가
- 커스텀 캐싱 전략 구현
- 모니터링 및 로깅 강화

## 🔧 빌드 및 실행

```bash
# 가상환경 생성 및 활성화
python3 -m venv venv
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt

# 애플리케이션 실행
python vault_app.py

# 가상환경 비활성화
deactivate
```

## 🐛 문제 해결

1. **Vault 연결 실패**: URL, 네임스페이스 확인
2. **인증 실패**: Role ID, Secret ID 확인
3. **권한 오류**: Entity 정책 확인
4. **시크릿 조회 실패**: 시크릿 엔진 활성화 확인
5. **패키지 설치 실패**: 가상환경 사용 확인
   ```bash
   # macOS에서 패키지 설치 오류 시
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   ```

## 📚 참고 자료

- [Vault API 문서](https://www.vaultproject.io/api-docs)
- [AppRole 인증 방법](https://www.vaultproject.io/docs/auth/approle)
- [KV v2 시크릿 엔진](https://www.vaultproject.io/docs/secrets/kv/kv-v2)
- [Database 시크릿 엔진](https://www.vaultproject.io/docs/secrets/databases)
- [hvac Python 라이브러리](https://hvac.readthedocs.io/)
