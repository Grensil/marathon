# Marathon

Nike Run Club에서 영감을 받은 Android 러닝 트래커 앱입니다. 실시간 GPS 추적, 페이스/케이던스/심박수 모니터링, 경로 기록, TTS 음성 안내 등을 지원합니다.

## 주요 기능

- **실시간 러닝 트래킹** - GPS 기반 거리, 속도, 페이스, 케이던스, 고도 실시간 측정
- **시작/일시중지/재개/종료** - 완전한 러닝 세션 제어
- **Google Maps 경로 표시** - 완료된 러닝의 경로를 실제 지도 위에 Polyline으로 시각화
- **TTS 음성 안내** - 킬로미터 마일스톤 도달 시 페이스, 시간 음성 안내
- **러닝 기록 저장** - Room DB를 통한 모든 세션 영구 보관
- **히스토리 목록** - 총 달린 거리, 시간, 횟수 통계 및 세션별 상세 조회
- **포그라운드 서비스** - 앱이 백그라운드에 있어도 GPS 추적 유지
- **다크 테마 UI** - NRC 스타일의 다크 테마 디자인

## 기술 스택

| 카테고리 | 기술 |
|---------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 아키텍처 | Clean Architecture (Domain/Data/Presentation) + MVVM |
| DI | Hilt |
| 비동기 | Coroutines + Flow |
| 로컬 DB | Room |
| 지도 | Google Maps SDK + Maps Compose |
| 위치 | FusedLocationProviderClient (Google Play Services) |
| 센서 | StepCounter + StepDetector 센서 |
| 헬스 | Health Connect API |
| 음성 | Android TextToSpeech |

## 모듈 구조

```
Marathon/
├── app/                          # 메인 앱 모듈
│   ├── MainActivity              # 엔트리 포인트
│   ├── MainNavGraph              # 네비게이션 그래프
│   ├── MainRoute                 # 라우트 정의
│   ├── service/
│   │   ├── RunningService        # 포그라운드 서비스
│   │   └── RunningTtsManager     # TTS 음성 안내 관리
│   └── ui/theme/                 # 다크 테마 (NeonGreen 액센트)
│
├── core/healthcare/              # 코어 헬스케어 모듈
│   ├── data/
│   │   ├── datasource/           # Health Connect 데이터소스
│   │   ├── local/                # Room DB (Entity, DAO, Database)
│   │   ├── mapper/               # Entity <-> Domain 모델 변환
│   │   ├── repository/           # Repository 구현체
│   │   └── sensor/               # GPS, StepCounter 센서
│   ├── di/                       # Hilt 모듈
│   └── domain/
│       ├── model/                # 도메인 모델 (RunHistory, RoutePoint, RunningMetrics)
│       ├── repository/           # Repository 인터페이스
│       └── usecase/              # UseCase 클래스
│
├── feature/history/              # 러닝 기록 피처 모듈
│   ├── HistoryScreen             # 메인 러닝 화면 (시작/일시중지/종료)
│   ├── HistoryListScreen         # 기록 목록 화면
│   ├── RunDetailScreen           # 상세 조회 (Google Maps 경로)
│   ├── RunningViewModel          # 러닝 상태 관리
│   └── RunningState              # UI 상태 데이터 클래스
│
└── feature/recommend/            # 추천 피처 모듈
```

## 빌드 환경

- **compileSdk:** 36
- **minSdk:** 26 (Android 8.0)
- **targetSdk:** 36
- **Java:** 11
- **Kotlin:** 2.1.0

## 설정

### 1. Google Maps API 키

`local.properties`에 API 키를 추가합니다:

```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

[Google Cloud Console](https://console.cloud.google.com/)에서 Maps SDK for Android를 활성화하고 API 키를 발급받으세요.

### 2. 빌드 및 실행

```bash
./gradlew assembleDebug
```

또는 Android Studio에서 직접 실행합니다.

## 필요 권한

| 권한 | 용도 |
|------|------|
| `ACCESS_FINE_LOCATION` | GPS 위치 추적 |
| `ACCESS_COARSE_LOCATION` | 대략적 위치 |
| `ACTIVITY_RECOGNITION` | 걸음 수 감지 |
| `POST_NOTIFICATIONS` | 포그라운드 서비스 알림 (Android 13+) |
| `FOREGROUND_SERVICE_LOCATION` | 백그라운드 위치 추적 |
| Health Connect 권한 | 심박수, 속도, 거리, 운동, 걸음 수 |

## 화면 구성

### Run 탭
- 대기 상태: START 버튼
- 러닝 중: 거리(대형), 경과 시간, 현재/평균 페이스, 심박수, 케이던스, 고도, 칼로리
- 일시중지: STOP + RESUME 버튼
- 완료 다이얼로그: 거리, 시간, 평균 페이스, 케이던스 요약

### History 탭
- 총 러닝 횟수, 총 거리, 총 시간 통계 카드
- 세션별 리스트 (날짜, 거리, 시간, 페이스)
- 세션 클릭 시 상세 화면으로 이동

### 상세 화면
- Google Maps 위에 경로 Polyline 표시
- 시작점(초록), 종료점(빨강) 마커
- 상세 통계 카드 (Duration, Avg Pace, Cadence, Heart Rate, Calories, Steps)
