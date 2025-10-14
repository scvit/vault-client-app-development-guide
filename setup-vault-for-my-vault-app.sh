#!/bin/bash

# =============================================================================
# Vault 개발 서버 자동 설정 스크립트
# =============================================================================
# 이 스크립트는 03.Vault개발서버.md의 모든 설정을 자동화합니다.
# 
# 사용법:
#   ./setup-vault-for-my-vault-app.sh
#
# 주의사항:
#   - Vault가 이미 실행 중이어야 합니다 (vault server -dev)
#   - Docker가 설치되어 있어야 합니다 (MySQL 컨테이너용)
#   - vault CLI가 PATH에 있어야 합니다
# =============================================================================

set -e  # 오류 발생 시 스크립트 종료

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수들
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Vault 연결 확인
check_vault_connection() {
    log_info "Vault 연결 확인 중..."
    
    if ! vault status > /dev/null 2>&1; then
        log_error "Vault 서버에 연결할 수 없습니다."
        log_error "다음 명령어로 Vault 개발 서버를 먼저 실행하세요:"
        log_error "  vault server -dev -dev-root-token-id=\"root\""
        exit 1
    fi
    
    log_success "Vault 서버 연결 확인됨"
}

# 환경변수 설정
setup_environment() {
    log_info "Vault 환경변수 설정 중..."
    
    export VAULT_ADDR='http://127.0.0.1:8200'
    export VAULT_TOKEN='root'
    
    log_success "환경변수 설정 완료"
}

# AppRole 인증 활성화
enable_approle() {
    log_info "AppRole 인증 활성화 중..."
    
    if vault auth list | grep -q "approle/"; then
        log_warning "AppRole이 이미 활성화되어 있습니다."
    else
        vault auth enable approle
        log_success "AppRole 인증 활성화 완료"
    fi
}

# Entity 기반 정책 생성
create_policy() {
    log_info "Entity 기반 템플릿 정책 생성 중..."
    
    vault policy write myapp-templated-policy - <<EOF
# 앱별 전용 시크릿 경로 (identity.entity.name 기반)
path "{{identity.entity.name}}-kv/data/*" {
  capabilities = ["read", "list"]
}

path "{{identity.entity.name}}-database/creds/*" {
  capabilities = ["read", "list", "create", "update"]
}

path "{{identity.entity.name}}-database/static-creds/*" {
  capabilities = ["read", "list"]
}

# 토큰 갱신 권한
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# 토큰 조회 권한
path "auth/token/lookup-self" {
  capabilities = ["read"]
}

# Lease 조회 권한 (Database Dynamic 시크릿 TTL 확인용)
path "sys/leases/lookup" {
  capabilities = ["update"]
}

# Lease 갱신 권한 (Database Dynamic 시크릿 갱신용)
path "sys/leases/renew" {
  capabilities = ["update"]
}
EOF
    
    log_success "정책 생성 완료"
}

# Entity 생성
create_entity() {
    log_info "Entity 생성 중..."
    
    # Entity 생성
    vault write identity/entity name="my-vault-app" policies="myapp-templated-policy"
    
    # Entity ID 추출
    ENTITY_ID=$(vault read -field=id /identity/entity/name/my-vault-app)
    echo "ENTITY_ID=$ENTITY_ID" > .vault-setup.env
    
    log_success "Entity 생성 완료 (ID: $ENTITY_ID)"
}

# AppRole 설정
setup_approle() {
    log_info "AppRole 설정 중..."
    
    # AppRole Accessor ID 추출
    APPROLE_ACCESSOR=$(vault read -field=accessor sys/auth/approle)
    echo "APPROLE_ACCESSOR=$APPROLE_ACCESSOR" >> .vault-setup.env
    
    # AppRole 생성
    vault write auth/approle/role/my-vault-app \
        secret_id_ttl=1h \
        token_ttl=0 \
        token_max_ttl=0 \
        period=1m \
        orphan=true
    
    log_success "AppRole 설정 완료"

    ROLE_ID=$(vault read -field=role_id auth/approle/role/my-vault-app/role-id)
    log_info "Role ID: $ROLE_ID"
    echo "ROLE_ID=$ROLE_ID" >> .vault-setup.env
}

# Entity Alias 생성 (AppRole과 Entity 연결)
create_entity_alias() {
    log_info "Entity Alias 생성 중..."
    
    # 환경변수 로드
    source .vault-setup.env
    
    # Entity Alias 생성
    vault write identity/entity-alias \
        name="$ROLE_ID" \
        canonical_id="$ENTITY_ID" \
        mount_accessor="$APPROLE_ACCESSOR"
    
    log_success "Entity Alias 생성 완료"
}

