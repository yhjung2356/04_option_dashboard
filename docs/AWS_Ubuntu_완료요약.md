# ✅ AWS Ubuntu 설정 완료!

## 🎉 축하합니다!

**Windows와 Linux(AWS Ubuntu) 모두 지원하는 스크립트가 완성되었습니다!**

---

## 📊 생성된 파일 요약

### Windows 스크립트 (3개)
- ✅ `auto-deploy.bat` - Windows 자동 배포
- ✅ `hot-reload.bat` - Windows 핫 리로드
- ✅ `docker-auto-deploy.bat` - Windows Docker 배포

### Linux 스크립트 (10개)
- ✅ `auto-deploy.sh` - Linux 자동 배포
- ✅ `hot-reload.sh` - Linux 핫 리로드
- ✅ `docker-auto-deploy.sh` - Linux Docker 배포
- ✅ `start.sh` - 시작
- ✅ `stop.sh` - 중지
- ✅ `restart.sh` - 재시작
- ✅ `setup-ubuntu.sh` - Ubuntu 초기 설정
- ✅ `setup-systemd.sh` - systemd 설정
- ✅ `quick-start.sh` - 빠른 시작
- ✅ `futures-dashboard.service` - systemd 서비스

### 문서 (5개)
- ✅ `docs/AWS_Ubuntu_배포가이드.md` - 완벽 가이드
- ✅ `docs/AWS_Ubuntu_빠른배포.md` - 3분 시작
- ✅ `docs/AWS_Ubuntu_설정완료.md` - 설정 완료 보고서
- ✅ `README.md` - 업데이트 완료
- ✅ `.github/workflows/ci-cd.yml` - GitHub Actions 업데이트

---

## 🚀 AWS Ubuntu에서 바로 시작!

### 한 줄 명령어 (가장 빠름!)
```bash
ssh -i your-key.pem ubuntu@your-ec2-ip
```

```bash
sudo mkdir -p /opt/futures-dashboard && \
sudo chown -R ubuntu:ubuntu /opt/futures-dashboard && \
cd /opt/futures-dashboard && \
git clone <your-repo-url> . && \
chmod +x *.sh && \
./quick-start.sh
```

**끝!** 브라우저에서 `http://your-ec2-ip:8080` 접속! 🎉

---

## 💡 추천 방법 (프로덕션)

### systemd 서비스 사용 (최고!)
```bash
cd /opt/futures-dashboard
./setup-ubuntu.sh         # 초기 설정 (처음 한 번)
./setup-systemd.sh        # systemd 설정
sudo systemctl start futures-dashboard
sudo systemctl status futures-dashboard
```

**장점:**
- ✅ 서버 재부팅 시 자동 시작
- ✅ SSH 연결 끊어져도 계속 실행
- ✅ 크래시 시 자동 재시작
- ✅ 로그 자동 관리

---

## 🔄 업데이트 방법

### 코드 수정 후:

**Windows:**
```bash
auto-deploy.bat
```

**Linux:**
```bash
./auto-deploy.sh
```

**systemd 사용 시:**
```bash
cd /opt/futures-dashboard
git pull
./mvnw clean package -DskipTests
sudo systemctl restart futures-dashboard
```

**GitHub Actions (자동!):**
```bash
# 로컬에서
git push origin main

# → 자동으로 빌드 및 배포!
```

---

## 📝 유용한 명령어

### Linux/Ubuntu 명령어
```bash
# 실행
sudo systemctl start futures-dashboard

# 중지
sudo systemctl stop futures-dashboard

# 재시작
sudo systemctl restart futures-dashboard

# 상태 확인
sudo systemctl status futures-dashboard

# 로그 확인
sudo journalctl -u futures-dashboard -f

# 프로세스 확인
ps aux | grep java
lsof -i :8080
```

### Windows 명령어
```bash
# 핫 리로드
hot-reload.bat

# 자동 배포
auto-deploy.bat

# 프로세스 확인
netstat -ano | findstr :8080
tasklist | findstr java
```

---

## 🔒 AWS 보안 설정

### 1. 보안 그룹 (필수!)
```
EC2 Console → Security Groups → Inbound Rules

추가해야 할 규칙:
- 포트 22 (SSH): Your IP
- 포트 8080 (HTTP): 0.0.0.0/0
```

### 2. Ubuntu 방화벽 (선택)
```bash
sudo ufw enable
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp
sudo ufw status
```

---

## 📚 문서 위치

### AWS Ubuntu 배포
- **`docs/AWS_Ubuntu_빠른배포.md`** ← 3분 빠른 시작
- **`docs/AWS_Ubuntu_배포가이드.md`** ← 완벽 가이드
- **`docs/AWS_Ubuntu_설정완료.md`** ← 설정 완료 보고서

### 실시간 반영
- **`docs/실시간반영가이드.md`** ← Windows & Linux 가이드

### 프로젝트 정보
- **`README.md`** ← 메인 문서
- **`START_HERE.md`** ← 빠른 시작
- **`CHANGELOG.md`** ← 변경 이력

---

## 🎯 환경별 추천 방법

### 로컬 개발 (Windows)
```bash
hot-reload.bat
# → 파일 저장하면 5초만에 반영!
```

### 로컬 개발 (Linux)
```bash
./hot-reload.sh
# → 파일 저장하면 5초만에 반영!
```

### AWS Ubuntu 프로덕션
```bash
./setup-systemd.sh
sudo systemctl start futures-dashboard
# → 가장 안정적!
```

### Docker 환경
```bash
# Windows
docker-auto-deploy.bat

# Linux
./docker-auto-deploy.sh
```

### CI/CD (GitHub Actions)
```bash
git push origin main
# → 자동 빌드 및 배포!
```

---

## 🆘 트러블슈팅

### Q1: AWS에서 8080 포트 접속이 안됨
**A:** 보안 그룹 설정 확인!
```
EC2 → Security Groups → Inbound Rules
포트 8080 추가: 0.0.0.0/0
```

### Q2: "Permission denied" (Linux)
**A:** 실행 권한 부여
```bash
chmod +x *.sh
chmod +x mvnw
```

### Q3: 메모리 부족 (t2.micro)
**A:** 스왑 메모리 추가
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Q4: systemd 서비스가 시작 안됨
**A:** 로그 확인
```bash
sudo journalctl -u futures-dashboard -n 50
sudo systemctl status futures-dashboard
```

### Q5: Git clone이 안됨 (Private Repo)
**A:** SSH 키 설정
```bash
ssh-keygen -t rsa -b 4096
cat ~/.ssh/id_rsa.pub
# → GitHub에 공개키 등록
```

---

## 🎊 완료!

**축하합니다! 이제 모든 환경에서 실행할 수 있습니다!**

### ✅ Windows
- hot-reload.bat
- auto-deploy.bat
- docker-auto-deploy.bat

### ✅ Linux / AWS Ubuntu
- ./hot-reload.sh
- ./auto-deploy.sh
- ./setup-systemd.sh
- ./docker-auto-deploy.sh

### ✅ CI/CD
- GitHub Actions (Git Push로 자동 배포!)

---

## 📞 빠른 참고

### Windows 개발
```bash
hot-reload.bat
```

### AWS Ubuntu 프로덕션
```bash
./setup-systemd.sh
sudo systemctl start futures-dashboard
```

### 배포
```bash
git push origin main
```

**이게 전부입니다!** 🚀

---

**작성일**: 2025-12-21
**환경**: Windows & Linux (AWS Ubuntu)
**상태**: ✅ 완료

**이제 AWS Ubuntu에서도 완벽하게 실행됩니다!** 🎉
