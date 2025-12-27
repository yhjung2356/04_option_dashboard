#!/bin/bash

# =============================================================================
# 선물/옵션 대시보드 Health Check 스크립트
# AWS Ubuntu 환경용 - 주기적으로 애플리케이션 상태 확인
# =============================================================================

# 설정
APP_URL="http://localhost:8080"
LOG_DIR="/var/log/option-monitor"
LOG_FILE="$LOG_DIR/health-check.log"
ERROR_LOG="$LOG_DIR/health-errors.log"
MAX_LOG_SIZE=10485760  # 10MB
DATE=$(date '+%Y-%m-%d %H:%M:%S')

# 로그 디렉토리 생성
mkdir -p "$LOG_DIR"

# 로그 파일 크기 체크 및 로테이션
check_log_size() {
    if [ -f "$1" ]; then
        size=$(stat -f%z "$1" 2>/dev/null || stat -c%s "$1" 2>/dev/null)
        if [ "$size" -gt "$MAX_LOG_SIZE" ]; then
            mv "$1" "$1.old"
            echo "[$DATE] Log rotated due to size limit" > "$1"
        fi
    fi
}

# 로그 함수
log_info() {
    check_log_size "$LOG_FILE"
    echo "[$DATE] INFO: $1" | tee -a "$LOG_FILE"
}

log_error() {
    check_log_size "$ERROR_LOG"
    echo "[$DATE] ERROR: $1" | tee -a "$ERROR_LOG"
}

# Health Check
log_info "Starting health check..."

# 1. 기본 연결 확인 (Actuator health endpoint 시도)
response=$(curl -s -w "\n%{http_code}" -o /tmp/health-response.txt --connect-timeout 5 --max-time 10 "$APP_URL/actuator/health" 2>&1)
http_code=$(echo "$response" | tail -n1)
body=$(cat /tmp/health-response.txt 2>/dev/null)

# Actuator가 없으면 메인 페이지로 시도
if [ "$http_code" = "404" ]; then
    log_info "Actuator not found, trying main page..."
    response=$(curl -s -w "\n%{http_code}" -o /tmp/health-response.txt --connect-timeout 5 --max-time 10 "$APP_URL/" 2>&1)
    http_code=$(echo "$response" | tail -n1)
    body=$(cat /tmp/health-response.txt 2>/dev/null)
fi

if [ "$http_code" = "200" ]; then
    log_info "✓ Application is healthy (HTTP $http_code)"
    
    # 2. API 응답 시간 체크
    response_time=$(curl -s -w "%{time_total}" -o /dev/null --max-time 10 "$APP_URL/api/options/summary" 2>&1)
    if [ $? -eq 0 ]; then
        log_info "✓ API response time: ${response_time}s"
        
        # 응답 시간이 5초 이상이면 경고
        if (( $(echo "$response_time > 5.0" | bc -l) )); then
            log_error "⚠ Slow API response: ${response_time}s"
        fi
    else
        log_error "✗ API request failed"
    fi
    
    # 3. 메모리 사용량 체크
    if command -v ps &> /dev/null; then
        java_pid=$(pgrep -f "option-monitor.*jar" | head -n1)
        if [ -n "$java_pid" ]; then
            mem_usage=$(ps -p "$java_pid" -o rss= | awk '{print $1/1024}')
            log_info "Memory usage: ${mem_usage}MB"
            
            # 메모리 1.5GB(1536MB) 이상 사용 시 경고
            if (( $(echo "$mem_usage > 1536" | bc -l) )); then
                log_error "⚠ High memory usage: ${mem_usage}MB"
            fi
        fi
    fi
    
else
    log_error "✗ Application is unhealthy (HTTP $http_code)"
    log_error "Response: $body"
    
    # 연속 3회 실패 시 재시작 시도 (선택사항)
    # FAILURE_COUNT_FILE="/tmp/health-check-failures"
    # failures=$(cat "$FAILURE_COUNT_FILE" 2>/dev/null || echo 0)
    # failures=$((failures + 1))
    # echo "$failures" > "$FAILURE_COUNT_FILE"
    # 
    # if [ "$failures" -ge 3 ]; then
    #     log_error "⚠ 3 consecutive failures detected. Attempting restart..."
    #     sudo systemctl restart option-monitor
    #     echo "0" > "$FAILURE_COUNT_FILE"
    # fi
fi

# 4. 디스크 공간 체크 (60GB SSD)
disk_usage=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
log_info "Disk usage: ${disk_usage}%"

if [ "$disk_usage" -gt 80 ]; then
    log_error "⚠ High disk usage: ${disk_usage}%"
    
    # 오래된 로그 자동 정리
    find "$LOG_DIR" -name "*.log.old" -mtime +7 -delete
    log_info "Old logs cleaned up"
fi

# 5. 간단한 통계 (최근 1시간 에러 개수)
if [ -f "$ERROR_LOG" ]; then
    error_count=$(grep -c "ERROR" "$ERROR_LOG" 2>/dev/null || echo 0)
    log_info "Total errors in log: $error_count"
fi

log_info "Health check completed\n"

# 정리
rm -f /tmp/health-response.txt
