# 🚀 한국투자증권 Open API 연동 가이드

## 📋 개요

이 프로젝트는 **한국투자증권 KIS Open API**를 통해 **실시간 선물/옵션 시세**를 받아옵니다!

---

## 🔗 주요 링크

### 1. 한국투자증권 개발자 포털
```
https://apiportal.koreainvestment.com
```
**기능:**
- 회원가입
- API Key 발급
- API 문서 확인
- 샘플 코드 다운로드

### 2. GitHub 공식 샘플
```
https://github.com/koreainvestment/open-trading-api
```
**제공 내용:**
- Python, Java, JavaScript 샘플
- WebSocket 실시간 시세 예제
- 주문 예제

### 3. API 문서
```
https://apiportal.koreainvestment.com/apiservice
```

---

## 🔑 API Key 발급 방법

### Step 1: 회원가입
1. https://apiportal.koreainvestment.com 접속
2. 회원가입 (한국투자증권 계좌 필요)

### Step 2: 앱 등록
1. 로그인 후 "My Application" 클릭
2. "앱 등록하기" 클릭
3. 앱 정보 입력:
   - **앱 이름**: "선물옵션 대시보드"
   - **앱 설명**: "실시간 시세 조회"
   - **권한**: 시세조회, 실시간시세

### Step 3: API Key 확인
발급 후 다음 정보를 메모:
- ✅ **APP KEY**: `PSxxxxxxxxxxxxxxxxxxxxx`
- ✅ **APP SECRET**: `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
- ✅ **계좌번호**: `12345678-01` (8자리-2자리)

---

## ⚙️ 설정 방법

### 1. application.properties 수정

파일 위치: `src/main/resources/application.properties`

```properties
# ===== 한국투자증권 Open API 설정 =====
# 발급받은 키 입력 (따옴표 없이)
kis.api.app-key=PSxxxxxxxxxxxxxxxxxxxxx
kis.api.app-secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
kis.api.account-no=12345678-01

# 실전투자 (실제 계좌)
kis.api.base-url=https://openapi.koreainvestment.com:9443

# 모의투자 (테스트용 - 실전과 동일한 API 구조)
# kis.api.base-url=https://openapivts.koreainvestment.com:29443

# WebSocket 실시간 시세
kis.websocket.url=ws://ops.koreainvestment.com:21000

# 데이터 소스 (KIS 사용)
trading.data-source=KIS
```

### 2. 모의투자 vs 실전투자

#### 모의투자 (추천 - 테스트용)
```properties
kis.api.base-url=https://openapivts.koreainvestment.com:29443
```
- ✅ **무료**
- ✅ 실전과 동일한 API
- ✅ 가상 자금으로 테스트
- ⚠️ 모의투자 계좌 별도 신청 필요

#### 실전투자
```properties
kis.api.base-url=https://openapi.koreainvestment.com:9443
```
- ✅ 실제 시세 데이터
- ⚠️ 실제 계좌 필요
- ⚠️ API 사용료 없음 (시세 조회는 무료)

---

## 🎯 구현된 기능

### 1. REST API 시세 조회 ✅

**KisApiService.java**

```java
// 접근 토큰 자동 발급 및 갱신
String token = getAccessToken();

// KOSPI200 선물 시세 조회
loadFuturesData();

// KOSPI200 옵션 시세 조회
loadOptionsData();
```

**지원 데이터:**
- ✅ 현재가, 시가, 고가, 저가
- ✅ 거래량, 거래대금
- ✅ 전일대비, 등락률
- ✅ 매수/매도 호가

### 2. WebSocket 실시간 시세 ✅

**KisWebSocketService.java**

```java
// WebSocket 연결
connect();

// 선물 시세 구독
subscribeFutures("101T3000");  // 12월물

// 옵션 시세 구독
subscribeOption("201TC400");  // 콜 400
subscribeOption("301TP400");  // 풋 400
```

**실시간 수신 데이터:**
- ⚡ 체결가
- ⚡ 체결량
- ⚡ 호가 변동
- ⚡ 1초 이내 업데이트

---

## 📊 데이터 로딩 우선순위

애플리케이션 시작 시:

```
1️⃣ 한국투자증권 KIS API (최우선)
   ├─ 성공 → 실시간 데이터 사용 ✅
   └─ 실패 ↓

