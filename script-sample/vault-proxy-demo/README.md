# Vault Proxy 데모 환경

이 디렉토리는 Vault Proxy를 사용한 데모 환경을 구성합니다. Vault Proxy는 Vault 서버와 클라이언트 사이에서 중간 역할을 하며, 복잡한 인증 과정을 대신 처리해줍니다.

## 📋 개요

### Vault Proxy란?
- **역할**: Vault 서버와 클라이언트 사이의 중간 서버
- **장점**: 클라이언트에서 복잡한 인증 과정 불필요
- **동작**: Proxy가 Vault에 인증하고, 클라이언트는 Proxy에만 요청

### 데모 환경 특징
- **Root Token 사용**: 데모 목적으로 root token 사용
- **포트**: 8400 (Vault 서버: 8200)
- **인증**: Token 파일 기반 자동 인증
- **캐싱**: 시크릿 캐싱으로 성능 향상

## 🚀 빠른 시작

### 1. 사전 준비사항
```bash
# Vault 개발 서버 실행 (다른 터미널에서)
vault server -dev -dev-root-token-id="root"

# Vault 서버 설정 (다른 터미널에서)
cd /path/to/Development-Guide
./setup-vault-for-my-vault-app.sh
```

### 2. Vault Proxy 설정
```bash
# Token 파일 생성
./setup-token.sh

# Vault Proxy 실행
./start-proxy.sh
```

### 3. 테스트
```bash
# Vault Proxy 상태 확인
curl http://127.0.0.1:8400/v1/sys/health

# KV 시크릿 조회 (포트 8400으로 수정된 스크립트 사용)
../get_kv_secret_proxy.sh
```

### 4. 정리
```bash
# Vault Proxy 중지
./stop-proxy.sh
```

## 📁 파일 구조

```
vault-proxy-demo/
├── vault-proxy.hcl       # Vault Proxy 설정 파일
├── setup-token.sh        # Token 파일 생성 스크립트
├── start-proxy.sh        # Proxy 실행 스크립트
├── stop-proxy.sh         # Proxy 중지 스크립트
├── README.md             # 이 파일
├── token                 # Root token 파일 (자동 생성)
├── vault-proxy.pid       # PID 파일 (자동 생성)
└── vault-proxy.log       # 로그 파일 (자동 생성)
```

## 🔧 설정 파일 설명

### vault-proxy.hcl
```hcl
# 리스너 설정 (포트 8400)
listener "tcp" {
  address = "127.0.0.1:8400"
  tls_disable = true
}

# Vault 서버 연결 (포트 8200)
vault {
  address = "http://127.0.0.1:8200"
}

# 자동 인증 (Token 파일 사용)
auto_auth {
  method "token" {
    config = {
      token_file_path = "./token"
    }
  }
}

# 캐시 설정
cache {
  use_auto_auth_token = true
}
```

## 🛠️ 스크립트 사용법

### setup-token.sh
- **용도**: Root token을 파일로 저장
- **실행**: `./setup-token.sh`
- **결과**: `token` 파일 생성

### start-proxy.sh
- **용도**: Vault Proxy 백그라운드 실행
- **실행**: `./start-proxy.sh`
- **결과**: Proxy가 포트 8400에서 실행

### stop-proxy.sh
- **용도**: Vault Proxy 중지 및 정리
- **실행**: `./stop-proxy.sh`
- **결과**: Proxy 프로세스 종료, 파일 정리

## 🔍 테스트 방법

### 1. 기본 연결 테스트
```bash
# Vault Proxy 상태 확인
curl http://127.0.0.1:8400/v1/sys/health

# Vault 서버 직접 연결 (비교용)
curl http://127.0.0.1:8200/v1/sys/health
```

### 2. 시크릿 조회 테스트
```bash
# KV 시크릿 조회
curl http://127.0.0.1:8400/v1/my-vault-app-kv/data/database

# Database Dynamic 시크릿 조회
curl http://127.0.0.1:8400/v1/my-vault-app-database/creds/db-demo-dynamic

# Database Static 시크릿 조회
curl http://127.0.0.1:8400/v1/my-vault-app-database/static-creds/db-demo-static
```

### 3. 스크립트 테스트
```bash
# 기존 스크립트를 Proxy용으로 수정하여 테스트
# (포트 8200 → 8400으로 변경)
```

## ⚠️ 주의사항

### 보안 고려사항
- **Root Token**: 데모 목적으로만 사용
- **Token 파일**: 보안에 주의 (chmod 600)
- **운영 환경**: 적절한 인증 방법 사용 필요

### 포트 충돌
- **Vault 서버**: 8200
- **Vault Proxy**: 8400
- **충돌 방지**: 다른 포트 사용 가능

### 파일 정리
```bash
# 수동 정리 (필요시)
rm -f token vault-proxy.pid vault-proxy.log
```

## 🔧 문제 해결

### Vault Proxy 실행 실패
```bash
# 로그 확인
cat vault-proxy.log

# 포트 사용 확인
netstat -tlnp | grep 8400

# 프로세스 확인
ps aux | grep vault
```

### 연결 실패
```bash
# Vault 서버 상태 확인
curl http://127.0.0.1:8200/v1/sys/health

# Token 유효성 확인
vault token lookup
```

### 권한 문제
```bash
# Token 파일 권한 확인
ls -la token

# 권한 수정
chmod 600 token
```

## 📚 추가 정보

### Vault Proxy vs 직접 연결
- **직접 연결**: 복잡한 인증, 토큰 관리 필요
- **Proxy 사용**: 간단한 HTTP 요청만으로 시크릿 조회

### 캐싱 효과
- **성능**: 시크릿 캐싱으로 응답 속도 향상
- **부하**: Vault 서버 부하 감소
- **안정성**: 네트워크 문제 시 캐시된 데이터 사용

### 운영 환경 고려사항
- **인증**: AppRole, LDAP 등 적절한 인증 방법 사용
- **보안**: TLS 암호화, 방화벽 설정
- **모니터링**: 로그 수집, 메트릭 수집
- **고가용성**: 다중 Proxy, 로드 밸런싱
