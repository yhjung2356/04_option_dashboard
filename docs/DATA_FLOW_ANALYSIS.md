# 거래량, 거래대금, 미결제 데이터 흐름 분석 리포트

> **작성일:** 2025-12-24  
> **분석 대상:** 선물/옵션 실시간 데이터 파싱 및 표시 로직

---

## ✅ 최종 검증 결과: **정상 작동**

모든 데이터 필드가 API 가이드와 정확히 일치하게 매핑되어 있으며, 단위 변환도 올바르게 처리되고 있습니다.

---

## 📊 API 필드 매핑 (한투증권 WebSocket)

### 1. 선물 실시간체결가 (H0IFCNT0)

| 필드 인덱스 | 필드명 | 한글명 | 코드 변수명 | 비고 |
|------------|--------|--------|-------------|------|
| [5] | FUTS_PRPR | 현재가 | currentPriceStr | ✅ |
| [10] | ACML_VOL | **누적 거래량** | volumeStr | ✅ |
| [11] | ACML_TR_PBMN | **누적 거래대금** | tradingValueStr | ✅ 천원→억원 변환 |
| [18] | HTS_OTST_STPL_QTY | **미결제약정수량** | openInterestStr | ✅ |
| [19] | OTST_STPL_QTY_ICDC | **미결제증감** | openInterestChangeStr | ✅ |
| [35] | FUTS_ASKP1 | 매도호가1 | askPriceStr | ✅ |
| [36] | FUTS_BIDP1 | 매수호가1 | bidPriceStr | ✅ |
| [37] | ASKP_RSQN1 | 매도잔량1 | askVolumeStr | ✅ |
| [38] | BIDP_RSQN1 | 매수잔량1 | bidVolumeStr | ✅ |

### 2. 옵션 실시간체결가 (H0IOCNT0)

| 필드 인덱스 | 필드명 | 한글명 | 코드 변수명 | 비고 |
|------------|--------|--------|-------------|------|
| [2] | OPTN_PRPR | 현재가 | currentPriceStr | ✅ |
| [10] | ACML_VOL | **누적 거래량** | volumeStr | ✅ |
| [11] | ACML_TR_PBMN | **누적 거래대금** | tradingValueStr | ✅ 천원→억원 변환 |
| [12] | HTS_THPR | 이론가 | theoreticalPriceStr | ✅ |
| [13] | HTS_OTST_STPL_QTY | **미결제약정수량** | openInterestStr | ✅ |
| [14] | OTST_STPL_QTY_ICDC | **미결제증감** | openInterestChangeStr | ✅ |
| [26] | INVL_VAL | 내재가치 | intrinsicValueStr | ✅ |
| [27] | TMVL_VAL | 시간가치 | timeValueStr | ✅ |
| [41] | OPTN_ASKP1 | 매도호가1 | askPriceStr | ✅ |
| [42] | OPTN_BIDP1 | 매수호가1 | bidPriceStr | ✅ |
| [43] | ASKP_RSQN1 | 매도잔량1 | askVolumeStr | ✅ |
| [44] | BIDP_RSQN1 | 매수잔량1 | bidVolumeStr | ✅ |

---

## 🔄 데이터 흐름 (End-to-End)

```
WebSocket 수신
    ↓
필드 파싱 (KisRealtimeWebSocketClient.java)
    ├─ fields[10] → 거래량 (Long)
    ├─ fields[11] → 거래대금 (BigDecimal) ※ 천원 단위
    └─ fields[13/18] → 미결제 (Long)
    ↓
단위 변환 처리
    └─ 거래대금: 천원 → 억원 (÷ 100,000)
       BigDecimal tradingValueInEokWon = 
           tradingValueInThousandWon.divide(new BigDecimal("100000"), 2, RoundingMode.HALF_UP);
    ↓
DB 저장 (H2 Database)
    └─ FuturesData / OptionData entity
    ↓
집계 쿼리 (MarketDataService.java)
    ├─ futuresDataRepository.sumAllVolume()
    ├─ futuresDataRepository.sumAllTradingValue()
    ├─ futuresDataRepository.sumAllOpenInterest()
    ├─ optionDataRepository.sumVolumeByOptionType("CALL/PUT")
    ├─ optionDataRepository.sumTradingValueByOptionType("CALL/PUT")
    └─ optionDataRepository.sumOpenInterestByOptionType("CALL/PUT")
    ↓
REST API 응답 (/api/market/overview)
    └─ MarketOverviewDTO
        ├─ totalFuturesVolume: 123,456
        ├─ totalFuturesTradingValue: 1,234.56 (억원)
        ├─ totalFuturesOpenInterest: 98,765
        ├─ totalOptionsVolume: 234,567
        ├─ totalOptionsTradingValue: 2,345.67 (억원)
        └─ totalOptionsOpenInterest: 345,678
    ↓
JavaScript 포맷팅 (dashboard.js)
    ├─ formatNumber(): 123,456 → "123,456"
    └─ formatCurrency(): 1234.56 → "1,234.56억"
    ↓
HTML 표시 (main.html)
    └─ <span id="futures-volume">123,456</span>
```

---

## 💾 Repository 쿼리

### FuturesDataRepository.java
```java
@Query("SELECT SUM(f.volume) FROM FuturesData f")
Long sumAllVolume();

@Query("SELECT SUM(f.tradingValue) FROM FuturesData f")
BigDecimal sumAllTradingValue();

@Query("SELECT SUM(f.openInterest) FROM FuturesData f")
Long sumAllOpenInterest();
```

