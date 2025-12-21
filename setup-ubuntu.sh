#!/bin/bash

# ========================================
# AWS Ubuntu 초기 설정 스크립트
# ========================================

set -e

echo ""
echo "========================================"
echo "  AWS Ubuntu 환경 초기 설정"
echo "========================================"
echo ""

# 1. 시스템 업데이트
echo "[1/8] 시스템 업데이트..."
sudo apt-get update
sudo apt-get upgrade -y

# 2. Java 17 설치
echo ""
echo "[2/8] Java 17 설치..."
sudo apt-get install -y openjdk-17-jdk
java -version

# 3. Git 설치 (이미 있을 수 있음)
echo ""
echo "[3/8] Git 설치..."
sudo apt-get install -y git
git --version

# 4. Docker 설치
echo ""
echo "[4/8] Docker 설치..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker ubuntu
    rm get-docker.sh
    echo "Docker 설치 완료! (재로그인 필요)"
else
    echo "Docker가 이미 설치되어 있습니다."
fi

# 5. Docker Compose 설치
echo ""
echo "[5/8] Docker Compose 설치..."
if ! command -v docker-compose &> /dev/null; then
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    docker-compose --version
else
    echo "Docker Compose가 이미 설치되어 있습니다."
fi

# 6. 프로젝트 디렉토리 생성
echo ""
echo "[6/8] 프로젝트 디렉토리 생성..."
sudo mkdir -p /opt/option
sudo chown -R ubuntu:ubuntu /opt/option

# 7. 로그 디렉토리 생성
echo ""
echo "[7/8] 로그 디렉토리 생성..."
sudo mkdir -p /var/log/option
sudo chown -R ubuntu:ubuntu /var/log/option

# 8. 방화벽 설정 (포트 8080 열기)
echo ""
echo "[8/8] 방화벽 설정..."
if command -v ufw &> /dev/null; then
    sudo ufw allow 8080/tcp
    sudo ufw status
else
    echo "UFW가 설치되어 있지 않습니다. AWS 보안 그룹에서 포트를 열어주세요."
fi

echo ""
echo "========================================"
echo "  초기 설정 완료!"
echo "========================================"
echo ""
echo "다음 단계:"
echo "1. cd /opt/option"
echo "2. git clone <your-repo-url> ."
echo "3. ./setup-systemd.sh  (systemd 서비스 설정)"
echo "4. 또는 ./auto-deploy.sh  (즉시 실행)"
echo ""
echo "주의: Docker를 처음 설치했다면 재로그인이 필요합니다."
echo "      logout 후 다시 접속하세요."
echo ""
