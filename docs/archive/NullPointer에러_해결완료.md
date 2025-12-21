# NullPointer 에러 해결 완료 (2025-12-20)

## 🔍 문제 분석

### 에러 내용
```
Error fetching futures price for A01603: Cannot invoke "JsonNode.asText(String)" 
because the return value of "JsonNode.get(String)" is null
```

### 원인
1. **API 응답 코드 체크 누락**: `rt_cd`가 "1"(에러)인 경우 처리 안 함
2. **Null 안전성 부족**: `output1.get("prpr")`이 null일 때 `.asText()` 호출
3. **에러 메시지 불명확**: 실제 API 에러 원인 파악 어려움

## ✅ 해결 방법

### 1. API 응답 코드 체크 추가
```java
// rt_cd 체크 (성공: "0", 실패: "1")
String rtCd = root.path("rt_cd").asText("");
if (!"0".equals(rtCd)) {
    log.warn("API error for futures {}: {} - {}", 
            code, root.path("msg_cd").asText(""), root.path("msg1").asText(""));
    return null;
}
```

### 2. 안전한 필드 추출 (`.get()` → `.path()`)
```java
// 이전 (위험): NullPointerException 가능
output1.get("prpr").asText("0")

// 개선 (안전): null이면 빈 노드 반환
output1.path("prpr").asText("0")
```

### 3. 상세 로깅 추가
```java
// 디버깅용 API 응답 로깅
log.debug("Futures {} API response: {}", code, response.body());

// 에러 상황별 명확한 메시지
log.warn("API error for futures {}: {} - {}", code, msgCode, msg1);
log.warn("No output1 data for futures {}", code);
log.warn("HTTP error {} for futures {}: {}", statusCode, code, body);
```

## 📊 수정된 코드

### `KisApiService.java`

#### 선물 시세 조회 (`fetchFuturesPrice`)
```java
if (response.statusCode() == 200) {
    JsonNode root = objectMapper.readTree(response.body());
    
    // 1️⃣ API 응답 로깅
    log.debug("Futures {} API response: {}", code, response.body());
    
    // 2️⃣ 성공 코드 체크
    String rtCd = root.path("rt_cd").asText("");
    if (!"0".equals(rtCd)) {
        log.warn("API error for futures {}: {} - {}", 
                code, root.path("msg_cd").asText(""), root.path("msg1").asText(""));
        return null;
    }
    
    // 3️⃣ 안전한 필드 추출 (.path() 사용)
    JsonNode output1 = root.get("output1");
    if (output1 != null && !output1.isEmpty()) {
        FuturesData futures = new FuturesData();
        futures.setCurrentPrice(new BigDecimal(output1.path("prpr").asText("0")));
        futures.setChangeAmount(new BigDecimal(output1.path("prdy_vrss").asText("0")));
        // ...
        return futures;
    }
}
```

#### 옵션 시세 조회 (`fetchOptionPrice`)
- 선물과 동일한 로직 적용
- `rt_cd` 체크, `.path()` 사용, 상세 로깅

## 🎯 이제 발생하는 에러 메시지

### 1️⃣ API 에러 (종목코드 없음)
```
WARN: API error for futures A01603: OPSQ0002 - 존재하는 종목코드가 아닙니다
```
→ **의미**: 종목코드가 잘못되었거나 해당 종목이 거래 중이 아님

### 2️⃣ 응답 데이터 없음
```
WARN: No output1 data for futures A01603
```
→ **의미**: API는 성공했지만 데이터가 비어있음

### 3️⃣ HTTP 에러
```
WARN: HTTP error 403 for futures A01603: {"error":"..."}
```
→ **의미**: 인증 실패 또는 권한 없음

## 🔧 다음 단계

### 현재 상황
- ✅ **NullPointerException 해결 완료**
- ❌ **종목코드 오류 발생**: `OPSQ0002 - 존재하는 종목코드가 아닙니다`

### 해결 방법
1. **한국투자증권 API 문서 확인**: 실제 거래 가능한 종목코드 확인
2. **종목코드 형식 검증**: 
   - `A01603` → 실제로는 `101T3000` 같은 형식일 수도 있음
   - 월물 코드: 3(3월), 6(6월), 9(9월), C(12월)
3. **API 테스트**: Postman 등으로 직접 API 호출해서 응답 확인

## 📝 참고

### JsonNode 안전 메서드
```java
// ❌ 위험: NullPointerException 가능
node.get("field").asText()

// ✅ 안전: null이면 빈 노드 반환
node.path("field").asText()
node.path("field").asText("기본값")

// ✅ 안전: 존재 여부 체크
if (node.has("field")) {
    String value = node.get("field").asText();
}
```

### 한국투자증권 API 응답 구조
```json
{
  "rt_cd": "0",           // 성공: "0", 실패: "1"
  "msg_cd": "OPSQ0002",   // 에러 코드
  "msg1": "에러 메시지",
  "output1": {
    "prpr": "367.50",     // 현재가
    "prdy_vrss": "2.50",  // 전일대비
    "prdy_ctrt": "0.68",  // 등락률
    "acml_vol": "12345"   // 누적거래량
  }
}
```

## 🎉 완료!
- ✅ **NullPointerException 완전 해결**
- ✅ **명확한 에러 메시지 제공**
- ✅ **디버깅 정보 충분**

이제 **종목코드만 정확히 수정**하면 정상 동작합니다! 🚀
