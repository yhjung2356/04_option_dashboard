#!/bin/bash

# ========================================
# 빠른 시작 스크립트 (AWS Ubuntu)
# ========================================

echo ""
echo "========================================"
echo "  선물/옵션 대시보드 빠른 시작"
echo "========================================"
echo ""

# 실행 권한 확인
if [ ! -x "./auto-deploy.sh" ]; then
    echo "실행 권한 부여 중..."
    chmod +x *.sh
fi

# Maven Wrapper 실행 권한
if [ ! -x "./mvnw" ]; then
    chmod +x mvnw
fi

echo "1. 즉시 실행 (개발/테스트용)"
echo "2. systemd 서비스로 등록 (프로덕션용)"
echo "3. Docker로 실행"
echo ""
read -p "선택 (1-3): " choice

case $choice in
    1)
        echo ""
        echo "즉시 실행 모드..."
        ./auto-deploy.sh
        ;;
    2)
        echo ""
        echo "systemd 서비스 설정..."
        ./setup-systemd.sh
        echo ""
        read -p "지금 시작하시겠습니까? (y/n): " start
        if [ "$start" = "y" ]; then
            sudo systemctl start option
            sudo systemctl status option
        fi
        ;;
    3)
        echo ""
        echo "Docker 실행..."
        ./docker-auto-deploy.sh
        ;;
    *)
        echo "잘못된 선택입니다."
        exit 1
        ;;
esac

echo ""
echo "완료!"
echo ""
