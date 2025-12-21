# 📁 프로젝트 구조

## 루트 디렉토리

```
futures-options-dashboard/
│
├── src/                          # 소스 코드
│   ├── main/
│   │   ├── java/                 # Java 소스 파일
│   │   └── resources/            # 리소스 파일
│   │       ├── static/           # 정적 파일 (CSS, JS)
│   │       └── templates/        # HTML 템플릿
│   └── test/                     # 테스트 코드
│
├── target/                       # 빌드 결과물
│
├── docs/                         # 📚 문서 (정리됨)
│   ├── archive/                  # 과거 개발 이력
│   ├── AWS배포*.md               # AWS 배포 가이드
│   ├── *가이드.md                # 각종 가이드 문서
│   ├── 페이지*.md                # 페이지 공유 관련 문서
│   └── *.md                      # 기타 설명 문서
│
├── test-data/                    # 🧪 테스트 데이터 (정리됨)
│   ├── *.json                    # 테스트용 JSON 파일
│   ├── *.csv                     # 테스트용 CSV 파일
│   └── output.log                # 로그 파일
│
├── pom.xml                       # Maven 설정
├── Dockerfile                    # Docker 이미지 빌드
├── docker-compose.yml            # Docker Compose 설정
├── build-for-aws.bat             # AWS 빌드 스크립트
├── mvnw.cmd                      # Maven Wrapper
├── kis_token.cache               # KIS API 토큰 캐시
└── README.md                     # 프로젝트 메인 문서
```

## 주요 소스 파일

### Java 패키지 구조
```
com.trading.dashboard/
├── DashboardApplication.java    # 메인 애플리케이션
├── config/                       # 설정 클래스
├── controller/                   # REST API 컨트롤러
├── dto/                          # 데이터 전송 객체
├── model/                        # 도메인 모델
├── service/                      # 비즈니스 로직
└── util/                         # 유틸리티 클래스
```

### 정적 리소스
```
resources/static/
├── css/
│   └── dashboard.css            # 대시보드 스타일
└── js/
    └── dashboard.js             # 대시보드 스크립트
```

### 템플릿
```
resources/templates/
└── dashboard.html               # 메인 대시보드 페이지
```

## 문서 분류

### 📚 docs/ - 주요 가이드
- **AWS배포_빠른시작.md**: AWS 배포 빠른 시작 가이드
- **AWS배포가이드.md**: 상세 AWS 배포 가이드
- **실행가이드.md**: 로컬 실행 가이드
- **빠른참고가이드.md**: 빠른 참고 문서
- **한국투자증권API연동가이드.md**: KIS API 연동 방법
- **페이지공유_빠른사용법.md**: 페이지 공유 기능 사용법
- **페이지공유_사용예시.md**: 페이지 공유 예시
- **페이지공유방법.md**: 페이지 공유 상세 방법
- **페이지상태전달가이드.md**: 상태 전달 가이드
- **장시간체크기능.md**: 장 시간 체크 기능 설명
- **전거래일데이터표시.md**: 전거래일 데이터 표시 설명
- **종목코드_최종확정_20251220.md**: 종목코드 확정 내역
- **최종완성_실제종목코드적용.md**: 종목코드 적용 완료
- **프로젝트완성요약.md**: 프로젝트 완성 요약
- **프로젝트완성요약_최종.md**: 최종 프로젝트 요약
- **데이터소스설명.md**: 데이터 소스 설명
- **화면설명.md**: 화면 구성 설명
- **한국투자증권_완성요약.md**: KIS API 연동 완성 요약
- **지금바로사용하세요.md**: 즉시 사용 가이드

### 📦 docs/archive/ - 개발 이력
과거 개발 과정에서 생성된 문서들이 보관되어 있습니다.
- 버그 수정 기록
- UI 개선 이력
- 기능 추가 완료 문서
- 문제 해결 과정

### 🧪 test-data/ - 테스트 데이터
개발 및 테스트에 사용된 데이터 파일들입니다.
- JSON 샘플 데이터
- CSV 시세 데이터
- 로그 파일

## 설정 파일

### application.properties
로컬 개발 환경 설정

### application-prod.properties
프로덕션 환경 설정

### pom.xml
Maven 의존성 및 빌드 설정

## 빌드 결과물

### target/
- **futures-options-dashboard-1.0.0.jar**: 실행 가능한 JAR 파일
- **classes/**: 컴파일된 클래스 파일
- **generated-sources/**: 자동 생성된 소스

## 정리 기준

1. **docs/**: 문서는 모두 docs 폴더로
   - 주요 가이드: docs/ 루트
   - 과거 이력: docs/archive/

2. **test-data/**: 테스트 파일은 test-data 폴더로
   - JSON/CSV 데이터
   - 로그 파일

3. **루트**: 필수 파일만 유지
   - 설정 파일 (pom.xml, Dockerfile 등)
   - 실행 스크립트
   - README.md

이렇게 정리하여 프로젝트 구조가 더 깔끔하고 관리하기 쉬워졌습니다! 🎉
