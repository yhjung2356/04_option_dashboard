# 🚀 AWS Ubuntu 빠른 배포 가이드

## ⚡ 3분만에 배포 완료!

### Step 1: EC2 접속
```bash
ssh -i your-key.pem ubuntu@your-ec2-ip
```

### Step 2: 한 줄로 설치 및 실행
```bash
sudo mkdir -p /opt/futures-dashboard && \
sudo chown -R ubuntu:ubuntu /opt/futures-dashboard && \
cd /opt/futures-dashboard && \
git clone <your-repo-url> . && \
chmod +x *.sh && \
./setup-ubuntu.sh && \
./quick-start.sh
```

**끝!** 🎉

---

## 📋 설명

### 위 명령어가 하는 일:
1. ✅ 디렉토리 생성
2. ✅ Git 클론
3. ✅ 실행 권한 부여
4. ✅ Ubuntu 환경 설정 (Java, Docker 등)
5. ✅ 애플리케이션 실행

### 실행 후:
```
브라우저에서: http://your-ec2-ip:8080
```

---

## 🎯 추천 방법 (프로덕션)

### systemd 서비스로 실행 (가장 안정적!)
```bash
cd /opt/futures-dashboard
./setup-systemd.sh
sudo systemctl start futures-dashboard
sudo systemctl status futures-dashboard
```

**장점:**
- ✅ 서버 재부팅 시 자동 시작
- ✅ SSH 끊어져도 계속 실행
- ✅ 크래시 시 자동 재시작
- ✅ 로그 자동 관리

---

## 🔄 업데이트 방법

### 코드 업데이트 후 재배포
```bash
cd /opt/futures-dashboard
./auto-deploy.sh
```

**또는 systemd 사용 시:**
```bash
cd /opt/futures-dashboard
git pull
./mvnw clean package -DskipTests
sudo systemctl restart futures-dashboard
```

---

## 📊 유용한 명령어

### 상태 확인
```bash
# systemd 상태
sudo systemctl status futures-dashboard

# 프로세스 확인
ps aux | grep java
lsof -i :8080
```

### 로그 확인
```bash
# systemd 로그
sudo journalctl -u futures-dashboard -f

# 일반 로그
tail -f /var/log/futures-dashboard/app.log
```

### 중지/재시작
```bash
# systemd
sudo systemctl stop futures-dashboard
sudo systemctl restart futures-dashboard

# 또는
./stop.sh
./restart.sh
```

---

## 🔒 보안 그룹 설정

### AWS Console에서:
```
EC2 → Security Groups → Inbound Rules
```

**필수 규칙:**
- **포트 22 (SSH)**: Your IP 또는 필요한 IP만
- **포트 8080 (HTTP)**: 0.0.0.0/0 (전체 공개) 또는 특정 IP

---

## 💡 팁

### 1. t2.micro 메모리 부족 시
```bash
# 스왑 메모리 추가 (2GB)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 2. 도메인 연결 시
```bash
# Nginx 설치 및 설정
sudo apt-get install -y nginx

# /etc/nginx/sites-available/futures-dashboard 생성
# server { listen 80; ... proxy_pass http://localhost:8080; }

sudo ln -s /etc/nginx/sites-available/futures-dashboard /etc/nginx/sites-enabled/
sudo systemctl restart nginx
```

### 3. SSL 인증서 (Let's Encrypt)
```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

---

## 🆘 문제 해결

### "Permission denied"
```bash
chmod +x *.sh
chmod +x mvnw
```

### 포트 8080이 이미 사용중
```bash
./stop.sh
# 또는
sudo kill -9 $(lsof -t -i:8080)
```

### Java가 없음
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk
```

---

## 📞 빠른 명령어 모음

```bash
# 실행
./quick-start.sh                          # 대화형
sudo systemctl start futures-dashboard    # systemd

# 중지
sudo systemctl stop futures-dashboard

# 재시작
sudo systemctl restart futures-dashboard

# 로그
sudo journalctl -u futures-dashboard -f

# 상태
sudo systemctl status futures-dashboard
```

---

## 🎉 완료!

**이제 AWS Ubuntu에서 완벽하게 실행됩니다!**

**문제가 있나요?**
→ `docs/AWS_Ubuntu_배포가이드.md` 참고

**자동 배포 설정하고 싶나요?**
→ GitHub Actions 이미 설정되어 있습니다!
→ GitHub Secrets만 추가하면 Git Push로 자동 배포!

```
Secrets 추가:
- EC2_HOST: your-ec2-ip
- EC2_USER: ubuntu
- EC2_SSH_KEY: (SSH 개인키 전체 내용)
```

끝! 🚀
