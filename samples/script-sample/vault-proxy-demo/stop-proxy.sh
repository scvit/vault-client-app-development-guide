#!/bin/bash

# =============================================================================
# Vault Proxy 중지 스크립트
# =============================================================================
# 이 스크립트는 실행 중인 Vault Proxy를 중지합니다.
# =============================================================================

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정 파일
PID_FILE="./vault-proxy.pid"
LOG_FILE="./vault-proxy.log"
TOKEN_FILE="./token"

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

# Vault Proxy 중지
stop_proxy() {
    if [ ! -f "$PID_FILE" ]; then
        log_warning "PID 파일을 찾을 수 없습니다. Vault Proxy가 실행되지 않았을 수 있습니다."
        return 1
    fi
    
    PID=$(cat "$PID_FILE")
    
    if ! ps -p "$PID" >/dev/null 2>&1; then
        log_warning "PID $PID에 해당하는 프로세스가 실행되지 않습니다."
        rm -f "$PID_FILE"
        return 1
    fi
    
    log_info "Vault Proxy 중지 중... (PID: $PID)"
    
    # 프로세스 종료
    kill "$PID"
    
    # 종료 확인 (최대 10초 대기)
    for i in {1..10}; do
        if ! ps -p "$PID" >/dev/null 2>&1; then
            log_success "Vault Proxy 중지 완료"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
    done
    
    # 강제 종료
    log_warning "일반 종료 실패, 강제 종료 시도 중..."
    kill -9 "$PID" 2>/dev/null
    
    if ! ps -p "$PID" >/dev/null 2>&1; then
        log_success "Vault Proxy 강제 종료 완료"
        rm -f "$PID_FILE"
        return 0
    else
        log_error "Vault Proxy 종료 실패"
        return 1
    fi
}

# 정리 작업
cleanup() {
    log_info "정리 작업 중..."
    
    # PID 파일 정리
    if [ -f "$PID_FILE" ]; then
        rm -f "$PID_FILE"
        log_info "PID 파일 정리 완료"
    fi
    
    # Token 파일 정리 (선택사항)
    if [ -f "$TOKEN_FILE" ]; then
        log_info "Token 파일이 남아있습니다: $TOKEN_FILE"
        log_info "보안을 위해 수동으로 삭제하세요: rm $TOKEN_FILE"
    fi
    
    log_success "정리 작업 완료"
}

# 메인 실행
main() {
    echo "============================================================================="
    echo "🛑 Vault Proxy 중지 스크립트"
    echo "============================================================================="
    echo ""
    
    # 1. Vault Proxy 중지
    if stop_proxy; then
        echo ""
        # 2. 정리 작업
        cleanup
        
        echo ""
        echo "============================================================================="
        log_success "Vault Proxy 중지 완료!"
        echo "============================================================================="
        echo ""
        echo "정리된 파일:"
        echo "- PID 파일: $PID_FILE"
        echo ""
        echo "남은 파일:"
        echo "- 로그 파일: $LOG_FILE (수동 삭제 가능)"
        echo "- Token 파일: $TOKEN_FILE (보안상 수동 삭제 권장)"
    else
        echo ""
        echo "============================================================================="
        log_error "Vault Proxy 중지 실패"
        echo "============================================================================="
        echo ""
        echo "수동으로 확인하세요:"
        echo "1. ps aux | grep vault"
        echo "2. netstat -tlnp | grep 8400"
        exit 1
    fi
}

# 스크립트 실행
main "$@"
