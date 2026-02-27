# Marathon (러닝 트래커 앱) 🏃‍♂️

Marathon은 사용자의 러닝 데이터를 기록하고 분석하여 건강한 운동 습관을 돕는 안드로이드 애플리케이션입니다.

## 📋 프로젝트 개요
이 프로젝트는 최신 안드로이드 개발 스택을 활용하여 제작되었으며, 정밀한 위치 추적과 러닝 관련 지표(페이스, 케이던스, 심박수 등)를 제공하는 데 중점을 둡니다.

## ✨ 주요 기능
- **러닝 기록**: GPS 기반 실시간 위치 추적 및 경로 기록.
- **실시간 지표**: 현재 페이스, 평균 페이스, 거리, 시간, 칼로리 소모량 계산.
- **활동 내역**: 과거 러닝 기록의 요약 및 상세 보기(Google Maps 경로 포함).
- **음성 알림**: 운동 시작, 일시 정지, 재개 및 매 km마다 음성 가이드 제공.
- **상태 관리**: 다양한 모드(Idle, Running, Paused)를 통한 직관적인 UI 제공.

## 🛠 기술 스택
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture + Multi-module (feature, core, app)
- **Dependency Injection**: Hilt
- **Data Persistence**: Room Database
- **Networking/Async**: Coroutines, Flow
- **Maps & Location**: Google Maps SDK for Android, Google Play Services Location
- **Healthcare**: Health Connect (헬스 커넥트 연동 준비 중)

## 🏗 프로젝트 구조
```text
├── app/               # 메인 애플리케이션 모듈 (네비게이션 및 진입점)
├── core/
│   └── healthcare/    # 데이터 레이어, 리포지토리 및 공통 도메인 로직
├── feature/
│   ├── history/       # 러닝 기록 리스트 및 상세 화면
│   └── recommend/     # 러닝 코스 추천 기능 (구현 예정)
└── .github/           # CI/CD (GitHub Actions) 설정
```

## 🚀 CI/CD 구성
GitHub Actions를 활용하여 모든 Pull Request와 Push에 대해 다음을 자동화합니다.
- **Unit Tests**: 모든 모듈의 단위 테스트 실행 및 결과 리포트 저장.
- **Build**: Debug APK 빌드 및 결과물 업로드.
- **Concurrency**: 중복 워크플로우 실행 방지 설정.

## ⚙️ 설정 방법
1. 프로젝트를 클론합니다.
2. `local.properties` 파일에 `MAPS_API_KEY`를 설정합니다.
3. Android Studio에서 최신 JDK 17을 사용하여 빌드합니다.

---
*이 프로젝트는 지속적으로 발전하고 있습니다.*
