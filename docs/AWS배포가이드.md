# 🚀 AWS 배포 가이드

## 📅 2025년 12월 21일

## 🎯 배포 방법 선택

### 1. AWS EC2 (추천) ⭐
- **장점**: 간단하고 빠름, 서버 제어 가능
- **비용**: 프리티어 사용 가능 (t2.micro)
- **적합**: 실시간 WebSocket 필요한 애플리케이션

### 2. AWS Elastic Beanstalk
- **장점**: 자동 스케일링, 로드밸런싱
- **비용**: EC2보다 약간 높음
- **적합**: 트래픽이 많은 경우

### 3. AWS ECS (Docker)
- **장점**: 컨테이너 기반, 확장성 좋음
- **비용**: 중간
- **적합**: MSA 아키텍처

## 📦 1단계: 빌드 (Windows 환경)

### A. Maven 빌드
```cmd
cd D:\Workspace\Spring\futures-options-dashboard
mvnw.cmd clean package -DskipTests
```

**결과 파일:**
```
target\futures-options-dashboard-1.0.0.jar
```

### B. 빌드 확인
```cmd
dir target\*.jar
```

**출력 예시:**
```
futures-options-dashboard-1.0.0.jar          (약 60-80MB)
futures-options-dashboard-1.0.0.jar.original (약 1-2MB)
```

**사용할 파일:** `futures-options-dashboard-1.0.0.jar` (큰 파일)

## 🔧 2단계: AWS EC2 배포 (상세)

### Step 1: EC2 인스턴스 생성

1. **AWS Console 접속**
   - https://console.aws.amazon.com/ec2

2. **인스턴스 시작**
   - AMI: `Amazon Linux 2023` 또는 `Ubuntu 22.04 LTS`
   - 인스턴스 타입: `t2.micro` (프리티어) 또는 `t3.small`
   - 키 페어: 새로 생성 또는 기존 사용 (`.pem` 파일 다운로드)
   - 보안 그룹:
     ```
     SSH:   22 (내 IP만)
     HTTP:  80 (0.0.0.0/0)
     Custom: 8080 (0.0.0.0/0)
     ```

3. **탄력적 IP 할당** (선택사항)
   - 고정 IP 필요 시

### Step 2: Java 17 설치

**Amazon Linux 2023:**
```bash
sudo dnf install -y java-17-amazon-corretto-devel
java -version
```

**Ubuntu:**
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version
```

### Step 3: 애플리케이션 업로드

**방법 A: SCP 사용 (Windows PowerShell)**
```powershell
# PEM 파일 위치로 이동
cd C:\Users\YourName\.ssh

# 파일 업로드 (한 줄로)
scp -i "your-key.pem" D:\Workspace\Spring\futures-options-dashboard\target\futures-options-dashboard-1.0.0.jar ec2-user@YOUR-EC2-IP:/home/ec2-user/
```

**방법 B: WinSCP 사용**
1. WinSCP 다운로드: https://winscp.net
2. 호스트: EC2 퍼블릭 IP
3. 사용자: `ec2-user` (Amazon Linux) 또는 `ubuntu`
4. 프라이빗 키: `.pem` 파일 선택
5. JAR 파일 드래그 앤 드롭

### Step 4: SSH 접속

**Windows PowerShell:**
```powershell
ssh -i "your-key.pem" ec2-user@YOUR-EC2-IP
```

**또는 PuTTY 사용:**
1. PuTTYgen으로 `.pem` → `.ppk` 변환
2. PuTTY로 접속

### Step 5: 애플리케이션 실행

**A. 직접 실행 (테스트용)**
```bash
cd /home/ec2-user
java -jar futures-options-dashboard-1.0.0.jar
```

**B. 백그라운드 실행**
```bash
nohup java -jar futures-options-dashboard-1.0.0.jar > app.log 2>&1 &
```

**C. Systemd 서비스로 등록 (추천)**

1. 서비스 파일 생성:
```bash
sudo nano /etc/systemd/system/trading-dashboard.service
```

2. 내용 입력:
```ini
[Unit]
Description=Futures Options Trading Dashboard
After=syslog.target network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/futures-options-dashboard-1.0.0.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

3. 서비스 시작:
```bash
sudo systemctl daemon-reload
sudo systemctl enable trading-dashboard
sudo systemctl start trading-dashboard
```

4. 상태 확인:
```bash
sudo systemctl status trading-dashboard
```

5. 로그 확인:
```bash
sudo journalctl -u trading-dashboard -f
```

### Step 6: Nginx 리버스 프록시 설정 (선택사항)

**80 포트로 접속하기:**

1. Nginx 설치:
```bash
sudo dnf install -y nginx  # Amazon Linux
# 또는
sudo apt install -y nginx  # Ubuntu
```

2. 설정 파일 생성:
```bash
sudo nano /etc/nginx/conf.d/trading.conf
```

