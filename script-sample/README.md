# Vault Proxy 연계 스크립트 예시

이 디렉토리는 Vault Proxy와 연계하여 curl을 사용해 시크릿을 조회하는 스크립트 예시들을 포함합니다.

## 📋 스크립트 목록

### Vault Proxy 연계 스크립트 (권장)

#### 1. `get_kv_secret_proxy.sh` - KV 시크릿 조회 (Proxy)
- **용도**: Vault Proxy를 통한 정적 키-값 시크릿 조회
- **경로**: `my-vault-app-kv/data/database`
- **출력**: API 키, 데이터베이스 URL, 메타데이터

#### 2. `get_db_dynamic_secret_proxy.sh` - Database Dynamic 시크릿 조회 (Proxy)
- **용도**: Vault Proxy를 통한 동적 데이터베이스 자격증명 조회
- **경로**: `my-vault-app-database/creds/db-demo-dynamic`
- **출력**: 임시 사용자명, 비밀번호, Lease 정보

#### 3. `get_db_static_secret_proxy.sh` - Database Static 시크릿 조회 (Proxy)
- **용도**: Vault Proxy를 통한 정적 데이터베이스 자격증명 조회
- **경로**: `my-vault-app-database/static-creds/db-demo-static`
- **출력**: 고정 사용자명, 비밀번호, TTL 정보

### AWS 자격증명 조회 스크립트

#### 4. `get_aws_userpass.sh` - AWS 자격증명 조회
- **용도**: AWS 자격증명 조회 및 credentials 파일 업데이트
- **인증**: Userpass 방식
- **출력**: AWS Access Key, Secret Key, Session Token

#### 5. `get_aws_oidc.sh` - AWS 자격증명 조회
- **용도**: AWS 자격증명 조회 및 credentials 파일 업데이트
- **인증**: OIDC 방식
- **출력**: AWS Access Key, Secret Key, Session Token

### Vault Proxy 데모 환경

#### 6. `vault-proxy-demo/` - Vault Proxy 데모 환경
- **용도**: Vault Proxy 실행 및 관리
- **포함**: 설정 파일, 실행/중지 스크립트, 사용법
- **특징**: Root token 사용, 포트 8400

## 🚀 사용법

### Vault Proxy 연계 (권장)

#### 사전 준비사항
1. **Vault 서버 실행**: Vault 개발 서버가 `http://127.0.0.1:8200`에서 실행 중이어야 합니다
2. **Vault 서버 설정**: `setup-vault-for-my-vault-app.sh`로 Vault 서버가 설정되어 있어야 합니다
3. **jq 설치**: JSON 파싱을 위해 jq가 설치되어 있어야 합니다

#### Vault Proxy 설정 및 실행
```bash
# Vault Proxy 데모 환경으로 이동
cd vault-proxy-demo

# Token 파일 생성
./setup-token.sh

# Vault Proxy 실행
./start-proxy.sh
```

#### 스크립트 실행
```bash
# 상위 디렉토리로 이동
cd ..

# KV 시크릿 조회
./get_kv_secret_proxy.sh

# Database Dynamic 시크릿 조회
./get_db_dynamic_secret_proxy.sh

# Database Static 시크릿 조회
./get_db_static_secret_proxy.sh
```

#### Vault Proxy 중지
```bash
cd vault-proxy-demo
./stop-proxy.sh
```

### AWS 자격증명 조회
```bash
# AWS 자격증명 조회 (Userpass 인증)
./get_aws_userpass.sh

# AWS 자격증명 조회 (OIDC 인증)
./get_aws_oidc.sh
```

## 🔧 설정 정보

### Vault 서버 주소
- **직접 연계**: `http://127.0.0.1:8200`
- **Proxy 연계**: `http://127.0.0.1:8400`

### 시크릿 경로
- **KV**: `my-vault-app-kv/data/database`
- **Database Dynamic**: `my-vault-app-database/creds/db-demo-dynamic`
- **Database Static**: `my-vault-app-database/static-creds/db-demo-static`

### 포트 구성
- **Vault 서버**: 8200 (직접 연계용)
- **Vault Proxy**: 8400 (Proxy 연계용)
- **충돌 방지**: 다른 포트 사용 가능

## 📝 특징

### 공통 특징
- **Vault CLI 불필요**: 순수 curl 사용
- **Vault Proxy 연계**: 이미 인증된 Proxy 사용
- **JSON 파싱**: jq를 사용한 구조화된 출력
- **색상 출력**: 가독성 향상을 위한 컬러 로그
- **에러 처리**: HTTP 상태 코드 기반 간단한 에러 처리

### KV 시크릿 스크립트
- 시크릿 데이터와 메타데이터 출력
- 버전 정보 및 생성 시간 표시

### Database Dynamic 스크립트
- 임시 자격증명 정보 출력
- Lease ID 및 TTL 정보 제공
- MySQL 연결 예시 명령어 제공

### Database Static 스크립트
- 고정 자격증명 정보 출력
- TTL 정보 및 Static vs Dynamic 차이점 설명
- MySQL 연결 예시 명령어 제공

## ⚠️ 주의사항

1. **Vault Proxy 인증**: 스크립트는 Vault Proxy가 이미 인증되어 있다고 가정합니다
2. **네트워크 연결**: Vault Proxy에 대한 네트워크 연결이 필요합니다
3. **jq 의존성**: JSON 파싱을 위해 jq가 설치되어 있어야 합니다
4. **권한**: Vault Proxy가 해당 시크릿 경로에 접근할 수 있는 권한이 있어야 합니다

## 🔍 문제 해결

### Vault Proxy 연결 실패
```bash
# Vault Proxy 상태 확인
curl -s http://127.0.0.1:8400/v1/sys/health
```

### jq 설치
```bash
# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq

# macOS
brew install jq
```

### 권한 오류
Vault Proxy가 해당 시크릿에 접근할 수 있는 권한이 있는지 확인하세요.
