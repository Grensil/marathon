# Marathon 앱 - 실시간 러닝 트래커 구현 가이드

## 개요

Health Connect API를 사용하여 실시간으로 러닝 데이터를 추적하는 기능을 구현했습니다.
클린 아키텍처를 따르며, 다음 메트릭을 실시간으로 표시합니다:

- 진행 시간 (Elapsed Time)
- 러닝 페이스 (Current & Average Pace) - min/km
- 심박수 (Current & Average Heart Rate) - bpm
- 케이던스 (Current & Average Cadence) - steps per minute
- 거리 (Distance) - km
- 칼로리 (Calories)

## 프로젝트 구조

```
Marathon/
├── app/                                    # 앱 모듈
│   └── src/main/
│       ├── AndroidManifest.xml             # Health Connect 권한 설정
│       └── java/com/example/marathon/
│           ├── MainActivity.kt              # @AndroidEntryPoint
│           └── MyApplication.kt             # @HiltAndroidApp
│
├── core/healthcare/                        # 헬스케어 코어 모듈 (러닝 기능)
│   └── src/main/java/com/example/healthcare/
│       ├── domain/                         # 도메인 레이어
│       │   ├── model/
│       │   │   ├── RunningSession.kt       # 러닝 세션 엔티티
│       │   │   ├── RunningMetrics.kt       # 실시간 메트릭 엔티티
│       │   │   └── ExerciseType.kt         # 운동 타입 enum
│       │   ├── repository/
│       │   │   └── RunningRepository.kt    # 리포지토리 인터페이스
│       │   └── usecase/
│       │       ├── StartRunningSessionUseCase.kt
│       │       ├── StopRunningSessionUseCase.kt
│       │       ├── ObserveRunningMetricsUseCase.kt
│       │       ├── CheckHealthConnectPermissionsUseCase.kt
│       │       └── GetActiveSessionUseCase.kt
│       ├── data/                           # 데이터 레이어
│       │   ├── datasource/
│       │   │   └── HealthConnectDataSource.kt  # Health Connect API 통신
│       │   └── repository/
│       │       └── RunningRepositoryImpl.kt     # 리포지토리 구현
│       └── di/
│           └── HealthcareModule.kt         # Hilt 의존성 주입
│
└── feature/history/                        # 히스토리 피처 모듈
    └── src/main/java/com/example/history/
        ├── HistoryScreen.kt                # UI 화면
        ├── RunningViewModel.kt             # ViewModel
        ├── RunningState.kt                 # UI 상태
        └── PermissionHelper.kt             # 권한 요청 헬퍼
```

## 클린 아키텍처 레이어

### 1. Domain Layer (core/healthcare/domain)

**Entity (Model)**
- `RunningSession`: 러닝 세션 데이터
- `RunningMetrics`: 실시간 메트릭 (심박수, 페이스, 케이던스 등)
- `ExerciseType`: 운동 타입

**Repository Interface**
- `RunningRepository`: 러닝 데이터 접근 인터페이스

**Use Cases**
- `StartRunningSessionUseCase`: 러닝 세션 시작
- `StopRunningSessionUseCase`: 러닝 세션 종료
- `ObserveRunningMetricsUseCase`: 실시간 메트릭 관찰
- `CheckHealthConnectPermissionsUseCase`: 권한 확인
- `GetActiveSessionUseCase`: 활성 세션 조회

### 2. Data Layer (core/healthcare/data)

**Data Source**
- `HealthConnectDataSource`: Health Connect API와 직접 통신
  - 심박수, 속도, 걸음 수 데이터 조회
  - 운동 세션 시작/종료
  - 권한 관리

**Repository Implementation**
- `RunningRepositoryImpl`: Repository 인터페이스 구현
  - 2초마다 최신 데이터 폴링
  - 실시간 메트릭 Flow 제공
  - 평균값 계산

### 3. Presentation Layer (feature/history)

