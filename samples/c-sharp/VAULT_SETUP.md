# Vault 개발 서버 설정 안내

이 문서는 `setup-vault-for-my-vault-app.sh` 및 `setup-vault-for-my-vault-app-namespace.sh` 스크립트가 Vault에 어떤 설정을 하는지 설명합니다.

## 스크립트 종류

| 스크립트 | 설명 |
|---------|------|
| `setup-vault-for-my-vault-app.sh` | 기본 설정 (Namespace 없음, Community Edition 호환) |
| `setup-vault-for-my-vault-app-namespace.sh` | Namespace 포함 설정 (Enterprise 환경용) |

두 스크립트는 **Namespace 생성 및 환경변수 설정 여부**만 다르고, 이후 모든 설정 내용은 동일합니다.

## 사전 요구사항

- Vault 개발 서버가 실행 중이어야 합니다.
- Docker가 설치되어 있어야 합니다 (MySQL 컨테이너용).

```bash
# Vault 개발 서버 실행 (별도 터미널에서)  
vault server -dev -dev-root-token-id=root
```

## 스크립트 실행

```bash
# 기본 (Namespace 없음)
./setup-vault-for-my-vault-app.sh

# Namespace 사용 (Enterprise)
./setup-vault-for-my-vault-app-namespace.sh
```

---

## 설정 내용

### 0. Namespace 생성 (Namespace 스크립트만 해당)

Vault **Enterprise** 환경에서는 Namespace를 통해 팀/프로젝트별로 Vault 리소스를 격리할 수 있습니다.
Namespace 스크립트는 가장 먼저 Namespace를 생성하고, 이후 모든 설정을 해당 Namespace 안에서 수행합니다.

```
Namespace 이름: ns1  (스크립트 내 NS_NAME 변수로 변경 가능)
```

동작 방식:
- `vault namespace create ns1` 으로 Namespace 생성
- `export VAULT_NAMESPACE=ns1` 으로 환경변수 설정
- 이후 모든 Vault 명령이 `ns1` Namespace 안에서 실행됨

> Community Edition(OSS)은 Namespace를 지원하지 않습니다. 기본 스크립트를 사용하세요.

---

### 1. AppRole 인증 활성화

AppRole은 애플리케이션이 Vault에 로그인할 때 사용하는 인증 방식입니다.
`Role ID` + `Secret ID` 조합으로 인증하며, 로그인 성공 시 Vault 토큰을 발급받습니다.

```
인증 경로: auth/approle/
```

---

### 2. 정책 생성 (Policy)

`myapp-templated-policy` 정책을 생성합니다.
**Identity 템플릿**을 사용하여 앱 이름(`identity.entity.name`) 기반으로 경로를 동적으로 제한합니다.
하나의 정책으로 여러 앱을 각각 격리된 경로에서 관리할 수 있습니다.

| 경로 | 권한 | 용도 |
|------|------|------|
| `{entity}-kv/data/*` | read, list | KV 시크릿 조회 |
| `{entity}-database/creds/*` | read, create, update | DB Dynamic 자격증명 |
| `{entity}-database/static-creds/*` | read, list | DB Static 자격증명 |
| `auth/token/renew-self` | update | 토큰 갱신 |
| `auth/token/lookup-self` | read | 토큰 정보 조회 |
| `sys/leases/lookup` | update | Lease TTL 확인 |
| `sys/leases/renew` | update | Lease 갱신 |
| `{entity}-ssh-otp/creds/*` | read, create, update | SSH OTP 발급 |
| `{entity}-ssh-ca/sign/*` | read, create, update | SSH 인증서 서명 |

---

### 3. Entity 생성

Vault Identity의 Entity는 애플리케이션의 고유한 신원(Identity)입니다.
정책은 AppRole이 아닌 Entity에 부여되므로, 인증 방식이 바뀌어도 권한이 유지됩니다.

```
Entity 이름: my-vault-app
연결 정책:   myapp-templated-policy
```

---

### 4. AppRole 역할 생성 및 Entity 연결

