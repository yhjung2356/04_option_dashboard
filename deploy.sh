#!/bin/bash

# =============================================================================
# 선물/옵션 대시보드 배포 스크립트
# AWS Ubuntu 환경용
# =============================================================================

set -e  # 에러 발생 시 스크립트 중단

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 설정
APP_NAME="option-monitor"
JAR_NAME="futures-options-dashboard.jar"
DEPLOY_DIR="/home/ubuntu/option-monitor"
BACKUP_DIR="/home/ubuntu/option-monitor-backups"
SERVICE_NAME="option-monitor"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  선물/옵션 대시보드 배포 시작${NC}"
echo -e "${GREEN}========================================${NC}"

# 1. 프론트엔드 빌드 (npm)
echo -e "\n${YELLOW}[1/8] 프론트엔드 빌드 시작...${NC}"
cd frontend
npm install
npm run build
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 프론트엔드 빌드 성공${NC}"
    cd ..
else
    echo -e "${RED}✗ 프론트엔드 빌드 실패${NC}"
    exit 1
fi

# 2. Maven 빌드 (백엔드만)
echo -e "\n${YELLOW}[2/8] Maven 빌드 시작...${NC}"
mvn package -Dskip.npm=true -Dmaven.test.skip=true
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Maven 빌드 성공${NC}"
else
    echo -e "${RED}✗ Maven 빌드 실패${NC}"
    exit 1
fi

# 3. JAR 파일 확인
echo -e "\n${YELLOW}[3/8] JAR 파일 확인...${NC}"
JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}✗ JAR 파일을 찾을 수 없습니다${NC}"
    exit 1
fi
echo -e "${GREEN}✓ JAR 파일 발견: $JAR_FILE${NC}"

# 4. 배포 디렉토리 준비
echo -e "\n${YELLOW}[4/8] 배포 디렉토리 준비...${NC}"
mkdir -p "$DEPLOY_DIR"
mkdir -p "$BACKUP_DIR"
mkdir -p /var/log/option-monitor
sudo chown -R ubuntu:ubuntu /var/log/option-monitor
echo -e "${GREEN}✓ 디렉토리 준비 완료${NC}"

# 5. 기존 JAR 백업
echo -e "\n${YELLOW}[5/8] 기존 JAR 백업...${NC}"
if [ -f "$DEPLOY_DIR/$JAR_NAME" ]; then
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    cp "$DEPLOY_DIR/$JAR_NAME" "$BACKUP_DIR/${JAR_NAME}.${TIMESTAMP}"
    echo -e "${GREEN}✓ 백업 완료: ${JAR_NAME}.${TIMESTAMP}${NC}"
    
    # 오래된 백업 삭제 (최근 5개만 유지)
    cd "$BACKUP_DIR"
    ls -t ${JAR_NAME}.* | tail -n +6 | xargs -r rm
    echo -e "${GREEN}✓ 오래된 백업 정리 완료${NC}"
else
    echo -e "${YELLOW}! 기존 JAR 파일 없음 (첫 배포)${NC}"
fi

# 6. 새 JAR 파일 복사
echo -e "\n${YELLOW}[6/8] 새 JAR 파일 복사...${NC}"
cp "$JAR_FILE" "$DEPLOY_DIR/$JAR_NAME"
echo -e "${GREEN}✓ JAR 파일 복사 완료${NC}"

# 7. Systemd 서비스 설정
echo -e "\n${YELLOW}[7/8] Systemd 서비스 설정...${NC}"
if [ -f "scripts/option-monitor.service" ]; then
    echo "서비스 파일 복사 중..."
    sudo cp scripts/option-monitor.service /etc/systemd/system/
    sudo systemctl daemon-reload
    
    # 서비스가 enabled 되어 있지 않으면 enable
    if ! systemctl is-enabled --quiet "$SERVICE_NAME" 2>/dev/null; then
        echo "서비스 자동 시작 설정 중..."
        sudo systemctl enable "$SERVICE_NAME"
        echo -e "${GREEN}✓ 서비스 자동 시작 활성화${NC}"
    else
        echo -e "${GREEN}✓ 서비스 이미 활성화됨${NC}"
    fi
    
    echo -e "${GREEN}✓ Systemd 서비스 설정 완료${NC}"
else
    echo -e "${YELLOW}! 서비스 파일을 찾을 수 없습니다 (첫 배포가 아니면 무시 가능)${NC}"
fi

# 8. 서비스 재시작
echo -e "\n${YELLOW}[8/8] 서비스 재시작...${NC}"
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "서비스 중지 중..."
    sudo systemctl stop "$SERVICE_NAME"
    sleep 2
fi

echo "서비스 시작 중..."
sudo systemctl start "$SERVICE_NAME"
sleep 3

# 서비스 상태 확인
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo -e "${GREEN}✓ 서비스 재시작 성공${NC}"
else
    echo -e "${RED}✗ 서비스 시작 실패${NC}"
    echo "로그 확인:"
    sudo journalctl -u "$SERVICE_NAME" -n 20 --no-pager
    exit 1
fi

# 8. Health Check
echo -e "\n${YELLOW}[9/9] Health Check...${NC}"
echo "애플리케이션 시작 대기 중..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 애플리케이션이 정상적으로 시작되었습니다!${NC}"
        break
    fi
    
    if [ $i -eq 30 ]; then
        echo -e "${RED}✗ Health Check 타임아웃${NC}"
        echo "로그 확인:"
        sudo tail -n 50 /var/log/option-monitor/application.log
        exit 1
    fi
    
    echo -n "."
    sleep 2
done

# 배포 완료
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  배포 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n배포 정보:"
echo "  - 배포 시간: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  - JAR 파일: $JAR_NAME"
echo "  - 배포 위치: $DEPLOY_DIR"
echo ""
echo "유용한 명령어:"
echo "  - 서비스 상태: sudo systemctl status $SERVICE_NAME"
echo "  - 로그 보기: sudo tail -f /var/log/option-monitor/application.log"
echo "  - 서비스 중지: sudo systemctl stop $SERVICE_NAME"
echo "  - 서비스 시작: sudo systemctl start $SERVICE_NAME"
echo "  - 서비스 재시작: sudo systemctl restart $SERVICE_NAME"
echo "  - Health Check: ./scripts/health-check.sh"
echo ""
echo -e "${GREEN}웹 브라우저에서 확인하세요: http://[서버IP]:8080${NC}"
echo ""
echo -e "${YELLOW}💡 팁: Health Check cron 설정을 원하시면:${NC}"
echo "   crontab -e"
echo "   */5 * * * * $(pwd)/scripts/health-check.sh >> /var/log/option-monitor/cron.log 2>&1"
