#!/bin/bash

# =============================================================================
# Database Static 시크릿 조회 스크립트 (Vault Proxy 연계)
# =============================================================================
# 이 스크립트는 Vault Proxy를 통해 Database Static 시크릿을 조회합니다.
# Vault CLI 없이 순수 curl을 사용하여 정적 자격증명을 가져옵니다.
# =============================================================================

# Vault Proxy 설정 (하드코딩)
VAULT_PROXY_ADDR="http://127.0.0.1:8400"
VAULT_NAMESPACE=""  # Vault Namespace (필요시 설정, 예: "my-namespace")
DB_STATIC_PATH="my-vault-app-database/static-creds/db-demo-static"

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

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# jq 설치 확인
check_jq() {
    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq가 설치되지 않았습니다. jq를 설치해주세요."
        log_error "Ubuntu/Debian: sudo apt-get install jq"
        log_error "CentOS/RHEL: sudo yum install jq"
        log_error "macOS: brew install jq"
        exit 1
    fi
}

# Vault Proxy 연결 확인
check_vault_proxy() {
    log_info "Vault Proxy 연결 확인 중... ($VAULT_PROXY_ADDR)"
    
    if ! curl -s -f "$VAULT_PROXY_ADDR/v1/sys/health" >/dev/null 2>&1; then
        log_error "Vault Proxy에 연결할 수 없습니다."
        log_error "Vault Proxy가 실행 중인지 확인하세요: $VAULT_PROXY_ADDR"
        log_error "Vault Proxy 실행: cd vault-proxy-demo && ./start-proxy.sh"
        exit 1
    fi
    
    log_success "Vault Proxy 연결 확인됨"
}

# Database Static 시크릿 조회
get_db_static_secret() {
    log_info "Database Static 시크릿 조회 중... ($DB_STATIC_PATH)"
    
    # curl로 시크릿 조회
    if [ -n "$VAULT_NAMESPACE" ]; then
        RESPONSE=$(curl -s -w "\n%{http_code}" \
            -H "X-Vault-Namespace: $VAULT_NAMESPACE" \
            "$VAULT_PROXY_ADDR/v1/$DB_STATIC_PATH")
    else
        RESPONSE=$(curl -s -w "\n%{http_code}" \
            "$VAULT_PROXY_ADDR/v1/$DB_STATIC_PATH")
    fi
    
    # HTTP 상태 코드와 응답 본문 분리
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')
    
    # HTTP 상태 코드 확인
    if [ "$HTTP_CODE" != "200" ]; then
        log_error "시크릿 조회 실패 (HTTP $HTTP_CODE)"
        echo "응답: $RESPONSE_BODY"
        exit 1
    fi
    
    # JSON 파싱 및 출력
    log_success "Database Static 시크릿 조회 성공!"
    echo ""
    echo "=== Database Static 자격증명 ==="
    
    # 자격증명 정보 출력
    USERNAME=$(echo "$RESPONSE_BODY" | jq -r '.data.username')
    PASSWORD=$(echo "$RESPONSE_BODY" | jq -r '.data.password')
    TTL=$(echo "$RESPONSE_BODY" | jq -r '.ttl')
    
    echo "Username: $USERNAME"
    echo "Password: $PASSWORD"
    echo "TTL: ${TTL}초"
    
    # TTL 정보 계산
    if [ "$TTL" != "null" ] && [ "$TTL" != "0" ]; then
        TTL_HOURS=$((TTL / 3600))
        TTL_MINUTES=$(((TTL % 3600) / 60))
        echo "TTL: ${TTL_HOURS}시간 ${TTL_MINUTES}분"
    fi
    
    echo ""
    echo "=== 사용 예시 ==="
    echo "mysql -h localhost -P 3306 -u $USERNAME -p$PASSWORD"
    echo "또는"
    echo "export DB_USERNAME=\"$USERNAME\""
    echo "export DB_PASSWORD=\"$PASSWORD\""
    
    echo ""
    echo "=== Static vs Dynamic 차이점 ==="
    echo "- Static: 고정된 사용자, 비밀번호 주기적 갱신"
    echo "- Dynamic: 임시 사용자, 자동 만료"
    echo "- Static은 장기간 사용 가능한 자격증명입니다"
    
    echo ""
    echo "=== Vault Proxy 정보 ==="
    echo "Proxy 주소: $VAULT_PROXY_ADDR"
    echo "시크릿 경로: $DB_STATIC_PATH"
}

# 메인 실행
main() {
    echo "============================================================================="
    echo "🔒 Database Static 시크릿 조회 스크립트 (Vault Proxy 연계)"
    echo "============================================================================="
    echo ""
    
    # 1. jq 설치 확인
    check_jq
    
    # 2. Vault Proxy 연결 확인
    check_vault_proxy
    
    # 3. Database Static 시크릿 조회
    get_db_static_secret
    
    echo ""
    echo "============================================================================="
    log_success "Database Static 시크릿 조회 완료!"
    echo "============================================================================="
}

# 스크립트 실행
main "$@"
