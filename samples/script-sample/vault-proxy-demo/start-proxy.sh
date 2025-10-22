#!/bin/bash

# =============================================================================
# Vault Proxy 실행 스크립트
# =============================================================================
# 이 스크립트는 Vault Proxy를 백그라운드로 실행합니다.
# =============================================================================

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정 파일
CONFIG_FILE="./vault-proxy.hcl"
PID_FILE="./vault-proxy.pid"
LOG_FILE="./vault-proxy.log"

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

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Vault Proxy 실행 상태 확인
check_proxy_status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" >/dev/null 2>&1; then
            log_warning "Vault Proxy가 이미 실행 중입니다 (PID: $PID)"
            return 0
        else
            log_info "PID 파일이 존재하지만 프로세스가 실행되지 않습니다. PID 파일을 정리합니다."
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# Vault Proxy 실행
start_proxy() {
    log_info "Vault Proxy 실행 중... (포트: 8400)"
    
    # 설정 파일 확인
    if [ ! -f "$CONFIG_FILE" ]; then
        log_error "설정 파일을 찾을 수 없습니다: $CONFIG_FILE"
        exit 1
    fi
    
    # Token 파일 확인
    if [ ! -f "./token" ]; then
        log_error "Token 파일을 찾을 수 없습니다. 먼저 ./setup-token.sh를 실행하세요."
        exit 1
    fi
    
    # Vault Proxy 실행 (백그라운드)
    vault proxy -config="$CONFIG_FILE" > "$LOG_FILE" 2>&1 &
    PROXY_PID=$!
    
    # PID 파일 저장
    echo "$PROXY_PID" > "$PID_FILE"
    
    # 잠시 대기하여 실행 확인
    sleep 2
    
    if ps -p "$PROXY_PID" >/dev/null 2>&1; then
        log_success "Vault Proxy 실행 완료 (PID: $PROXY_PID)"
        log_info "로그 파일: $LOG_FILE"
        log_info "PID 파일: $PID_FILE"
    else
        log_error "Vault Proxy 실행 실패"
        log_error "로그를 확인하세요: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
}

# Vault Proxy 상태 확인
verify_proxy() {
    log_info "Vault Proxy 상태 확인 중... (http://127.0.0.1:8400)"
    
    # 잠시 대기
    sleep 3
    
    if curl -s -f "http://127.0.0.1:8400/v1/sys/health" >/dev/null 2>&1; then
        log_success "Vault Proxy 정상 동작 확인됨"
        echo ""
        echo "=== Vault Proxy 정보 ==="
        echo "주소: http://127.0.0.1:8400"
        echo "PID: $(cat $PID_FILE)"
        echo "로그: $LOG_FILE"
        echo ""
        echo "=== 테스트 명령어 ==="
        echo "curl http://127.0.0.1:8400/v1/sys/health"
        echo "curl http://127.0.0.1:8400/v1/my-vault-app-kv/data/database"
    else
        log_error "Vault Proxy 상태 확인 실패"
        log_error "로그를 확인하세요: $LOG_FILE"
        exit 1
    fi
}

# 메인 실행
main() {
    echo "============================================================================="
    echo "🚀 Vault Proxy 실행 스크립트"
    echo "============================================================================="
    echo ""
    
    # 1. 실행 상태 확인
    if check_proxy_status; then
        echo ""
        log_info "Vault Proxy가 이미 실행 중입니다."
        echo "중지하려면: ./stop-proxy.sh"
        exit 0
    fi
    
    # 2. Vault Proxy 실행
    start_proxy
    
    # 3. 상태 확인
    verify_proxy
    
    echo ""
    echo "============================================================================="
    log_success "Vault Proxy 실행 완료!"
    echo "============================================================================="
    echo ""
    echo "다음 단계:"
    echo "1. 스크립트 테스트: ../get_kv_secret.sh (포트 8400으로 수정 필요)"
    echo "2. Proxy 중지: ./stop-proxy.sh"
    echo ""
    echo "주의사항:"
    echo "- Vault Proxy는 백그라운드에서 실행됩니다"
    echo "- 중지하려면 ./stop-proxy.sh를 사용하세요"
}

# 스크립트 실행
main "$@"