# KV 시크릿 엔진 활성화
enable_kv_secrets() {
    log_info "KV 시크릿 엔진 활성화 중..."
    
    if vault secrets list | grep -q "my-vault-app-kv/"; then
        log_warning "KV 엔진이 이미 활성화되어 있습니다."
    else
        vault secrets enable -path=my-vault-app-kv kv-v2
        log_success "KV 엔진 활성화 완료"
    fi
    
    # 예시 시크릿 데이터 생성
    log_info "예시 시크릿 데이터 생성 중..."
    vault kv put my-vault-app-kv/database \
        api_key="myapp-api-key-123456" \
        database_url="mysql://localhost:3306/mydb"
    
    log_success "KV 시크릿 데이터 생성 완료"
}

# MySQL 컨테이너 실행
setup_mysql() {
    log_info "MySQL 컨테이너 설정 중..."
    
    # 기존 컨테이너가 있는지 확인
    if docker ps -a | grep -q "my-vault-app-mysql"; then
        log_warning "MySQL 컨테이너가 이미 존재합니다."
        if ! docker ps | grep -q "my-vault-app-mysql"; then
            log_info "MySQL 컨테이너 시작 중..."
            docker start my-vault-app-mysql
        fi
    else
        log_info "MySQL 컨테이너 생성 중..."
        docker run --name my-vault-app-mysql -e MYSQL_ROOT_PASSWORD=password -d -p 3306:3306 mysql:9
        
        # MySQL이 시작될 때까지 대기
        log_info "MySQL 시작 대기 중..."
        sleep 10
    fi
    
    # MySQL 데이터베이스 및 사용자 생성
    log_info "MySQL 데이터베이스 및 사용자 생성 중..."
    mysql -u root -ppassword -h 127.0.0.1 -P 3306 --protocol=TCP -e "
        CREATE DATABASE IF NOT EXISTS mydb;
        CREATE USER IF NOT EXISTS 'my-vault-app-static'@'%' IDENTIFIED BY 'password';
        GRANT ALL PRIVILEGES ON *.* TO 'my-vault-app-static'@'%';
        FLUSH PRIVILEGES;
    " 2>/dev/null || {
        log_warning "MySQL 데이터베이스 및 사용자 생성 실패 (MySQL이 아직 시작되지 않았을 수 있습니다)"
        log_info "수동으로 다음 명령어를 실행하세요:"
        log_info "mysql -u root -ppassword -h 127.0.0.1 -P 3306 --protocol=TCP -e \"CREATE DATABASE IF NOT EXISTS mydb; CREATE USER 'my-vault-app-static'@'%' IDENTIFIED BY 'password'; GRANT ALL PRIVILEGES ON *.* TO 'my-vault-app-static'@'%'; FLUSH PRIVILEGES;\""
    }
    
    log_success "MySQL 설정 완료"
}

# Database 시크릿 엔진 활성화
enable_database_secrets() {
    log_info "Database 시크릿 엔진 활성화 중..."
    
    if vault secrets list | grep -q "my-vault-app-database/"; then
        log_warning "Database 엔진이 이미 활성화되어 있습니다."
    else
        vault secrets enable -path=my-vault-app-database database
        log_success "Database 엔진 활성화 완료"
    fi
    
    # Database 시크릿 엔진 설정
    log_info "Database 시크릿 엔진 설정 중..."
    vault write my-vault-app-database/config/mysql-demo \
        plugin_name=mysql-database-plugin \
        connection_url="{{username}}:{{password}}@tcp(localhost:3306)/" \
        allowed_roles="*" \
        username="root" \
        password="password"
    
    # Dynamic Role 생성 (테스트를 위해 1분)
    vault write my-vault-app-database/roles/db-demo-dynamic \
        db_name=mysql-demo \
        creation_statements="CREATE USER '{{username}}'@'%' IDENTIFIED BY '{{password}}'; GRANT ALL PRIVILEGES ON *.* TO '{{username}}'@'%';" \
        default_ttl="1m" \
        max_ttl="24h"
    
    # Static Role 생성
    vault write my-vault-app-database/static-roles/db-demo-static \
        db_name=mysql-demo \
        username=my-vault-app-static \
        rotation_schedule="0 * * * *"
    
    log_success "Database 시크릿 엔진 설정 완료"
}