AppRole 역할을 생성하고, Entity Alias를 통해 위에서 만든 Entity와 연결합니다.
앱이 AppRole로 로그인하면 자동으로 `my-vault-app` Entity로 인식되어 정책이 적용됩니다.

```
AppRole 역할: auth/approle/role/my-vault-app
  - secret_id_ttl: 1시간 (Secret ID 유효기간)
  - period:        1분 (토큰 갱신 주기)
  - token_ttl:     0 (period 사용 시 무제한)
```

> 스크립트 실행 후 출력되는 `Role ID`와 `Secret ID`를 `config.ini`에 설정해야 합니다.

---

### 5. KV v2 시크릿 엔진

키-값 형태의 시크릿을 저장하는 엔진입니다. 버전 관리를 지원합니다.

```
마운트 경로: my-vault-app-kv
```

스크립트가 생성하는 예시 데이터:

| 경로 | 키 |
|------|----|
| `my-vault-app-kv/database` | api_key, database_url, database_username, database_password |
| `my-vault-app-kv/ssh/10.10.0.222` | ssh_username, ssh_password |
| `my-vault-app-kv/ssh/192.168.0.47` | ssh_username, ssh_password |

---

### 6. MySQL 컨테이너 및 Database 시크릿 엔진

Docker로 MySQL 컨테이너를 실행하고, Vault Database 시크릿 엔진을 설정합니다.

**MySQL 컨테이너:**
```
컨테이너 이름: my-vault-app-mysql
포트:          3306
root 비밀번호: password
```

**Database 시크릿 엔진:**
```
마운트 경로:    my-vault-app-database
DB 연결 이름:   mysql-demo
```

**Dynamic Role** — 요청 시마다 임시 DB 계정을 생성합니다.
```
역할 이름: db-demo-dynamic
TTL:       1분 (테스트용, 최대 24시간)
```

**Static Role** — 기존 DB 계정의 비밀번호를 주기적으로 교체합니다.
```
역할 이름:      db-demo-static
관리 계정:      my-vault-app-static
교체 스케줄:    매 정시 (cron: 0 * * * *)
```

---

### 7. SSH OTP 시크릿 엔진

SSH 접속 시 일회용 비밀번호(OTP)를 발급하는 엔진입니다.

```
마운트 경로:    my-vault-app-ssh-otp
역할 이름:      otp-role
허용 사용자:    test1, test2
```

> SSH 타겟 서버에 `vault-ssh-helper`가 설치 및 설정되어 있어야 실제 사용 가능합니다.

---

### 8. SSH CA 시크릿 엔진

Vault가 CA(인증기관) 역할을 하여 SSH 인증서에 서명하는 엔진입니다.

```
마운트 경로:    my-vault-app-ssh-ca
역할 이름:      client-signer
인증서 TTL:     20초 (테스트용)
```

> SSH 타겟 서버의 `sshd_config`에 Vault CA 공개키가 등록되어 있어야 실제 사용 가능합니다.

---

## 설정 완료 후

스크립트 실행이 끝나면 `config.ini`에 발급된 인증 정보를 업데이트해야 합니다.

```bash
# Role ID 확인
vault read -field=role_id auth/approle/role/my-vault-app/role-id

# Secret ID 발급
vault write -field=secret_id -f auth/approle/role/my-vault-app/secret-id
```

`config.ini` 업데이트:
```ini
[vault]
role_id = <위에서 확인한 Role ID>
secret_id = <위에서 발급한 Secret ID>

# Namespace 스크립트를 사용한 경우 namespace 항목도 설정
namespace = ns1

# 기본 스크립트를 사용한 경우 빈값으로 둡니다
namespace =
```

## 주의사항

- 개발 모드 Vault는 **재시작하면 모든 설정이 초기화**됩니다. 재시작 후 스크립트를 다시 실행해야 합니다.
- MySQL 컨테이너는 Vault를 재시작해도 유지됩니다. (`docker start my-vault-app-mysql`)
- 이 설정은 **개발 환경 전용**입니다. 프로덕션 환경에서는 사용하지 마세요.