**ViewModel**
- `RunningViewModel`: UI 상태 관리
  - 러닝 세션 시작/종료
  - 실시간 메트릭 수집 및 평균 계산
  - 타이머 관리

**UI**
- `HistoryScreen`: Jetpack Compose UI
  - 실시간 메트릭 표시
  - START/STOP 버튼
  - 권한 요청 화면

## 주요 기능 설명

### 1. 실시간 데이터 폴링

Health Connect는 실시간 스트리밍을 제공하지 않으므로 폴링 방식을 사용합니다:

```kotlin
// RunningRepositoryImpl.kt
override fun observeRunningMetrics(): Flow<RunningMetrics> = flow {
    while (currentSessionId != null) {
        val currentTime = Instant.now()
        val pollingStartTime = currentTime.minusSeconds(5) // 최근 5초 데이터

        // 심박수, 속도, 걸음 수 데이터 조회
        // ...

        emit(metrics)
        delay(2000) // 2초마다 업데이트
    }
}
```

### 2. 페이스 계산

속도(m/s)를 페이스(min/km)로 변환:

```kotlin
fun speedToPace(speedInMetersPerSecond: Double): Double? {
    return if (speedInMetersPerSecond > 0) {
        (1000.0 / 60.0) / speedInMetersPerSecond
    } else null
}
```

### 3. 케이던스 계산

5초 동안의 걸음 수를 분당으로 변환:

```kotlin
val cadence = if (totalSteps > 0) {
    (totalSteps / 5.0) * 60.0
} else null
```

### 4. 평균값 계산

ViewModel에서 누적 리스트를 관리하여 평균 계산:

```kotlin
private val heartRateList = mutableListOf<Int>()
private val paceList = mutableListOf<Double>()
private val cadenceList = mutableListOf<Double>()

// 평균 심박수
averageHeartRate = if (heartRateList.isNotEmpty()) {
    heartRateList.average().toInt()
} else null
```

## Health Connect 권한

### AndroidManifest.xml에 추가된 권한:

```xml
<uses-permission android:name="android.permission.health.READ_HEART_RATE"/>
<uses-permission android:name="android.permission.health.WRITE_HEART_RATE"/>
<uses-permission android:name="android.permission.health.READ_SPEED"/>
<uses-permission android:name="android.permission.health.WRITE_SPEED"/>
<uses-permission android:name="android.permission.health.READ_DISTANCE"/>
<uses-permission android:name="android.permission.health.WRITE_DISTANCE"/>
<uses-permission android:name="android.permission.health.READ_EXERCISE"/>
<uses-permission android:name="android.permission.health.WRITE_EXERCISE"/>
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"/>

<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>
```

### 런타임 권한 요청

`PermissionHelper.kt`에서 Compose를 사용한 권한 요청:

```kotlin
val requestPermissions = rememberHealthConnectPermissionLauncher { granted ->
    if (granted) {
        viewModel.onPermissionGranted()
    }
}
```

## 의존성

### gradle/libs.versions.toml

```toml
[versions]
connectClient = "1.2.0-alpha02"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
lifecycleViewmodelCompose = "2.9.3"

[libraries]
androidx-connect-client = { module = "androidx.health.connect:connect-client", version.ref = "connectClient" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
```

### core/healthcare/build.gradle.kts

