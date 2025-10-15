#!/bin/bash

# Vault Proxy를 통한 SSH Signed Certificate 생성 스크립트

VAULT_PROXY_ADDR="http://127.0.0.1:8400"
SSH_CA_PATH="my-vault-app-ssh-ca/sign/client-signer"

# 사용자 설정 (수정 필요)
SSH_USERNAME="test2"
SSH_PUBLIC_KEY_PATH="$HOME/.ssh/vault_rsa.pub"

echo "=== Vault Proxy를 통한 SSH Signed Certificate 생성 ==="
echo "Vault Proxy: $VAULT_PROXY_ADDR"
echo "SSH CA Path: $SSH_CA_PATH"
echo "Username: $SSH_USERNAME"
echo "Public Key: $SSH_PUBLIC_KEY_PATH"
echo ""

# Public Key 읽기
if [ ! -f "$SSH_PUBLIC_KEY_PATH" ]; then
    echo "❌ Public key 파일을 찾을 수 없습니다: $SSH_PUBLIC_KEY_PATH"
    echo "다음 명령어로 SSH 키를 생성하세요:"
    echo "  ssh-keygen -t rsa -C \"test@rocky\" -f ~/.ssh/vault_rsa"
    exit 1
fi

PUBLIC_KEY=$(cat "$SSH_PUBLIC_KEY_PATH")

# SSH Signed Certificate 생성
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "{\"public_key\":\"$PUBLIC_KEY\",\"valid_principals\":\"$SSH_USERNAME\"}" \
    "$VAULT_PROXY_ADDR/v1/$SSH_CA_PATH")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ SSH Signed Certificate 생성 성공"
    echo ""
    
    # Certificate 추출 및 저장
    SIGNED_KEY=$(echo "$RESPONSE_BODY" | jq -r '.data.signed_key')
    SERIAL_NUMBER=$(echo "$RESPONSE_BODY" | jq -r '.data.serial_number')
    
    CERT_PATH="$HOME/.ssh/vault_rsa-cert.pub"
    echo "$SIGNED_KEY" > "$CERT_PATH"
    
    # Certificate 유효성 검증
    if ssh-keygen -L -f "$CERT_PATH" >/dev/null 2>&1; then
        echo "✅ Certificate 유효성 검증 성공"
    else
        echo "❌ Certificate 유효성 검증 실패"
        echo "Certificate 내용 확인:"
        cat "$CERT_PATH"
    fi
    
    echo "📋 SSH Certificate 정보:"
    echo "  - Serial Number: $SERIAL_NUMBER"
    echo "  - Certificate Path: $CERT_PATH"
    echo ""
    echo "🔐 SSH 연결 방법:"
    echo "  1. SSH 서버 설정 (한 번만 수행):"
    echo "     # SSH 서버에서 CA Public Key 등록"
    echo "     vault read -field=public_key my-vault-app-ssh-ca/config/ca | sudo tee /etc/ssh/trusted-user-ca-keys.pem"
    echo "     echo 'TrustedUserCAKeys /etc/ssh/trusted-user-ca-keys.pem' | sudo tee -a /etc/ssh/sshd_config"
    echo "     sudo systemctl restart sshd"
    echo ""
    echo "  2. SSH 연결:"
    echo "     ssh -i ~/.ssh/vault_rsa -o CertificateFile=~/.ssh/vault_rsa-cert.pub -o IdentitiesOnly=yes test2@<호스트IP>"
    echo ""
    echo "  3. Certificate 확인:"
    echo "     ssh-keygen -L -f $CERT_PATH"
    echo ""
    echo "⚠️  주의: Certificate는 20초 후 만료됩니다."
else
    echo "❌ SSH Signed Certificate 생성 실패 (HTTP $HTTP_CODE)"
    echo "$RESPONSE_BODY" | jq -r '.errors[]' 2>/dev/null || echo "$RESPONSE_BODY"
    exit 1
fi

# 동작 테스트
# ssh -i ~/.ssh/vault_rsa -i ~/.ssh/vault_rsa-cert.pub test2@146.56.162.165
