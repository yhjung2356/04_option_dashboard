# 🔍 한국투자증권 API 데이터 수신 문제 해결

## ❌ 문제 상황

**증상:** API Key를 입력했는데 전거래일 데이터가 안 들어옴

---

## 🔧 수정 내용

### 1. **잘못된 API 엔드포인트 사용**

**Before (잘못됨):**
```java
// 국내주식 API 사용 (선물/옵션 아님!)
.uri(URI.create(config.getBaseUrl() + "/uapi/domestic-stock/v1/quotations/inquire-price"))
.header("tr_id", "FHKST01010100")  // 주식용 TR
```

**After (수정):**
```java
// 파생상품(선물/옵션) 전용 API
.uri(URI.create(config.getBaseUrl() + "/uapi/domestic-futureoption/v1/quotations/inquire-price"))
.header("tr_id", "FHKST01010000")  // 선물옵션용 TR
```

### 2. **로그 레벨 변경**

DEBUG 레벨로 변경하여 API 응답 확인 가능:
```properties
logging.level.com.trading.dashboard=DEBUG
logging.level.com.trading.dashboard.service.KisApiService=DEBUG
```

---

## 🎯 한국투자증권 선물/옵션 API 정보

### 파생상품 시세 조회 API

**엔드포인트:**
```
GET /uapi/domestic-futureoption/v1/quotations/inquire-price
```

**Headers:**
```
Content-Type: application/json; charset=utf-8
authorization: Bearer {access_token}
appkey: {your_app_key}
appsecret: {your_app_secret}
tr_id: FHKST01010000
```

**Query Parameters:**
- `FID_COND_MRKT_DIV_CODE`: 
  - `F` = 선물
  - `O` = 옵션
- `FID_INPUT_ISCD`: 종목코드
  - 선물: `101T3000` (12월물)
  - 옵션 콜: `201TC400` (12월 콜 400)
  - 옵션 풋: `301TP400` (12월 풋 400)

---

## 📊 응답 필드 (예상)

### 공통 필드
```json
{
  "output": {
    "stck_prpr": "현재가",
    "prdy_vrss": "전일대비",
    "prdy_ctrt": "등락률",
    "acml_vol": "누적거래량",
    "acml_tr_pbmn": "누적거래대금",
    "stck_oprc": "시가",
    "stck_hgpr": "고가",
    "stck_lwpr": "저가"
  }
}
```

---

## 🚨 예상 문제점

### 1. **TR ID 불일치**

한국투자증권 문서에 따라 TR ID가 다를 수 있음:
- `FHKST01010000` - 파생상품 현재가
- `FHPST01060000` - 파생상품 체결가

### 2. **장 마감 시간**

현재 토요일 20시 → 장이 닫혀있어서 데이터 없을 수 있음

### 3. **API 권한**

발급받은 API Key에 "파생상품 시세" 권한이 있는지 확인 필요

### 4. **계좌번호 형식**

```properties
# 8자리-2자리 형식인지 확인
kis.api.account-no=43602495-XX
```

---

## ✅ 확인 방법

### 1. 로그 확인

애플리케이션 실행 후 로그에서 다음 확인:

**성공 시:**
```
DEBUG - KIS API Response for 101T3000: {"output":{"stck_prpr":"400.50",...}}
INFO  - ✓ Loaded 6 KOSPI200 futures from KIS API
```

**실패 시:**
```
ERROR - KIS API error for 101T3000: 401 - {"msg_cd":"EGW00123","msg1":"..."}
```

### 2. 에러 코드별 해결

| 에러 코드 | 의미 | 해결방법 |
|-----------|------|----------|
| 401 | 인증 실패 | APP_KEY, APP_SECRET 확인 |
| 403 | 권한 없음 | API 권한 설정 확인 |
| 404 | 잘못된 경로 | TR ID 또는 엔드포인트 확인 |
| 429 | Rate Limit | 요청 간격 조절 |

---

## 🔄 다음 단계

### Plan A: 한투 API 문서 확인

1. https://apiportal.koreainvestment.com/apiservice 접속
2. "국내선물옵션" 또는 "파생상품" 섹션 찾기
3. 정확한 TR ID와 필드명 확인

### Plan B: KRX API로 전환

한투 API가 안 되면 KRX 전거래일 데이터 사용:
```properties
trading.data-source=KRX
```

### Plan C: 샘플 데이터

모두 실패 시 샘플 데이터 자동 생성:
```properties
trading.data-source=SAMPLE
```

---

## 🛠️ 임시 해결책

지금 당장 데이터를 보려면:

```properties
# application.properties 수정
trading.data-source=SAMPLE

# 또는
trading.demo-mode=true
```

그러면 샘플 데이터가 생성되어 화면에서 바로 확인 가능!

---

## 📞 추가 지원 필요

로그에서 다음 정보를 확인해주세요:

1. **접근 토큰 발급 성공 여부**
   ```
   INFO - ✓ Access token obtained successfully!
   ```

2. **API 호출 에러 메시지**
   ```
   ERROR - KIS API error for 101T3000: [상태코드] - [응답내용]
   ```

3. **응답 데이터 구조**
   ```
   DEBUG - KIS API Response for 101T3000: {...}
   ```

이 정보를 공유해주시면 정확한 원인 파악이 가능합니다!

---

## ✅ 수정 완료 파일

1. ✅ `KisApiService.java` - API 엔드포인트 수정
2. ✅ `application.properties` - 로그 레벨 DEBUG
3. ✅ 재빌드 완료

**다시 실행해서 로그를 확인해보세요!**

```bash
java -jar target\futures-options-dashboard-1.0.0.jar
```

로그 파일이나 콘솔 출력을 보여주시면 더 정확한 해결책을 드릴 수 있습니다! 🚀