```kotlin
dependencies {
    implementation(libs.androidx.connect.client)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

### feature/history/build.gradle.kts

```kotlin
dependencies {
    implementation(project(":core:healthcare"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

## 사용 방법

1. **Health Connect 앱 설치 필요**
   - Android 14 이상: 시스템 앱으로 제공
   - Android 13 이하: Google Play에서 설치

2. **권한 요청**
   - 앱 실행 시 "Request Permissions" 버튼 클릭
   - Health Connect 권한 승인

3. **러닝 시작**
   - History 화면에서 "START" 버튼 클릭
   - 실시간 메트릭 확인

4. **러닝 종료**
   - "STOP" 버튼 클릭
   - 세션 종료

## UI 구성

```
┌─────────────────────────────┐
│   Running in Progress       │
│                             │
│       00:15:32              │ ← 경과 시간
│       2.45 km               │ ← 거리
│                             │
│ ┌──────────┐ ┌──────────┐  │
│ │Current   │ │Avg       │  │
│ │Pace      │ │Pace      │  │
│ │5:30      │ │5:45      │  │
│ │min/km    │ │min/km    │  │
│ └──────────┘ └──────────┘  │
│                             │
│ ┌──────────┐ ┌──────────┐  │
│ │Heart Rate│ │Cadence   │  │
│ │145       │ │170       │  │
│ │bpm       │ │spm       │  │
│ │Avg: 142  │ │Avg: 168  │  │
│ └──────────┘ └──────────┘  │
│                             │
│ ┌─────────────────────────┐│
│ │ Calories      245 kcal  ││
│ └─────────────────────────┘│
│                             │
│        ┌─────────┐          │
│        │  STOP   │          │ ← START/STOP 버튼
│        └─────────┘          │
└─────────────────────────────┘
```

## 참고사항

1. **실시간 데이터의 제약**
   - Health Connect는 실시간 스트리밍을 제공하지 않습니다
   - 2초마다 폴링하여 최신 5초 데이터를 조회합니다
   - 웨어러블 기기나 센서가 연결되어야 정확한 데이터를 얻을 수 있습니다

2. **테스트**
   - 에뮬레이터에서는 실제 센서 데이터를 얻을 수 없습니다
   - 실제 기기 + 웨어러블 기기(예: Galaxy Watch, Fitbit)로 테스트하세요

3. **권한**
   - 앱을 처음 실행하면 권한 요청 화면이 표시됩니다
   - 권한을 거부하면 러닝 기능을 사용할 수 없습니다

4. **백그라운드 실행**
   - 현재 구현은 포그라운드에서만 동작합니다
   - 백그라운드 러닝 추적이 필요하다면 Foreground Service를 추가해야 합니다

## 향후 개선 사항

1. **Foreground Service 추가**
   - 앱이 백그라운드에서도 러닝 추적 가능

2. **러닝 기록 저장**
   - 완료된 세션을 로컬 DB나 Health Connect에 저장

3. **지도 통합**
   - GPS 위치를 추적하여 경로 표시

4. **알림 기능**
   - 특정 거리/시간마다 알림

5. **음성 코칭**
   - TTS를 사용한 실시간 코칭

## 파일 위치 요약

### Core Healthcare 모듈
- `core/healthcare/src/main/java/com/example/healthcare/domain/model/RunningSession.kt`
- `core/healthcare/src/main/java/com/example/healthcare/domain/model/RunningMetrics.kt`
- `core/healthcare/src/main/java/com/example/healthcare/domain/model/ExerciseType.kt`
- `core/healthcare/src/main/java/com/example/healthcare/domain/repository/RunningRepository.kt`
- `core/healthcare/src/main/java/com/example/healthcare/domain/usecase/*.kt` (5개)
- `core/healthcare/src/main/java/com/example/healthcare/data/datasource/HealthConnectDataSource.kt`
- `core/healthcare/src/main/java/com/example/healthcare/data/repository/RunningRepositoryImpl.kt`
- `core/healthcare/src/main/java/com/example/healthcare/di/HealthcareModule.kt`

### Feature History 모듈
- `feature/history/src/main/java/com/example/history/HistoryScreen.kt`
- `feature/history/src/main/java/com/example/history/RunningViewModel.kt`
- `feature/history/src/main/java/com/example/history/RunningState.kt`
- `feature/history/src/main/java/com/example/history/PermissionHelper.kt`

### App 모듈
- `app/src/main/java/com/example/marathon/MainActivity.kt`
- `app/src/main/java/com/example/marathon/MyApplication.kt`
- `app/src/main/AndroidManifest.xml`
