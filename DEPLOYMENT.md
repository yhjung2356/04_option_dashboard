# =============================================================================
# AWS Ubuntu 서버 설치 가이드
# 선물/옵션 대시보드
# =============================================================================

## 📋 시스템 요구사항
- OS: Ubuntu 20.04 LTS 이상
- RAM: 2GB
- CPU: 2 vCPU
- Disk: 60GB SSD

## 🚀 초기 설정 (서버에서 실행)

### 1. Java 설치
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version
```

### 2. 필요한 패키지 설치
```bash
sudo apt install -y curl wget git logrotate
```

### 3. 애플리케이션 사용자 설정 (이미 ubuntu 사용자가 있으면 생략)
```bash
# 로그 디렉토리 생성
sudo mkdir -p /var/log/option-monitor
sudo chown -R ubuntu:ubuntu /var/log/option-monitor
```

### 4. 배포 디렉토리 생성
```bash
mkdir -p /home/ubuntu/option-monitor
mkdir -p /home/ubuntu/option-monitor-backups
```

## 📦 애플리케이션 배포

### 방법 1: Git으로 소스 코드 배포 (추천)
```bash
cd /home/ubuntu
git clone <your-repository-url> option-monitor-source
cd option-monitor-source

# 배포 스크립트 실행 권한 부여
chmod +x deploy.sh
chmod +x scripts/health-check.sh

# 배포 실행
./deploy.sh
```

### 방법 2: 직접 JAR 파일 업로드
```bash
# 로컬에서 빌드
mvn clean package -DskipTests

# SCP로 서버에 업로드
scp target/futures-options-dashboard-*.jar ubuntu@[서버IP]:/home/ubuntu/option-monitor/futures-options-dashboard.jar
```

## ⚙️ Systemd 서비스 설정

### 1. 서비스 파일 복사
```bash
sudo cp scripts/option-monitor.service /etc/systemd/system/
sudo systemctl daemon-reload
```

### 2. 서비스 시작 및 자동 시작 설정
```bash
sudo systemctl enable option-monitor
sudo systemctl start option-monitor
sudo systemctl status option-monitor
```

## 📊 로그 관리 설정

### 1. Logrotate 설정
```bash
sudo cp scripts/logrotate-option-monitor /etc/logrotate.d/option-monitor
sudo chmod 644 /etc/logrotate.d/option-monitor

# 테스트
sudo logrotate -d /etc/logrotate.d/option-monitor
```

## 🏥 Health Check 설정

### 1. Health Check 스크립트 설정
```bash
chmod +x scripts/health-check.sh

# 수동 실행 테스트
./scripts/health-check.sh
```

### 2. Cron 설정 (5분마다 자동 체크)
```bash
crontab -e

# 아래 라인 추가
*/5 * * * * /home/ubuntu/option-monitor-source/scripts/health-check.sh >> /var/log/option-monitor/cron.log 2>&1
```

## 🔒 방화벽 설정 (AWS Security Group)

AWS 콘솔에서 Security Group 설정:
- Inbound Rule 추가: TCP 8080 (0.0.0.0/0 또는 특정 IP)
- Outbound Rule: All traffic (기본값)

## 🔍 유용한 명령어

### 서비스 관리
```bash
# 상태 확인
sudo systemctl status option-monitor

# 로그 보기
sudo journalctl -u option-monitor -f

# 재시작
sudo systemctl restart option-monitor

# 중지
sudo systemctl stop option-monitor

# 시작
sudo systemctl start option-monitor
```

### 로그 확인
```bash
# 애플리케이션 로그
tail -f /var/log/option-monitor/application.log

# 에러 로그
tail -f /var/log/option-monitor/application-error.log

# Health check 로그
tail -f /var/log/option-monitor/health-check.log
```

### 시스템 모니터링
```bash
# 메모리 사용량
free -h

# 디스크 사용량
df -h

# Java 프로세스 확인
ps aux | grep java

# 실시간 메모리 모니터링
top -p $(pgrep -f option-monitor)
```

### Health Check 수동 실행
```bash
# API Health Check
curl http://localhost:8080/actuator/health

# 스크립트 실행
./scripts/health-check.sh
```

## 🔄 업데이트 방법

### Git 사용 시
```bash
cd /home/ubuntu/option-monitor-source
git pull
./deploy.sh
```

### 수동 업로드 시
```bash
# 로컬에서 빌드
mvn clean package -DskipTests

# 서버에 업로드 및 배포
scp target/*.jar ubuntu@[서버IP]:/home/ubuntu/option-monitor/futures-options-dashboard.jar
sudo systemctl restart option-monitor
```

## 🐛 트러블슈팅

### 서비스가 시작되지 않을 때
```bash
# 상세 로그 확인
sudo journalctl -u option-monitor -n 100 --no-pager

# 애플리케이션 로그 확인
tail -n 100 /var/log/option-monitor/application-error.log
```

### 메모리 부족 시
```bash
# Java 힙 크기 조정 (service 파일 수정)
sudo vi /etc/systemd/system/option-monitor.service

# -Xmx 값을 1024m 또는 1280m으로 낮춤
# 수정 후:
sudo systemctl daemon-reload
sudo systemctl restart option-monitor
```

### 포트 충돌 시
```bash
# 8080 포트 사용 중인 프로세스 확인
sudo lsof -i :8080

# 프로세스 종료
sudo kill -9 [PID]
```

## 📈 성능 최적화 (2GB RAM 환경)

### application-prod.properties 권장 설정
```properties
# 메모리 효율화
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false

# 커넥션 풀 최적화
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# 로깅 레벨 조정
logging.level.root=WARN
logging.level.com.trading.dashboard=INFO
```

## 🔐 보안 권장사항

1. **방화벽 설정**: 필요한 포트만 열기
2. **SSH 키 인증**: 비밀번호 인증 비활성화
3. **정기 업데이트**: `sudo apt update && sudo apt upgrade`
4. **로그 모니터링**: 정기적인 로그 확인

## 📞 도움말

문제 발생 시 확인할 로그 파일:
- `/var/log/option-monitor/application.log`
- `/var/log/option-monitor/application-error.log`
- `/var/log/option-monitor/health-check.log`
- `sudo journalctl -u option-monitor`
