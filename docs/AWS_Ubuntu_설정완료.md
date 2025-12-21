# ✅ AWS Ubuntu 설정 완료!

## 🎉 완료된 작업

### 생성된 Linux 스크립트 (10개)
1. ✅ **`auto-deploy.sh`** - 자동 배포 (Git Pull → 빌드 → 실행)
2. ✅ **`hot-reload.sh`** - 핫 리로드 (개발용)
3. ✅ **`docker-auto-deploy.sh`** - Docker 자동 배포
4. ✅ **`start.sh`** - 애플리케이션 시작
5. ✅ **`stop.sh`** - 애플리케이션 중지
6. ✅ **`restart.sh`** - 애플리케이션 재시작
7. ✅ **`setup-ubuntu.sh`** - Ubuntu 초기 설정 (Java, Docker 등)
8. ✅ **`setup-systemd.sh`** - systemd 서비스 설정
9. ✅ **`quick-start.sh`** - 빠른 시작 (대화형)
10. ✅ **`futures-dashboard.service`** - systemd 서비스 파일

### 생성된 문서 (2개)
1. ✅ **`docs/AWS_Ubuntu_배포가이드.md`** - 완벽한 가이드
2. ✅ **`AWS_Ubuntu_빠른배포.md`** - 3분 빠른 시작

### 업데이트된 파일
1. ✅ **`.github/workflows/ci-cd.yml`** - Ubuntu 자동 배포 개선

---

## 🚀 지금 바로 사용하는 법

### AWS Ubuntu EC2에서:

#### 1단계: EC2 접속
```bash
ssh -i your-key.pem ubuntu@your-ec2-ip
```

#### 2단계: 한 줄로 설치
```bash
sudo mkdir -p /opt/futures-dashboard && \
sudo chown -R ubuntu:ubuntu /opt/futures-dashboard && \
cd /opt/futures-dashboard && \
git clone <your-repo-url> . && \
chmod +x *.sh && \
./quick-start.sh
```

**끝!** 🎉

---

## 📊 실행 방법 비교

| 방법 | 명령어 | 장점 | 추천 |
|------|--------|------|------|
| **즉시 실행** | `./auto-deploy.sh` | 빠름, 간단 | 개발/테스트 |
| **systemd** | `./setup-systemd.sh` | 자동 시작, 안정적 | ⭐ 프로덕션 |
| **Docker** | `./docker-auto-deploy.sh` | 환경 일관성 | 컨테이너 선호 시 |

---

## 🎯 추천 구성 (프로덕션)

```bash
# 1. 초기 설정 (처음 한 번)
cd /opt/futures-dashboard
./setup-ubuntu.sh

# 2. systemd 서비스 등록
./setup-systemd.sh

# 3. 시작
sudo systemctl start futures-dashboard

# 4. 상태 확인
sudo systemctl status futures-dashboard

# 5. 로그 확인
sudo journalctl -u futures-dashboard -f
```

**이제 서버가 재부팅되어도 자동으로 시작됩니다!** ✅

---

## 🔄 업데이트 방법

### 코드 수정 후:

**방법 1: 자동 배포 스크립트**
```bash
cd /opt/futures-dashboard
./auto-deploy.sh
```

**방법 2: systemd (추천)**
```bash
cd /opt/futures-dashboard
git pull
./mvnw clean package -DskipTests
sudo systemctl restart futures-dashboard
```

**방법 3: GitHub Actions (최고!)**
```bash
# 로컬에서
git push origin main

# → GitHub Actions가 자동으로:
# 1. 빌드
# 2. EC2 배포
# 3. 재시작

# GitHub Secrets 설정 필요:
# - EC2_HOST: your-ec2-ip
# - EC2_USER: ubuntu
# - EC2_SSH_KEY: (개인키 전체 내용)
```

---

## 📝 유용한 명령어

### 상태 관리
```bash
# 시작
sudo systemctl start futures-dashboard

# 중지
sudo systemctl stop futures-dashboard

# 재시작
sudo systemctl restart futures-dashboard

# 상태 확인
sudo systemctl status futures-dashboard
```

