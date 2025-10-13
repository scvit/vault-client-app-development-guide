#!/bin/bash

# getaws.sh example

# Vault 서버와 인증 경로 설정
export VAULT_ADDR="http://127.0.0.1:8200"  # Vault 서버 주소로 변경
export VAULT_NAMESPACE="admin"  # 필요시 설정
AWS_SECRET_PATH="aws/creds/ec2_admin"  # AWS Secret Path, 역할에 맞게 변경

# AWS credentials 파일 위치
AWS_CREDENTIALS_FILE="$HOME/.aws/credentials"
AWS_PROFILE="default"  # 사용할 AWS 프로파일

# Vault에 OIDC로 로그인
echo "Vault에 Userpass로 로그인 중..."
vault login -method=userpass username=admin

if [ $? -ne 0 ]; then
  echo "Vault 로그인 실패"
  exit 1
fi

# AWS Access Key, Secret Key, Session Token 발급
echo "AWS Secret 발급 요청 중..."
AWS_SECRET=$(vault read -format=json $AWS_SECRET_PATH)

if [ $? -ne 0 ]; then
  echo "AWS Secret 발급 실패"
  exit 1
fi

# 발급된 Access Key, Secret Key, Session Token 추출
AWS_ACCESS_KEY=$(echo $AWS_SECRET | jq -r '.data.access_key')
AWS_SECRET_KEY=$(echo $AWS_SECRET | jq -r '.data.secret_key')
AWS_SESSION_TOKEN=$(echo $AWS_SECRET | jq -r '.data.security_token')

# .aws/credentials 파일에 업데이트
echo "AWS credentials 파일 업데이트 중..."
mkdir -p "$(dirname "$AWS_CREDENTIALS_FILE")"

cat > "$AWS_CREDENTIALS_FILE" <<EOL
[$AWS_PROFILE]
aws_access_key_id = $AWS_ACCESS_KEY
aws_secret_access_key = $AWS_SECRET_KEY
aws_session_token = $AWS_SESSION_TOKEN
EOL

echo "AWS credentials 파일 업데이트 완료!"

# 완료 메시지
echo "AWS Secrets을 성공적으로 발급받고 credentials 파일에 업데이트했습니다."
