#!/bin/bash

# ========================================
# systemd 서비스 설정 스크립트
# ========================================

set -e

echo ""
echo "========================================"
echo "  systemd 서비스 설정"
echo "========================================"
echo ""

# 1. 서비스 파일 복사
echo "[1/4] 서비스 파일 복사..."
sudo cp option.service /etc/systemd/system/
sudo chmod 644 /etc/systemd/system/option.service

# 2. systemd 데몬 리로드
echo ""
echo "[2/4] systemd 데몬 리로드..."
sudo systemctl daemon-reload

# 3. 서비스 활성화 (부팅 시 자동 시작)
echo ""
echo "[3/4] 서비스 활성화..."
sudo systemctl enable option

# 4. JAR 파일이 있는지 확인
echo ""
echo "[4/4] JAR 파일 확인..."
if [ ! -f target/futures-options-dashboard-1.0.0.jar ]; then
    echo "JAR 파일이 없습니다. 빌드를 먼저 실행합니다..."
    ./mvnw clean package -DskipTests
fi

echo ""
echo "========================================"
echo "  systemd 서비스 설정 완료!"
echo "========================================"
echo ""
echo "사용법:"
echo "  시작:   sudo systemctl start option"
echo "  중지:   sudo systemctl stop option"
echo "  재시작: sudo systemctl restart option"
echo "  상태:   sudo systemctl status option"
echo "  로그:   sudo journalctl -u option -f"
echo ""
echo "지금 시작하려면:"
echo "  sudo systemctl start option"
echo ""
