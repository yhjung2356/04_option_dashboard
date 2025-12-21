# 🚀 AWS Ubuntu 배포 가이드

## 📋 빠른 시작 (3분 완료!)

### Step 1: EC2 접속
```bash
ssh -i your-key.pem ubuntu@your-ec2-ip
```

### Step 2: 프로젝트 클론
```bash
sudo mkdir -p /opt/futures-dashboard
sudo chown -R ubuntu:ubuntu /opt/futures-dashboard
cd /opt/futures-dashboard
git clone <your-repo-url> .
```

### Step 3: 초기 설정 (처음 한 번만)
```bash
chmod +x *.sh
./setup-ubuntu.sh
```

### Step 4: 실행!
```bash
# 방법 1: 즉시 실행 (간단!)
./quick-start.sh

# 방법 2: 자동 배포 스크립트
./auto-deploy.sh

# 방법 3: systemd 서비스 (추천!)
./setup-systemd.sh
sudo systemctl start futures-dashboard
```

**완료!** 🎉
브라우저에서 `http://your-ec2-ip:8080` 접속

---

## 🎯 배포 방법 선택

### 1. 즉시 실행 (개발/테스트) ⭐
```bash
./auto-deploy.sh
```
- ✅ 가장 빠름
- ✅ Git Pull → 빌드 → 실행 (자동)
- ⚠️ SSH 연결 끊기면 종료됨

### 2. systemd 서비스 (프로덕션) ⭐⭐⭐
```bash
./setup-systemd.sh
sudo systemctl start futures-dashboard
```
- ✅ 자동 시작 (서버 재부팅 시)
- ✅ 백그라운드 실행
- ✅ 로그 관리
- ✅ **가장 추천!**

### 3. Docker (컨테이너) ⭐⭐
```bash
./docker-auto-deploy.sh
```
- ✅ 환경 일관성
- ✅ 쉬운 관리
- ⚠️ Docker 설치 필요

---

## 📝 상세 가이드

### 초기 설정 (처음 한 번만)

#### 1. AWS 보안 그룹 설정
```
EC2 → Security Groups → Inbound Rules
- 포트 8080: 0.0.0.0/0 (HTTP)
- 포트 22: Your IP (SSH)
```

#### 2. Ubuntu 환경 설정
```bash
# 시스템 업데이트
sudo apt-get update
sudo apt-get upgrade -y

# 또는 자동 설정 스크립트 사용
./setup-ubuntu.sh
```

#### 3. Git 설정 (Private Repo 사용 시)
```bash
# SSH 키 생성
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"

# 공개키 복사
cat ~/.ssh/id_rsa.pub

# GitHub → Settings → SSH Keys에 추가
```

---

## 🔧 systemd 서비스 설정 (추천!)

### 설치
```bash
./setup-systemd.sh
```

### 사용법
```bash
# 시작
sudo systemctl start futures-dashboard

# 중지
sudo systemctl stop futures-dashboard

# 재시작
sudo systemctl restart futures-dashboard

# 상태 확인
sudo systemctl status futures-dashboard

# 로그 확인 (실시간)
sudo journalctl -u futures-dashboard -f

# 부팅 시 자동 시작 (이미 활성화됨)
sudo systemctl enable futures-dashboard
```

### 장점
- ✅ SSH 연결 끊어져도 계속 실행
- ✅ 서버 재부팅 시 자동 시작
- ✅ 크래시 시 자동 재시작
- ✅ 로그 관리 자동
- ✅ 프로덕션 환경에 최적

---

## 🐳 Docker 배포

### 설치 (Docker 없는 경우)
```bash
./setup-ubuntu.sh  # Docker 자동 설치 포함
```

### 사용법
```bash
# 빌드 및 실행
./docker-auto-deploy.sh

# 로그 확인
docker-compose logs -f

# 중지
docker-compose down

# 재시작
docker-compose restart
```

---

## ⚡ 빠른 명령어 모음

### 실행 관련
```bash
# 즉시 실행
./auto-deploy.sh

# systemd로 시작
sudo systemctl start futures-dashboard

# Docker로 시작
docker-compose up -d
```

### 중지 관련
```bash
# 프로세스 중지
./stop.sh

# systemd 중지
sudo systemctl stop futures-dashboard

# Docker 중지
docker-compose down
```

### 로그 확인
```bash
# 일반 실행 시
tail -f app.log

# systemd 사용 시
sudo journalctl -u futures-dashboard -f

# Docker 사용 시
docker-compose logs -f
```

### 상태 확인
```bash
# 프로세스 확인
ps aux | grep java
lsof -i :8080

# systemd 상태
sudo systemctl status futures-dashboard

# Docker 상태
docker-compose ps
```

---

## 🔄 자동 배포 설정

### Git Pull로 자동 업데이트
```bash
# auto-deploy.sh 실행하면:
# 1. Git Pull (최신 코드)
# 2. 기존 프로세스 종료
# 3. Maven 빌드
# 4. 애플리케이션 실행

./auto-deploy.sh
```

### GitHub Actions로 자동 배포
```bash
# .github/workflows/ci-cd.yml 설정 완료!
# Git Push만 하면 자동으로:
# 1. 빌드
# 2. Docker 이미지 생성
# 3. EC2 배포

# GitHub Secrets 설정 필요:
# - EC2_HOST: EC2 IP 주소
# - EC2_USER: ubuntu
# - EC2_SSH_KEY: SSH 개인키 내용
```

---

## 🛠️ 트러블슈팅

### Q1: "Permission denied" 오류
```bash
# 실행 권한 부여
chmod +x *.sh
chmod +x mvnw
```