# 설정 검증
verify_setup() {
    log_info "설정 검증 중..."
    
    echo ""
    log_info "=== Vault 상태 확인 ==="
    vault status
    
    echo ""
    log_info "=== 인증 방법 확인 ==="
    vault auth list
    
    echo ""
    log_info "=== 시크릿 엔진 확인 ==="
    vault secrets list
    
    echo ""
    log_info "=== 정책 확인 ==="
    vault policy list
    
    echo ""
    log_info "=== Entity 확인 ==="
    vault list identity/entity/name 2>/dev/null || vault read identity/entity/name/my-vault-app
    
    echo ""
    log_info "=== AppRole 설정 확인 ==="
    vault read auth/approle/role/my-vault-app
    
    echo ""
    log_info "=== KV 시크릿 테스트 ==="
    vault kv get my-vault-app-kv/database
    
    echo ""
    log_info "=== Database Dynamic Role 테스트 ==="
    vault read my-vault-app-database/creds/db-demo-dynamic
    
    echo ""
    log_info "=== Database Static Role 테스트 ==="
    vault read my-vault-app-database/static-creds/db-demo-static
    
    log_success "설정 검증 완료"
}

# config.ini 업데이트 안내
update_config_ini() {
    log_info "config.ini 파일 업데이트 안내..."

    echo ""
    log_info "=== config.ini 파일 업데이트 필요 ==="
    echo ""
    echo "다음 명령어를 실행하여 config.ini 파일을 업데이트하세요:"
    echo ""
    echo "  # Role ID 확인"
    echo "  vault read -field=role_id auth/approle/role/my-vault-app/role-id"
    echo ""
    echo "  # Secret ID 확인"
    echo "  vault write -field=secret_id -f auth/approle/role/my-vault-app/secret-id"
    echo ""
    echo "또는 수동으로 다음 값들을 config.ini에 설정하세요:"
    echo "  role_id = <출력된 Role ID>"
    echo "  secret_id = <출력된 Secret ID>"
    echo ""
    log_success "config.ini 업데이트 안내 완료"
}

# 정리 함수
cleanup() {
    log_info "임시 파일 정리 중..."
    rm -f .vault-setup.env
    log_success "정리 완료"
}

# 메인 실행 함수
main() {
    echo "============================================================================="
    echo "🏗️  Vault 개발 서버 자동 설정 스크립트"
    echo "============================================================================="
    echo ""
    
    # 1. Vault 연결 확인
    check_vault_connection
    
    # 2. 환경변수 설정
    setup_environment
    
    # 3. AppRole 인증 활성화
    enable_approle
    
    # 4. Entity 기반 정책 생성
    create_policy
    
    # 5. Entity 생성
    create_entity
    
    # 6. AppRole 설정
    setup_approle
    
    # 7. Entity Alias 생성
    create_entity_alias
    
    # 8. KV 시크릿 엔진 활성화
    enable_kv_secrets
    
    # 9. MySQL 설정 (선택사항)
    if command -v docker >/dev/null 2>&1; then
        setup_mysql
        enable_database_secrets
    else
        log_warning "Docker가 설치되지 않았습니다. Database 시크릿 엔진을 건너뜁니다."
    fi
    
    # 10. 설정 검증
    verify_setup
    
    # 11. config.ini 업데이트
    update_config_ini
    
    # 12. 정리
    cleanup
    
    echo ""
    echo "============================================================================="
    log_success "🎉 Vault 개발 서버 설정이 완료되었습니다!"
    echo "============================================================================="
    echo ""
    echo "다음 단계:"
    echo "1. config.ini 파일 업데이트 (위의 안내 명령어 실행)"
    echo "2. 예제 애플리케이션 실행:"
    echo "   - C 애플리케이션: cd c-app && make && ./vault-app"
    echo "   - C++ 애플리케이션: cd cpp-app && mkdir build && cd build && cmake .. && make && ./vault-app"
    echo "   - Java 애플리케이션: cd java-pure-app && mvn clean package && java -jar target/vault-java-app.jar"
    echo "   - Python 애플리케이션: cd python-app && python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt && python vault_app.py"
    echo "   - Spring Boot 웹 애플리케이션: cd java-web-springboot-app && ./gradlew bootRun"
    echo "     (웹 브라우저에서 http://localhost:8080/vault-web 접속)"
    echo ""
    echo "주의사항:"
    echo "- 이 설정은 개발 환경 전용입니다"
    echo "- Vault 서버를 재시작하면 모든 데이터가 사라집니다"
    echo "- MySQL 컨테이너는 수동으로 중지해야 합니다: docker stop my-vault-app-mysql"
    echo "- 여러 앱을 생성할 때는 각 앱의 config.ini를 개별적으로 업데이트해야 합니다"
    echo ""
}

# 스크립트 실행
main "$@"