### 로그 확인
```bash
# 실시간 로그
sudo journalctl -u futures-dashboard -f

# 최근 100줄
sudo journalctl -u futures-dashboard -n 100

# 특정 시간 이후
sudo journalctl -u futures-dashboard --since "1 hour ago"
```

### 프로세스 확인
```bash
# Java 프로세스 확인
ps aux | grep java

# 포트 사용 확인
lsof -i :8080

# 리소스 사용량
top
htop
```

---

## 🔒 보안 설정

### AWS 보안 그룹
```
EC2 → Security Groups → Inbound Rules

필수 규칙:
- 포트 22 (SSH): Your IP
- 포트 8080 (HTTP): 0.0.0.0/0 또는 특정 IP
```

### Ubuntu 방화벽 (UFW)
```bash
# UFW 활성화
sudo ufw enable

# 포트 열기
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp

# 상태 확인
sudo ufw status
```

---

## 💡 최적화 팁

### 1. 메모리 최적화 (t2.micro 등)
```bash
# JVM 메모리 설정 (futures-dashboard.service 수정)
Environment="JAVA_OPTS=-Xms256m -Xmx512m"

# 스왑 메모리 추가
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstac
```

### 2. 로그 로테이션
```bash
# /etc/logrotate.d/futures-dashboard 생성
sudo nano /etc/logrotate.d/futures-dashboard

# 내용:
# /var/log/futures-dashboard/*.log {
#     daily
#     rotate 7
#     compress
#     delaycompress
#     missingok
#     notifempty
# }
```

### 3. 자동 업데이트 (선택사항)
```bash
# crontab 편집
crontab -e

# 매일 새벽 3시 자동 업데이트
# 0 3 * * * cd /opt/futures-dashboard && ./auto-deploy.sh >> /var/log/auto-deploy.log 2>&1
```

---

## 🆘 트러블슈팅

### Q: "Permission denied"
```bash
chmod +x *.sh
chmod +x mvnw
```

### Q: 포트 8080이 이미 사용중
```bash
# 프로세스 확인 및 종료
./stop.sh

# 또는
sudo kill -9 $(lsof -t -i:8080)
```

### Q: Java가 없음
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk
java -version
```

### Q: Git clone이 안됨 (Private Repo)
```bash
# SSH 키 생성
ssh-keygen -t rsa -b 4096

# 공개키 GitHub에 등록
cat ~/.ssh/id_rsa.pub

# 또는 HTTPS + Token
git clone https://YOUR_TOKEN@github.com/user/repo.git
```

### Q: 빌드가 실패함
```bash
# 로그 확인
cat /var/log/futures-dashboard/app.log

# 수동 빌드 테스트
./mvnw clean package -DskipTests

# 디스크 공간 확인
df -h
```

### Q: 메모리 부족
```bash
# 현재 메모리 확인
free -h

# 스왑 메모리 추가 (위 최적화 팁 참고)
```

---

## 📚 문서 위치

### 빠른 시작
- `AWS_Ubuntu_빠른배포.md` ← **이 파일**
- `docs/AWS_Ubuntu_배포가이드.md` ← 상세 가이드

### 실시간 반영
- `docs/실시간반영가이드.md`
- Windows용: `auto-deploy.bat`
- Linux용: `auto-deploy.sh`

---

## 🎉 완료!

**이제 AWS Ubuntu에서 완벽하게 실행됩니다!**

### 빠른 명령어 요약:
```bash
# 초기 설정
./setup-ubuntu.sh

# 실행 (프로덕션)
./setup-systemd.sh
sudo systemctl start futures-dashboard

# 상태 확인
sudo systemctl status futures-dashboard

# 로그
sudo journalctl -u futures-dashboard -f

# 재시작
sudo systemctl restart futures-dashboard
```

**Windows용 스크립트도 그대로 있습니다!**
- `auto-deploy.bat`
- `hot-reload.bat`
- `docker-auto-deploy.bat`

**이제 Windows와 Linux 모두 지원합니다!** 🚀

---

**작성일**: 2025-12-21
**대상**: AWS Ubuntu (EC2)
**상태**: ✅ 완료