### Q2: 포트 8080이 이미 사용중
```bash
# 사용 중인 프로세스 확인
lsof -i :8080

# 종료
./stop.sh

# 또는 직접 종료
kill -9 $(lsof -t -i:8080)
```

### Q3: Java가 없다고 나옴
```bash
# Java 17 설치
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk
java -version
```

### Q4: Git clone이 안됨 (Private Repo)
```bash
# SSH 키 생성
ssh-keygen -t rsa -b 4096

# 공개키 GitHub에 등록
cat ~/.ssh/id_rsa.pub

# 또는 HTTPS + Personal Access Token 사용
git clone https://YOUR_TOKEN@github.com/user/repo.git
```

### Q5: 메모리 부족
```bash
# 스왑 메모리 추가 (t2.micro 등)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 영구 적용
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Q6: 빌드가 너무 느림
```bash
# 테스트 스킵하고 빌드
./mvnw clean package -DskipTests

# 또는 로컬에서 빌드 후 JAR 파일만 업로드
# 로컬: mvnw clean package
# 업로드: scp target/*.jar ubuntu@ec2-ip:/opt/futures-dashboard/target/
```

---

## 🔐 보안 설정

### 1. 방화벽 설정
```bash
# UFW 활성화
sudo ufw enable

# 필요한 포트만 열기
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 8080/tcp  # 애플리케이션

# 상태 확인
sudo ufw status
```

### 2. Nginx 리버스 프록시 (선택사항)
```bash
# Nginx 설치
sudo apt-get install -y nginx

# 설정 파일 생성
sudo nano /etc/nginx/sites-available/futures-dashboard

# 내용:
# server {
#     listen 80;
#     server_name your-domain.com;
#     
#     location / {
#         proxy_pass http://localhost:8080;
#         proxy_set_header Host $host;
#         proxy_set_header X-Real-IP $remote_addr;
#     }
# }

# 활성화
sudo ln -s /etc/nginx/sites-available/futures-dashboard /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 3. SSL 인증서 (Let's Encrypt)
```bash
# Certbot 설치
sudo apt-get install -y certbot python3-certbot-nginx

# SSL 인증서 발급
sudo certbot --nginx -d your-domain.com

# 자동 갱신 확인
sudo certbot renew --dry-run
```

---

## 📊 모니터링

### 로그 확인
```bash
# 실시간 로그
tail -f app.log
tail -f /var/log/futures-dashboard/app.log

# systemd 로그
sudo journalctl -u futures-dashboard -f
sudo journalctl -u futures-dashboard --since "1 hour ago"

# Docker 로그
docker-compose logs -f
docker-compose logs --tail=100
```

### 리소스 사용량
```bash
# CPU, 메모리 사용량
top
htop

# 디스크 사용량
df -h

# 프로세스별 리소스
ps aux | grep java
```

---

## 🎯 최적화 팁

### 1. JVM 메모리 설정
```bash
# start.sh 또는 systemd 서비스 파일 수정
JAVA_OPTS="-Xms512m -Xmx1024m"

# futures-dashboard.service에서:
Environment="JAVA_OPTS=-Xms512m -Xmx1024m"
```

### 2. 프로파일 설정
```bash
# 프로덕션 프로파일 사용
java -jar app.jar --spring.profiles.active=prod

# systemd에서:
Environment="SPRING_PROFILES_ACTIVE=prod"
```

### 3. 로그 레벨 조정
```bash
# application-prod.properties
logging.level.root=WARN
logging.level.com.trading=INFO
```

---

## 🎉 완료 체크리스트

### 초기 설정
- [ ] EC2 인스턴스 생성 및 접속
- [ ] 보안 그룹 설정 (포트 8080, 22)
- [ ] Java 17 설치 확인
- [ ] Git 설치 및 리포지토리 클론
- [ ] 스크립트 실행 권한 부여

### 배포 방법 선택
- [ ] 즉시 실행: `./auto-deploy.sh`
- [ ] systemd 서비스: `./setup-systemd.sh`
- [ ] Docker: `./docker-auto-deploy.sh`

### 확인
- [ ] 애플리케이션 실행 확인
- [ ] 브라우저 접속 확인
- [ ] 로그 확인

---

## 💡 추천 구성

### 소규모 (t2.micro, t2.small)
```bash
# systemd 서비스 사용
./setup-systemd.sh
sudo systemctl start futures-dashboard

# 스왑 메모리 추가
sudo fallocate -l 2G /swapfile
# ... (위 Q5 참고)
```

### 중규모 (t2.medium 이상)
```bash
# Docker 사용
./docker-auto-deploy.sh

# Nginx 리버스 프록시
# ... (위 보안 설정 참고)
```

### 프로덕션
```bash
# systemd + Nginx + SSL
1. ./setup-systemd.sh
2. Nginx 설정
3. Let's Encrypt SSL
4. GitHub Actions 자동 배포
```

---

## 📞 빠른 명령어 치트시트

```bash
# === 실행 ===
./quick-start.sh              # 대화형 선택
./auto-deploy.sh              # 즉시 실행
sudo systemctl start futures-dashboard  # systemd

# === 중지 ===
./stop.sh                     # 즉시 중지
sudo systemctl stop futures-dashboard   # systemd

# === 로그 ===
tail -f app.log               # 일반
sudo journalctl -u futures-dashboard -f # systemd

# === 상태 ===
ps aux | grep java            # 프로세스
sudo systemctl status futures-dashboard # systemd

# === 재시작 ===
./restart.sh                  # 일반
sudo systemctl restart futures-dashboard # systemd
```

---

**이제 AWS Ubuntu에서 완벽하게 실행할 수 있습니다!** 🚀

**추천:** systemd 서비스 방식이 가장 안정적입니다!

```bash
./setup-systemd.sh
sudo systemctl start futures-dashboard
```

끝! 🎉
