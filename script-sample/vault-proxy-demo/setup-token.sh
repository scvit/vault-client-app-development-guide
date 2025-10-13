#!/bin/bash

# =============================================================================
# Vault Proxy Token 설정 스크립트
# =============================================================================
# 이 스크립트는 Vault Proxy에서 사용할 root token을 파일로 저장합니다.
# 데모 목적으로만 사용하세요.
# =============================================================================

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

# Vault 서버 설정
VAULT_ADDR="http://127.0.0.1:8200"
VAULT_TOKEN="root"
TOKEN_FILE="./token"

# Vault 연결 확인
check_vault_connection() {
    log_info "Vault 서버 연결 확인 중... ($VAULT_ADDR)"
    
    export VAULT_ADDR="$VAULT_ADDR"
    export VAULT_TOKEN="$VAULT_TOKEN"
    
    if ! vault status >/dev/null 2>&1; then
        log_error "Vault 서버에 연결할 수 없습니다."
        log_error "Vault 개발 서버가 실행 중인지 확인하세요:"
        log_error "  vault server -dev -dev-root-token-id=\"root\""
        exit 1
    fi
    
    log_success "Vault 서버 연결 확인됨"
}

# Token 파일 생성
create_token_file() {
    log_info "Token 파일 생성 중... ($TOKEN_FILE)"
    
    # Token 파일 생성
    echo "$VAULT_TOKEN" > "$TOKEN_FILE"
    
    # 파일 권한 설정 (소유자만 읽기/쓰기)
    chmod 600 "$TOKEN_FILE"
    
    log_success "Token 파일 생성 완료"
}

# Token 유효성 확인
verify_token() {
    log_info "Token 유효성 확인 중..."
    
    export VAULT_ADDR="$VAULT_ADDR"
    export VAULT_TOKEN="$(cat $TOKEN_FILE)"
    
    if vault token lookup >/dev/null 2>&1; then
        log_success "Token 유효성 확인됨"
    else
        log_error "Token이 유효하지 않습니다."
        exit 1
    fi
}

# 메인 실행
main() {
    echo "============================================================================="
    echo "🔑 Vault Proxy Token 설정 스크립트"
    echo "============================================================================="
    echo ""
    
    # 1. Vault 연결 확인
    check_vault_connection
    
    # 2. Token 파일 생성
    create_token_file
    
    # 3. Token 유효성 확인
    verify_token
    
    echo ""
    echo "============================================================================="
    log_success "Token 설정 완료!"
    echo "============================================================================="
    echo ""
    echo "다음 단계:"
    echo "1. Vault Proxy 실행: ./start-proxy.sh"
    echo "2. 스크립트 테스트: ../get_kv_secret.sh (포트 8400으로 수정 필요)"
    echo ""
    echo "주의사항:"
    echo "- 이 설정은 데모 목적으로만 사용하세요"
    echo "- Token 파일은 보안에 주의하세요"
}

# 스크립트 실행
main "$@"
