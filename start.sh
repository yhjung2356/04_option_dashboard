#!/bin/bash

# ========================================
# 애플리케이션 시작 스크립트 (systemd 서비스용)
# ========================================

APP_NAME="futures-dashboard"
JAR_FILE="/opt/futures-dashboard/target/futures-options-dashboard-1.0.0.jar"
LOG_FILE="/var/log/futures-dashboard/app.log"
PID_FILE="/var/run/futures-dashboard.pid"

# 로그 디렉토리 생성
mkdir -p /var/log/futures-dashboard

# 애플리케이션 실행
java -jar $JAR_FILE > $LOG_FILE 2>&1 &

# PID 저장
echo $! > $PID_FILE

echo "애플리케이션이 시작되었습니다. PID: $(cat $PID_FILE)"
echo "로그 위치: $LOG_FILE"
