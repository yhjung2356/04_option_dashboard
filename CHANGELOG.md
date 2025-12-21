# 📝 변경 이력 (Changelog)

## [1.0.1] - 2025-12-21

### ✨ 추가됨 (Added)
- **투자자 안내문 기능**: 헤더에 느낌표 버튼 추가하여 투자 위험 고지 팝업 표시
  - 투자 위험 고지
  - 주요 유의사항 (고위험 상품, 레버리지 위험, 데이터 정확성, 투자 책임)
  - 권장사항 (충분한 학습, 금융상품 설명서 확인, 여유 자금 투자, 전문가 상담)
- **프로젝트 구조 문서**: `docs/PROJECT_STRUCTURE.md` 추가
- **업데이트 문서**: `docs/UPDATE_20251221.md` 추가
- **변경 이력**: `CHANGELOG.md` (이 파일) 추가

### 🔧 변경됨 (Changed)
- **저장 버튼 → 느낌표 버튼**: 사용자 안전을 위한 안내문 표시 기능으로 변경
- **시장 상태 표시 통합**: "주말 휴장"을 "장 마감"으로 통합하여 더 직관적인 메시지 제공

### 📁 정리됨 (Organized)
- **docs/ 폴더**: 모든 마크다운 문서를 `docs/` 폴더로 이동 (20개 이상)
- **docs/archive/ 폴더**: 과거 개발 이력 문서를 `docs/archive/`로 보관
- **test-data/ 폴더**: 테스트 JSON/CSV 파일을 `test-data/` 폴더로 이동

### 🎨 UI 개선
- 느낌표 버튼: 주황색 배경, 흰색 아이콘
- 투자자 안내문 모달: 슬라이드인 애니메이션, 깔끔한 레이아웃
- 명확한 경고 표시: 빨간색 아이콘과 굵은 텍스트

---

## [1.0.0] - 2025-12-20

### ✨ 주요 기능
- **실시간 선물/옵션 데이터**: WebSocket을 통한 실시간 데이터 스트림
- **시장 개요**: 선물/옵션 거래량, 거래대금, 미결제약정
- **Put/Call Ratio**: 거래량, 미결제, 거래대금 기준 3가지 비율
- **Greeks 요약**: Delta, Gamma, Theta, Vega, IV (ATM 기준)
- **거래량 TOP 5**: 거래량 상위 5개 종목
- **미결제약정 TOP 5**: 미결제약정 상위 5개 종목
- **옵션 체인**: ATM 기준 콜/풋 옵션 체인 with Greeks
- **시장 심리 지표**: IV 지수 및 투자 심리 게이지

### 🚀 기술 스택
- **Backend**: Spring Boot 3.2.0, Java 17
- **Frontend**: Vanilla JavaScript, HTML5, CSS3
- **Communication**: WebSocket (STOMP), REST API
- **External API**: 한국투자증권 KIS API

### 📊 데이터 소스
- **KIS API**: 한국투자증권 실시간 시세 데이터
- **시뮬레이션 모드**: 개발/테스트용 모의 데이터

### 🎯 페이지 공유 기능
- 클립보드 복사 (Ctrl+Shift+C)
- 텍스트 파일 저장 (Ctrl+Shift+S)
- JSON 파일 저장 (Ctrl+Shift+J)
- 콘솔 출력

### 🕐 시장 시간 체크
- 주간장: 09:00 ~ 15:45
- 야간장: 18:00 ~ 익일 05:00
- 장 마감: 위 시간 외 (주말 포함)

### 🔄 동적 종목코드
- 전거래일 자동 계산 (주말, 공휴일, 연휴 고려)
- 실시간 종목코드 조회
- 월물 자동 전환

---

## 향후 계획

### 🎯 예정된 기능
- [ ] 알림 기능 (특정 조건 도달 시 알림)
- [ ] 차트 시각화 (가격 및 Greeks 차트)
- [ ] 사용자 설정 저장 (로컬스토리지)
- [ ] 다크 모드 지원
- [ ] 모바일 반응형 디자인 개선

### 🔧 개선 계획
- [ ] 성능 최적화
- [ ] 에러 핸들링 강화
- [ ] 테스트 커버리지 향상
- [ ] API 문서 자동화

---

## 기여 방법

버그 리포트, 기능 제안, 코드 기여를 환영합니다!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

---

## 연락처

프로젝트 관련 문의: [GitHub Issues](https://github.com/your-repo/issues)

---

**Last Updated**: 2025-12-21