2️⃣ KRX 공식 API (Fallback)
   ├─ 성공 → 전거래일 종가 데이터 ✅
   └─ 실패 ↓

3️⃣ 샘플 데이터 생성 (최후 수단)
   └─ 시뮬레이션 데이터 ⚠️
```

---

## 🔧 선물/옵션 코드 체계

### KOSPI200 선물

| 월물 | 코드 | 설명 |
|------|------|------|
| 12월 | 101T3000 | KOSPI200 선물 12월물 |
| 1월 | 101U3000 | KOSPI200 선물 1월물 |
| 2월 | 101H3000 | KOSPI200 선물 2월물 |
| 3월 | 101M3000 | KOSPI200 선물 3월물 |
| 6월 | 101J3000 | KOSPI200 선물 6월물 |
| 9월 | 101N3000 | KOSPI200 선물 9월물 |

### KOSPI200 옵션

| 타입 | 행사가 | 코드 | 설명 |
|------|--------|------|------|
| CALL | 380 | 201TC380 | 12월 콜 380 |
| CALL | 385 | 201TC385 | 12월 콜 385 |
| CALL | 390 | 201TC390 | 12월 콜 390 |
| ... | ... | ... | ... |
| PUT | 380 | 301TP380 | 12월 풋 380 |
| PUT | 385 | 301TP385 | 12월 풋 385 |
| PUT | 390 | 301TP390 | 12월 풋 390 |

**코드 규칙:**
- **2XXTCXXX**: 콜 옵션 (2로 시작)
- **3XXTPXXX**: 풋 옵션 (3으로 시작)
- **TC/TP**: 만기월 (T=12월, U=1월, H=2월...)

---

## 🚀 실행 방법

### 1. API Key 설정

`application.properties`에 발급받은 키 입력:
```properties
kis.api.app-key=YOUR_APP_KEY
kis.api.app-secret=YOUR_APP_SECRET
kis.api.account-no=YOUR_ACCOUNT_NO
```

### 2. 빌드

```bash
mvnw.cmd clean package -DskipTests
```

### 3. 실행

```bash
java -jar target\futures-options-dashboard-1.0.0.jar
```

### 4. 로그 확인

성공 시:
```
INFO  - Attempting to load KIS API data...
INFO  - Requesting new access token from KIS API...
INFO  - ✓ Access token obtained successfully!
INFO  - Loading KOSPI200 Futures data from KIS API...
INFO  - ✓ Loaded 6 KOSPI200 futures from KIS API
INFO  - Loading KOSPI200 Options data from KIS API...
INFO  - ✓ Loaded 82 KOSPI200 options from KIS API
INFO  - ✓ KIS API data loaded successfully!
```

### 5. WebSocket 실시간 시세

서비스 시작 후 자동 연결:
```
INFO  - Connecting to KIS WebSocket: ws://ops.koreainvestment.com:21000
INFO  - ✓ KIS WebSocket connected!
INFO  - ✓ Subscribed to futures: 101T3000
INFO  - ✓ Subscribed to option: 201TC400
INFO  - ✓ Subscribed to option: 301TP400
INFO  - 📈 [FUTURES] 101T3000 - Price: 400.50, Volume: 1523
INFO  - 📊 [OPTION] 201TC400 - Price: 5.25, Volume: 842
```

---

## ⚠️ 주의사항

### API 사용 제한

1. **Rate Limit**
   - 초당 요청 수 제한
   - 코드에서 `Thread.sleep(100)` 으로 조절

2. **접근 토큰**
   - 24시간 유효
   - 자동 갱신 구현됨

3. **WebSocket 연결**
   - 동시 구독 종목 수 제한
   - 주요 종목만 구독 권장

### 에러 처리

#### "접근 토큰 발급 실패"
```
원인: APP_KEY 또는 APP_SECRET 오류
해결: application.properties 확인
```

#### "WebSocket 연결 실패"
```
원인: 네트워크 또는 방화벽
해결: 포트 21000 허용
```

#### "시세 조회 오류"
```
원인: 종목 코드 오류 또는 장 마감
해결: 종목 코드 확인, 장 시간 확인
```

---

## 📱 화면 표시

### 실시간 데이터 (KIS API)

```
┌────────────────────────────────────────────┐
│ 📈 선물/옵션 거래 대시보드                 │
│ 🕐 14:35:22  🟢 주간장 거래중              │
│ 📡 실시간 데이터 (한국투자증권)            │
├────────────────────────────────────────────┤
│ ⚡ 실시간: KIS Open API를 통해             │
│    1초 이내 시세가 갱신됩니다               │
├────────────────────────────────────────────┤
│ 📊 KOSPI200 선물 12월물                    │
│ 현재가: 400.50 (+1.25, +0.31%)             │
│ 거래량: 152,345                            │
│                                            │
│ 📈 KOSPI200 옵션 콜 400                    │
│ 현재가: 5.25 (-0.15, -2.78%)               │
│ 거래량: 84,523                             │
└────────────────────────────────────────────┘
```

---

## 🔧 커스터마이징

### 구독 종목 추가

`KisWebSocketService.java` 수정:

```java
@Override
public void onOpen(ServerHandshake handshake) {
    sendApprovalRequest();
    
    // 선물 여러 월물 구독
    subscribeFutures("101T3000");  // 12월물
    subscribeFutures("101U3000");  // 1월물
    subscribeFutures("101H3000");  // 2월물
    
    // 옵션 여러 행사가 구독
    for (int strike = 380; strike <= 420; strike += 5) {
        subscribeOption(String.format("201TC%03d", strike));  // 콜
        subscribeOption(String.format("301TP%03d", strike));  // 풋
    }
}
```

### 실시간 데이터 DB 저장

`KisWebSocketService.java`의 `handleFuturesData()` 수정:

```java
private void handleFuturesData(JsonObject json) {
    // 파싱
    String code = ...;
    BigDecimal price = ...;
    
    // DB 업데이트
    FuturesData futures = futuresDataRepository.findBySymbol(code)
        .orElse(new FuturesData());
    futures.setCurrentPrice(price);
    futures.setTimestamp(LocalDateTime.now());
    futuresDataRepository.save(futures);
    
    // WebSocket 브로드캐스트
    marketDataWebSocketHandler.broadcast(futures);
}
```

---

## 📚 참고 자료

### 공식 문서
- API 명세: https://apiportal.koreainvestment.com/apiservice
- GitHub: https://github.com/koreainvestment/open-trading-api
- FAQ: https://apiportal.koreainvestment.com/faq

### TR 코드 (Transaction ID)
- **FHKST01010100**: 선물/옵션 현재가 조회
- **H0STCNT0**: 선물 실시간 체결가
- **H0STCNI0**: 옵션 실시간 체결가

### 응답 필드
| 필드명 | 설명 |
|--------|------|
| stck_prpr | 현재가 |
| prdy_vrss | 전일대비 |
| prdy_ctrt | 등락률 |
| acml_vol | 누적거래량 |
| acml_tr_pbmn | 누적거래대금 |
| stck_oprc | 시가 |
| stck_hgpr | 고가 |
| stck_lwpr | 저가 |
| stck_sdpr | 매수호가 |
| stck_mxpr | 매도호가 |

---

## ✅ 체크리스트

설정 완료 확인:

- [ ] 한국투자증권 계좌 개설
- [ ] API 포털 회원가입
- [ ] 앱 등록 및 API Key 발급
- [ ] `application.properties`에 키 입력
- [ ] 모의투자/실전투자 URL 선택
- [ ] 빌드 성공 (`mvnw.cmd clean package`)
- [ ] 실행 후 로그에서 "✓ Access token obtained" 확인
- [ ] WebSocket 연결 확인
- [ ] 웹 브라우저에서 데이터 확인 (http://localhost:8080)

---

## 🎉 완성!

**한국투자증권 실시간 API 연동이 완료되었습니다!**

이제 **실제 시장 데이터**를 **1초 이내**로 받아서 대시보드에 표시할 수 있습니다! 🚀

**질문이나 문제가 있으면 말씀해주세요!**
