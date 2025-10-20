#!/bin/bash

# Vault Proxy를 통한 SSH KV 자격증명 조회 및 연결 스크립트

VAULT_PROXY_ADDR="http://127.0.0.1:8400"
VAULT_NAMESPACE=""  # Vault Namespace (필요시 설정, 예: "my-namespace")
DEFAULT_SSH_HOST="10.10.0.222"
SSH_HOST="${1:-$DEFAULT_SSH_HOST}"
KV_PATH="my-vault-app-kv/data/ssh/$SSH_HOST"

echo "=== Vault Proxy를 통한 SSH KV 자격증명 조회 ==="
echo "Vault Proxy: $VAULT_PROXY_ADDR"
echo "KV Path: $KV_PATH"
echo "Target Host: $SSH_HOST"
echo ""

# KV에서 SSH 자격증명 조회
if [ -n "$VAULT_NAMESPACE" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "X-Vault-Namespace: $VAULT_NAMESPACE" \
        "$VAULT_PROXY_ADDR/v1/$KV_PATH")
else
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        "$VAULT_PROXY_ADDR/v1/$KV_PATH")
fi

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ SSH KV 자격증명 조회 성공"
    echo ""
    
    # username, password 추출
    SSH_USERNAME=$(echo "$RESPONSE_BODY" | jq -r '.data.data.ssh_username')
    SSH_PASSWORD=$(echo "$RESPONSE_BODY" | jq -r '.data.data.ssh_password')
    
    echo "📋 SSH 자격증명 정보:"
    echo "  - Host: $SSH_HOST"
    echo "  - Username: $SSH_USERNAME"
    echo "  - Password: $SSH_PASSWORD"
    echo ""
else
    echo "❌ SSH KV 자격증명 조회 실패 (HTTP $HTTP_CODE)"
    echo "$RESPONSE_BODY" | jq -r '.errors[]' 2>/dev/null || echo "$RESPONSE_BODY"
    echo ""
    echo "🔧 문제 해결 방법:"
    echo "1. KV 경로 확인: $KV_PATH"
    echo "2. Vault Proxy 상태 확인: $VAULT_PROXY_ADDR"
    echo "3. KV 데이터 존재 여부 확인:"
    echo "   vault kv get my-vault-app-kv/ssh/$SSH_HOST"
    exit 1
fi

# sshpass 확인 및 자동 연결
# sshpass -p "$SSH_PASSWORD" ssh \
#     -o StrictHostKeyChecking=no \
#     -o UserKnownHostsFile=/dev/null \
#     -o ConnectTimeout=5 \
#     -o ConnectionAttempts=1 \
#     "$SSH_USERNAME@$SSH_HOST"