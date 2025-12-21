# 🚀 AWS 배포 빠른 시작

## ⚡ 가장 빠른 방법: EC2 직접 배포

### 1️⃣ Windows에서 빌드
```cmd
build-for-aws.cmd
```

결과: `target\futures-options-dashboard-1.0.0.jar` (약 60-80MB)

---

### 2️⃣ EC2 인스턴스 생성

**AWS Console → EC2 → 인스턴스 시작**

- **AMI**: Amazon Linux 2023
- **타입**: t2.micro (프리티어) 또는 t3.small
- **키 페어**: 새로 생성 → `.pem` 파일 다운로드
- **보안 그룹**:
  - SSH (22) - 내 IP
  - Custom TCP (8080) - 0.0.0.0/0

---

### 3️⃣ JAR 파일 업로드

**PowerShell에서 실행:**
```powershell
scp -i "your-key.pem" target\futures-options-dashboard-1.0.0.jar ec2-user@YOUR-EC2-IP:/home/ec2-user/
```

**IP 확인**: EC2 콘솔에서 퍼블릭 IPv4 주소 복사

---

### 4️⃣ SSH 접속

```powershell
ssh -i "your-key.pem" ec2-user@YOUR-EC2-IP
```

---

### 5️⃣ Java 17 설치

```bash
sudo dnf install -y java-17-amazon-corretto-devel
java -version
```

---

### 6️⃣ 애플리케이션 실행

**A. 즉시 실행 (테스트)**
```bash
java -jar futures-options-dashboard-1.0.0.jar
```

**B. 백그라운드 실행**
```bash
nohup java -jar futures-options-dashboard-1.0.0.jar > app.log 2>&1 &
```

**C. Systemd 서비스 (추천)**
```bash
# 서비스 파일 생성
sudo tee /etc/systemd/system/trading.service > /dev/null <<EOF
[Unit]
Description=Trading Dashboard
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/futures-options-dashboard-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# 서비스 시작
sudo systemctl daemon-reload
sudo systemctl enable trading
sudo systemctl start trading
```

---

### 7️⃣ 접속 확인

브라우저에서:
```
http://YOUR-EC2-IP:8080
```

---

## 🐳 Docker 방법

### 1️⃣ 로컬에서 이미지 빌드
```bash
docker build -t trading-dashboard .
docker save trading-dashboard > trading-dashboard.tar
```

### 2️⃣ EC2에 전송
```powershell
scp -i "your-key.pem" trading-dashboard.tar ec2-user@YOUR-EC2-IP:/home/ec2-user/
```

### 3️⃣ EC2에서 실행
```bash
# Docker 설치
sudo dnf install -y docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user

# 이미지 로드 & 실행
docker load < trading-dashboard.tar
docker run -d -p 8080:8080 --name trading --restart unless-stopped trading-dashboard
```

---

## 📋 체크리스트

- [ ] JAR 파일 빌드 (`build-for-aws.cmd`)
- [ ] EC2 인스턴스 생성 (t2.micro)
- [ ] 보안 그룹 설정 (22, 8080)
- [ ] PEM 키 다운로드
- [ ] Java 17 설치
- [ ] JAR 파일 업로드
- [ ] 애플리케이션 실행
- [ ] 브라우저 접속 테스트

---

## 🛠️ 유용한 명령어

### 로그 확인
```bash
# Systemd
sudo journalctl -u trading -f

# Nohup
tail -f app.log

# Docker
docker logs -f trading
```

### 재시작
```bash
# Systemd
sudo systemctl restart trading

# Docker
docker restart trading
```

### 중지
```bash
# Systemd
sudo systemctl stop trading

# Docker
docker stop trading
```

---

## 🚨 문제 해결

### 포트가 이미 사용 중
```bash
sudo lsof -i :8080
sudo kill -9 [PID]
```

### 메모리 부족 (t2.micro)
```bash
# Swap 추가
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### 접속 안됨
1. 보안 그룹에 8080 포트 열려있는지 확인
2. 애플리케이션이 실행 중인지 확인: `sudo lsof -i :8080`
3. 방화벽 확인: `sudo firewall-cmd --list-all`

---

## 💰 예상 비용

**프리티어 (첫 12개월)**
- EC2 t2.micro: 월 750시간 무료
- **비용: $0**

**프리티어 이후**
- EC2 t3.small: ~$15/월
- 데이터 전송: ~$5/월
- **총: ~$20/월**

---

## 🎉 완료!

배포 후 접속:
```
http://YOUR-EC2-PUBLIC-IP:8080
```

---

## 📚 자세한 가이드

더 자세한 내용은 `AWS배포가이드.md` 참조
