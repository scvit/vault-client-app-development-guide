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

#### 4. `get_ssh_kv_proxy.sh` - SSH KV 자격증명 조회 (Proxy)
- **용도**: Vault Proxy를 통한 KV 기반 SSH 자격증명 조회 및 연결
- **경로**: `my-vault-app-kv/ssh/<호스트IP>`
- **출력**: SSH 자격증명 정보, 자동 연결 (sshpass 사용)
- **인자**: 호스트IP (선택사항, 기본값: 10.10.0.222)

#### 5. `get_ssh_otp_proxy.sh` - SSH OTP 생성 (Proxy)
- **용도**: Vault Proxy를 통한 SSH OTP 생성 및 수동 연결 안내
- **경로**: `my-vault-app-ssh-otp/creds/otp-role`
- **출력**: 일회용 OTP 키, 사용자명, 호스트 정보, SSH 연결 명령어

#### 6. `get_ssh_signed_cert_proxy.sh` - SSH Signed Certificate 생성 (Proxy)
- **용도**: Vault Proxy를 통한 SSH Signed Certificate 생성 및 저장
- **경로**: `my-vault-app-ssh-ca/sign/client-signer`
- **출력**: 서명된 SSH 인증서, 시리얼 번호, SSH 연결 명령어
- **사전 요구사항**: SSH 서버에 CA Public Key 등록 필요

### AWS 자격증명 조회 스크립트

#### 7. `get_aws_userpass.sh` - AWS 자격증명 조회
- **용도**: AWS 자격증명 조회 및 credentials 파일 업데이트
- **인증**: Userpass 방식
- **출력**: AWS Access Key, Secret Key, Session Token

#### 8. `get_aws_oidc.sh` - AWS 자격증명 조회
- **용도**: AWS 자격증명 조회 및 credentials 파일 업데이트
- **인증**: OIDC 방식
- **출력**: AWS Access Key, Secret Key, Session Token

### Vault Proxy 데모 환경

#### 9. `vault-proxy-demo/` - Vault Proxy 데모 환경
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

# SSH KV 자격증명 조회 및 연결
./get_ssh_kv_proxy.sh                    # 기본 호스트 사용
./get_ssh_kv_proxy.sh 192.168.0.47       # 특정 호스트 지정

# SSH OTP 생성 (수동 연결)
./get_ssh_otp_proxy.sh

# SSH Signed Certificate 생성
./get_ssh_signed_cert_proxy.sh

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

### SSH 연결 실패
```bash
# SSH 서버 설정 확인
ssh -v user@host  # 상세 로그로 연결 과정 확인

# SSH 서버에서 패스워드 인증 활성화
sudo vim /etc/ssh/sshd_config
# PasswordAuthentication yes 추가
sudo systemctl restart sshd

# Vault SSH Helper 설치 확인
vault ssh -help
```

## SSH 설정 주의사항

### SSH OTP
- OTP는 일회용이며 단 한 번만 사용 가능
- 타겟 SSH 서버에 Vault SSH Helper가 설치되어 있어야 함
- SSH 서버에서 패스워드 인증이 활성화되어 있어야 함
- CIDR 제한으로 보안 강화 가능
- 스크립트 실행 전 `SSH_USERNAME`과 `SSH_HOST` 설정 필요
- **수동 연결**: 스크립트에서 제공하는 SSH 명령어와 패스워드를 수동으로 입력

#### SSH 서버 설정 요구사항
```bash
# SSH 서버에서 패스워드 인증 활성화
sudo vim /etc/ssh/sshd_config
# 다음 설정 추가/수정:
PasswordAuthentication yes
PubkeyAuthentication yes
ChallengeResponseAuthentication yes
UsePAM yes

# SSH 서비스 재시작
sudo systemctl restart sshd

# Vault SSH Helper 설치 (SSH 서버에서)
vault ssh -help  # Vault CLI가 설치되어 있어야 함
```

### SSH Signed Certificate
- 타겟 SSH 서버에 CA Public Key가 등록되어 있어야 함
- Certificate TTL은 20초 (SSH CA Role 설정에 따라 제한됨)
- 기존 SSH 키를 재사용 가능
- 스크립트 실행 전 `SSH_USERNAME` 설정 필요
- SSH 키가 없는 경우 `ssh-keygen -t rsa -C "test@rocky" -f ~/.ssh/vault_rsa` 실행

### SSH KV 자격증명
- KV에 저장된 정적 자격증명 사용
- sshpass 설치 권장 (자동 연결)
- 초기 적용 단계에서 사용, 이후 SSH OTP/Signed Certificate로 전환 권장

#### SSH 서버 사전 설정 (한 번만 수행)
```bash
# SSH 서버에서 CA Public Key 등록
vault read -field=public_key my-vault-app-ssh-ca/config/ca | sudo tee /etc/ssh/trusted-user-ca-keys.pem
echo 'TrustedUserCAKeys /etc/ssh/trusted-user-ca-keys.pem' | sudo tee -a /etc/ssh/sshd_config
sudo systemctl restart sshd
```

## SSH 자동 연결 대안 방법 (고급 사용자용)

### sshpass 사용 (권장)
```bash
# sshpass 설치
# Ubuntu/Debian: sudo apt-get install sshpass
# CentOS/RHEL: sudo yum install sshpass
# macOS: brew install sshpass

# 사용법
sshpass -p "password" ssh user@host
```

### expect 사용
```bash
# expect 설치
# Ubuntu/Debian: sudo apt-get install expect
# CentOS/RHEL: sudo yum install expect
# macOS: brew install expect

# expect 스크립트 예제
expect << EOF
spawn ssh user@host
expect "password:"
send "password\r"
interact
EOF
```

**참고**: 예제 스크립트는 수동 연결을 기본으로 하며, 자동 연결이 필요한 경우 위의 방법을 사용할 수 있습니다.

### SSH 연결 방법 비교

| 방법 | 장점 | 단점 | 의존성 |
|------|------|------|--------|
| **수동 연결** | 의존성 없음, 안전함 | 사용자 개입 필요 | 없음 |
| **sshpass** | 간단하고 안정적 | 별도 설치 필요 | sshpass |
| **expect** | 대화형 세션 완벽 지원 | 복잡한 스크립트 | expect |

**권장**: 기본적으로 수동 연결을 사용하고, 자동화가 필요한 경우 `sshpass`를 사용하세요.
