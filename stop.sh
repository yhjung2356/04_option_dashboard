#!/bin/bash

# ========================================
# 애플리케이션 중지 스크립트
# ========================================

APP_NAME="futures-dashboard"
PID_FILE="/var/run/futures-dashboard.pid"

if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    echo "애플리케이션 종료 중... PID: $PID"
    kill -15 $PID
    
    # 정상 종료 대기 (최대 30초)
    for i in {1..30}; do
        if ! ps -p $PID > /dev/null 2>&1; then
            echo "애플리케이션이 정상 종료되었습니다."
            rm -f $PID_FILE
            exit 0
        fi
        sleep 1
    done
    
    # 강제 종료
    echo "강제 종료합니다..."
    kill -9 $PID
    rm -f $PID_FILE
    echo "애플리케이션이 강제 종료되었습니다."
else
    echo "실행 중인 애플리케이션이 없습니다."
    # 포트로 찾아서 종료
    PID=$(lsof -t -i:8080)
    if [ ! -z "$PID" ]; then
        echo "포트 8080을 사용하는 프로세스 종료: $PID"
        kill -9 $PID
    fi
fi