3. 내용:
```nginx
server {
    listen 80;
    server_name YOUR-DOMAIN.com;  # 또는 EC2 IP

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 지원
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

4. Nginx 시작:
```bash
sudo systemctl enable nginx
sudo systemctl start nginx
```

### Step 7: 접속 확인

```
http://YOUR-EC2-IP:8080
# 또는 Nginx 사용 시
http://YOUR-EC2-IP
```

## 🐳 3단계: Docker 배포 (대안)

### Dockerfile 생성

프로젝트 루트에 `Dockerfile` 생성:

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/futures-options-dashboard-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker 이미지 빌드
```bash
docker build -t trading-dashboard .
```

### Docker 실행
```bash
docker run -d -p 8080:8080 --name trading-dashboard trading-dashboard
```

### Docker Hub에 푸시 (선택)
```bash
docker tag trading-dashboard YOUR-DOCKERHUB/trading-dashboard:latest
docker push YOUR-DOCKERHUB/trading-dashboard:latest
```

## ☁️ 4단계: AWS Elastic Beanstalk 배포

### A. EB CLI 설치 (Windows)
```powershell
pip install awsebcli
```

### B. 초기화
```bash
cd D:\Workspace\Spring\futures-options-dashboard
eb init
```

### C. 배포
```bash
eb create trading-dashboard-env
eb open
```

## 🔐 5단계: 환경 변수 설정

### application-prod.properties 생성

`src/main/resources/application-prod.properties`:

```properties
spring.application.name=Futures Options Dashboard

# Production Database (예: MySQL/PostgreSQL)
# spring.datasource.url=jdbc:mysql://RDS-ENDPOINT:3306/tradingdb
# spring.datasource.username=${DB_USERNAME}
# spring.datasource.password=${DB_PASSWORD}

# H2 (개발용으로 유지)
spring.datasource.url=jdbc:h2:mem:tradingdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console (운영에서는 비활성화 권장)
spring.h2.console.enabled=false

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Server
server.port=8080

# Logging
logging.level.com.trading.dashboard=INFO
logging.level.com.trading.dashboard.service.KisApiService=WARN

# Trading Settings
trading.market-hours-check.enabled=true
```

### 환경 변수로 실행

```bash
java -jar -Dspring.profiles.active=prod futures-options-dashboard-1.0.0.jar
```

## 📊 6단계: 모니터링 설정

### CloudWatch 로그 전송 (선택)

1. CloudWatch 에이전트 설치
2. 로그 그룹 생성
3. 로그 스트림 연결

### 간단한 헬스 체크

```bash
curl http://localhost:8080/actuator/health
```

## 🔧 7단계: 성능 최적화

### JVM 옵션 설정

```bash
java -Xms512m -Xmx1024m \
     -XX:+UseG1GC \
     -jar futures-options-dashboard-1.0.0.jar
```

### Systemd 서비스 파일에 추가:

```ini
[Service]
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -XX:+UseG1GC -jar /home/ec2-user/futures-options-dashboard-1.0.0.jar
```

## 🛠️ 유용한 명령어

### 애플리케이션 관리
```bash
# 상태 확인
sudo systemctl status trading-dashboard

# 시작
sudo systemctl start trading-dashboard

# 중지
sudo systemctl stop trading-dashboard

# 재시작
sudo systemctl restart trading-dashboard

# 로그 보기
sudo journalctl -u trading-dashboard -f

# 프로세스 확인
ps aux | grep java
```

### 포트 확인
```bash
# 8080 포트 확인
sudo netstat -tulpn | grep 8080
# 또는
sudo lsof -i :8080
```

### 방화벽 확인
```bash
# Amazon Linux
sudo firewall-cmd --list-all

# Ubuntu
sudo ufw status
```

## 📋 체크리스트

배포 전:
- [ ] JAR 파일 빌드 완료
- [ ] EC2 인스턴스 생성
- [ ] 보안 그룹 설정 (22, 80, 8080)
- [ ] Java 17 설치
- [ ] 키 페어 준비

배포 중:
- [ ] JAR 파일 업로드
- [ ] 애플리케이션 실행
- [ ] Systemd 서비스 등록
- [ ] Nginx 설정 (선택)

배포 후:
- [ ] 브라우저 접속 테스트
- [ ] WebSocket 연결 확인
- [ ] API 데이터 로딩 확인
- [ ] 로그 모니터링

## 🚨 트러블슈팅

### 1. 포트 충돌
```bash
# 8080 포트 사용 중인 프로세스 확인
sudo lsof -i :8080
# 프로세스 종료
sudo kill -9 [PID]
```

### 2. 권한 문제
```bash
# JAR 파일 실행 권한 부여
chmod +x futures-options-dashboard-1.0.0.jar
```

### 3. 메모리 부족
```bash
# 스왑 메모리 추가 (t2.micro용)
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### 4. 로그 확인
```bash
# 애플리케이션 로그
sudo journalctl -u trading-dashboard --since "10 minutes ago"

# Nginx 로그
sudo tail -f /var/log/nginx/error.log
```

## 💰 비용 예상 (월)

### 프리티어 (12개월)
- EC2 t2.micro: $0
- 데이터 전송 15GB: $0
- **총: $0**

### 프리티어 이후
- EC2 t3.small: $15-20
- 탄력적 IP: $3.6
- 데이터 전송: $5-10
- **총: $25-35/월**

## 🎉 완료!

배포가 완료되면 다음 주소로 접속:
```
http://YOUR-EC2-PUBLIC-IP:8080
```

또는 도메인 연결 후:
```
http://yourdomain.com
```

## 📚 추가 자료

- [AWS EC2 시작하기](https://docs.aws.amazon.com/ec2/)
- [Spring Boot 배포 가이드](https://spring.io/guides/gs/spring-boot/)
- [Nginx 설정](https://nginx.org/en/docs/)

---

**빌드 명령어 요약:**
```cmd
# Windows에서 실행
cd D:\Workspace\Spring\futures-options-dashboard
mvnw.cmd clean package -DskipTests

# 결과: target\futures-options-dashboard-1.0.0.jar
```

이 파일을 EC2에 업로드하고 실행하면 됩니다! 🚀