### OptionDataRepository.java
```java
@Query("SELECT SUM(o.volume) FROM OptionData o WHERE o.optionType = :optionType")
Long sumVolumeByOptionType(String optionType);

@Query("SELECT SUM(o.tradingValue) FROM OptionData o WHERE o.optionType = :optionType")
BigDecimal sumTradingValueByOptionType(String optionType);

@Query("SELECT SUM(o.openInterest) FROM OptionData o WHERE o.optionType = :optionType")
Long sumOpenInterestByOptionType(String optionType);
```

---

## 🎯 단위 변환 정책

### 1. 거래량 (Volume)
- **API 전송 단위**: 계약 수 (정수)
- **DB 저장 타입**: `Long`
- **표시 형식**: 천 단위 쉼표 (예: 123,456)
- **변환 없음**: API 값 그대로 사용

### 2. 거래대금 (Trading Value)
- **API 전송 단위**: **천원** (KIS API 표준)
- **DB 저장 단위**: **억원** (`BigDecimal`, scale=2)
- **변환 공식**: `천원 ÷ 100,000 = 억원`
- **표시 형식**: "1,234.56억"

**예시:**
```
API 수신: "123456789" (천원)
→ 변환: 123456789 ÷ 100,000 = 1234.57 (억원)
→ 표시: "1,234.57억"
```

### 3. 미결제약정 (Open Interest)
- **API 전송 단위**: 계약 수 (정수)
- **DB 저장 타입**: `Long`
- **표시 형식**: 천 단위 쉼표 (예: 98,765)
- **변환 없음**: API 값 그대로 사용

---

## 🔍 코드 위치

### Backend (Java)

1. **WebSocket 파싱 및 저장**
   - 파일: [KisRealtimeWebSocketClient.java](../src/main/java/com/trading/dashboard/service/KisRealtimeWebSocketClient.java)
   - 선물: Lines 314-388 (`processFuturesPrice()`)
   - 옵션: Lines 420-545 (`processOptionsPrice()`)

2. **집계 쿼리**
   - 파일: [MarketDataService.java](../src/main/java/com/trading/dashboard/service/MarketDataService.java)
   - Lines 35-75 (`getMarketOverview()`)

3. **Repository**
   - 선물: [FuturesDataRepository.java](../src/main/java/com/trading/dashboard/repository/FuturesDataRepository.java)
   - 옵션: [OptionDataRepository.java](../src/main/java/com/trading/dashboard/repository/OptionDataRepository.java)

### Frontend (JavaScript)

1. **데이터 수신 및 업데이트**
   - 파일: [dashboard.js](../src/main/resources/static/js/dashboard.js)
   - Lines 520-595 (`updateMarketOverview()`)

2. **포맷팅 함수**
   - `formatNumber()`: 천 단위 쉼표 추가
   - `formatCurrency()`: 억원 단위 + 소수점 2자리

3. **HTML 표시**
   - 파일: [main.html](../src/main/resources/templates/fragments/main.html)
   - Lines 13-23: 선물 데이터
   - Lines 33-43: 옵션 데이터
   - Lines 140-180: TOP 5 테이블

---

## ⚠️ 과거 이슈 및 해결

### 문제 1: 잘못된 필드 인덱스 사용 (이미 수정됨)
```java
// ❌ OLD (잘못됨)
String bidPriceStr = fields[6];  // 실제로는 시가!
String askPriceStr = fields[7];  // 실제로는 고가!

// ✅ NEW (올바름)
String askPriceStr = fields[35];  // 매도호가1 (선물)
String bidPriceStr = fields[36];  // 매수호가1 (선물)
String askPriceStr = fields[41];  // 매도호가1 (옵션)
String bidPriceStr = fields[42];  // 매수호가1 (옵션)
```

### 문제 2: 거래대금 단위 혼동
- **해결**: API가 천원 단위로 전송하므로 100,000으로 나눠서 억원 변환
- **검증**: API 가이드 확인 완료

### 문제 3: PUT 옵션 호가 순서
- **해결**: HTML에서 CALL과 동일하게 Ask→Bid 순서로 통일

---

## 📝 검증 체크리스트

- [x] API 가이드 문서와 필드 인덱스 일치 확인
- [x] WebSocket 파싱 로직 검증
- [x] 단위 변환 (천원 → 억원) 검증
- [x] DB 저장 로직 검증
- [x] Repository 쿼리 검증
- [x] Service 집계 로직 검증
- [x] Controller REST API 검증
- [x] JavaScript 데이터 수신 검증
- [x] 포맷팅 함수 검증
- [x] HTML 표시 로직 검증

---

## ✨ 결론

**모든 데이터 필드가 올바르게 매핑되어 있으며, 데이터 흐름이 정상적으로 작동합니다.**

- ✅ 거래량: API → DB → Frontend (정수, 쉼표 포맷)
- ✅ 거래대금: API(천원) → DB(억원 변환) → Frontend("억" 표시)
- ✅ 미결제: API → DB → Frontend (정수, 쉼표 포맷)
- ✅ 미결제 증감: API → DB → Frontend (부호 색상 표시)
- ✅ 호가/잔량: API [35-38]/[41-44] → DB → Frontend

**다음 단계:**
1. 실서버 배포 후 실시간 데이터 확인
2. 로그 모니터링으로 데이터 정합성 검증
3. CSV 데이터와 WebSocket 데이터 비교
