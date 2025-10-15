#!/bin/bash

# Vault Proxy를 통한 SSH OTP 생성 스크립트

VAULT_PROXY_ADDR="http://127.0.0.1:8400"
SSH_OTP_PATH="my-vault-app-ssh-otp/creds/otp-role"

# 사용자 설정 (수정 필요)
SSH_USERNAME="test1"
SSH_HOST="10.10.0.222"

echo "=== Vault Proxy를 통한 SSH OTP 생성 ==="
echo "Vault Proxy: $VAULT_PROXY_ADDR"
echo "SSH OTP Path: $SSH_OTP_PATH"
echo "Target: $SSH_USERNAME@$SSH_HOST"
echo ""

# SSH OTP 생성
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "{\"ip\":\"$SSH_HOST\",\"username\":\"$SSH_USERNAME\"}" \
    "$VAULT_PROXY_ADDR/v1/$SSH_OTP_PATH")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ SSH OTP 생성 성공"
    echo ""
    
    # OTP 키 추출
    OTP_KEY=$(echo "$RESPONSE_BODY" | jq -r '.data.key')
    OTP_IP=$(echo "$RESPONSE_BODY" | jq -r '.data.ip')
    OTP_USERNAME=$(echo "$RESPONSE_BODY" | jq -r '.data.username')
    OTP_PORT=$(echo "$RESPONSE_BODY" | jq -r '.data.port // "22"')
    
    echo "📋 SSH OTP 정보:"
    echo "  - OTP Key: $OTP_KEY"
    echo "  - Username: $OTP_USERNAME"
    echo "  - Host: $OTP_IP"
    echo "  - Port: $OTP_PORT"
    echo ""
    echo "🔐 SSH 연결 방법:"
    echo "  ssh $OTP_USERNAME@$OTP_IP"
    echo "  Password: $OTP_KEY"
    echo ""
    echo "⚠️  주의: OTP는 일회용이며 단 한 번만 사용할 수 있습니다."
else
    echo "❌ SSH OTP 생성 실패 (HTTP $HTTP_CODE)"
    echo "$RESPONSE_BODY" | jq -r '.errors[]' 2>/dev/null || echo "$RESPONSE_BODY"
    exit 1
fi

# 동작 테스트
# sshpass -p "0fb11fc4-2cdf-17af-860d-40ccd633e6cd" ssh test@146.56.162.165 -p 22 \
#     -o StrictHostKeyChecking=no \
#     -o UserKnownHostsFile=/dev/null \
#     -o ConnectTimeout=5