# 📚 Libri — 도서관에 숨 쉴 틈을 주는 temi

> **"조용히 공부만 하는 곳"에서 "잠깐 웃고 쉬어 가는 곳"으로.**
> 광운대학교 중앙도서관 L층·1층에 있는 temi 로봇으로, 1~3분이면 끝나는 가벼운 체험을 제공합니다.

---

## 1. 문제 정의

| | 내용 |
|---|---|
| **현상** | 도서관은 "공부하는 공간"이라는 인식이 강하다 |
| **문제** | 분위기가 무겁고 삭막해서, 오래 머물수록 지치고 도서관이 딱딱한 공간으로 느껴진다 |
| **목표** | temi로 짧고 가벼운 즐길 거리를 만들어 **분위기를 환기**하고, 도서관에 대한 인식을 친근하게 바꾼다 |

---

## 2. 사용자 페인포인트

> 아래는 팀이 설정한 대표 사용자와 그들이 겪을 것으로 예상하는 불편입니다. 실제 이용자 인터뷰·설문으로 검증할 예정입니다.

### 👤 대표 사용자

| 사용자 | 상황 |
|---|---|
| **시험 기간 장시간 이용자** | 하루 5~6시간 이상 열람실에 앉아 있다 |
| **도서관이 낯선 신입생·방문객** | 조용한 분위기가 부담스러워 오래 머물지 않는다 |
| **친구와 함께 온 이용자** | 잠깐 쉬고 싶지만 도서관 안에서는 할 게 없다 |

### 😣 페인포인트

| # | 페인포인트 | 사용자의 말 (예상) |
|---|---|---|
| P1 | **쉬는 방법이 없다** — 쉬려면 휴대폰을 보거나 밖으로 나가야 한다 | "잠깐 쉬려다 유튜브 보고 30분이 지나요." |
| P2 | **몸이 굳는다** — 오래 앉아 있어서 목·어깨가 뻐근하다 | "고개를 숙이고 몇 시간 있다 보면 목이 아파요." |
| P3 | **눈이 피로하다** — 책과 모니터만 계속 본다 | "눈이 침침해도 그냥 계속 봐요." |
| P4 | **분위기가 무겁다** — 작은 소리도 눈치 보여 긴장하게 된다 | "도서관은 숨소리도 조심해야 할 것 같아요." |
| P5 | **추억이 없다** — 도서관에서 보낸 시간이 공부 말고는 남지 않는다 | "시험 끝나면 도서관은 그냥 힘든 기억이에요." |
| P6 | **로봇이 있어도 쓸 일이 없다** — temi가 있지만 무엇을 하는지 모른다 | "저 로봇 뭐 하는 거예요?" |

---

## 3. 해결 방법 — 기능

| 구분 | 기능 | 해결하는 페인포인트 | 설명 |
|---|---|---|---|
| ⭐ **메인** | **네컷 사진** | P4 · P5 · P6 | temi 앞에서 4장을 찍어 한 장으로 합성 |
| | **이미지 프레임** | P5 | 도서관·광운대 테마 프레임 중 골라서 꾸미기 |
| | **QR 다운로드** | P5 | 화면의 QR을 휴대폰으로 찍어 사진 저장 |
| 🎮 **미니게임** | **자세교정 스트레칭** | P1 · P2 | 화면 동작을 따라 하면 카메라가 목 각도로 판정 |
| | **눈 휴식** | P1 · P3 | 화면의 점을 고개로 따라가기, 먼 곳 보기 20초 |
| | **참참참** | P1 · P4 · P6 | 고개 방향으로 겨루는 게임. **L층·1층 temi끼리 대결** 가능 |
| 📝 **공통** | **만족도 조사** | — | 체험이 끝나면 이모지 1문항으로 평가 |

> 미니게임은 L층·1층 temi 두 대를 연결하는 **멀티 디바이스** 환경에서도 쓸 수 있도록 설계합니다.

---

## 4. 사용 흐름

```
 temi 대기 화면
   │  (사람이 다가오면) "잠깐 쉬어 갈래요?"
   ▼
 ┌──────────────┬────────────────────────────┐
 │ 📸 네컷 사진  │ 🎮 미니게임                 │
 │  촬영 동의    │  스트레칭 / 눈 휴식 / 참참참  │
 │  프레임 선택  │                            │
 │  4장 촬영     │                            │
 │  QR 다운로드  │                            │
 └──────┬───────┴─────────────┬──────────────┘
        └──────────┬──────────┘
                   ▼
           📝 만족도 조사 → 대기 화면
```

---

## 5. 도서관이라서 지키는 원칙

| 원칙 | 방법 |
|---|---|
| 🔇 **조용하게** | 안내는 화면 자막 위주, 소리는 최소로 |
| ⏱️ **짧게** | 한 번 체험은 1~3분 안에 끝나도록 |
| 🙋 **먼저 다가가지 않기** | 공부 중인 사람을 방해하지 않도록, 사람이 다가왔을 때만 반응 |
| 🔒 **사진은 안전하게** | 촬영 전 동의, 다운로드 링크는 일정 시간 뒤 삭제, temi에 사진을 남기지 않음 |

---

## 6. 진행 현황

| 기능 | 상태 |
|---|---|
| 고개 방향 인식 (ML Kit) | ✅ 구현 (`common/FaceDetectionView`) |
| 참참참 (봇 테스트 · Firebase 멀티플레이) | 🛠️ 구현 완료, 기기 테스트 중 (`chamchamcham`) |
| 홈 화면 · 기능 선택 메뉴 | ✅ 구현 (`HomeScreen`) |
| 네컷 사진 · 프레임 · QR (Supabase) | ⏳ 예정 (`photobooth` — 화면 자리만) |
| 눈 운동 (가운데 점 보며 고개 돌리기) | ✅ 구현 (`eye_work`) |
| 스트레칭 가이드 | ⏳ 예정 (`stretch` — 화면 자리만) |
| 만족도 조사 | ⏳ 예정 |

---

## 7. 기술 구성

| 영역 | 사용 기술 |
|---|---|
| 로봇 | temi SDK 1.138.0 (이동·회전·TTS·사람 감지) |
| 앱 | Kotlin, Jetpack Compose, minSdk 23 (temi V1·V2 호환) |
| 카메라 · 인식 | CameraX, ML Kit 얼굴 검출 (고개 좌우·위아래 각도, 눈 뜸·웃음 확률) |
| 멀티플레이 | Firebase Realtime Database (참참참 실시간 대결) |
| 사진 저장 · 관리 | **Supabase** — Storage(사진 파일) + Postgres DB(사진 메타데이터·만료 관리), 다운로드 QR (예정) |

---

## 8. 폴더 구조

기능은 **하나의 앱** 안에서 기능별 패키지로 나눈다. 여러 기능이 공유하는 카메라·인식 코드는 `common/` 에만 둔다.

```
Libri/app/src/main/java/kr/kw/libri/
├── MainActivity.kt          # temi 연결 + 홈 ↔ 기능 화면 전환
├── HomeScreen.kt            # 기능 선택 메뉴
│
├── common/                  # 여러 기능이 공유하는 코드
│   └── FaceDetectionView.kt     # 카메라 + ML Kit 얼굴·고개 인식 (YAW_SIGN·DIR_THRESHOLD)
│
├── photobooth/              # 📸 네컷 사진        (예정 — Supabase 업로드·QR)
│   └── PhotoBoothScreen.kt
│
├── chamchamcham/            # ✌️ 참참참          (구현 완료)
│   ├── ChamScreen.kt            # 대기실 · 공격/수비 화면
│   ├── ChamViewModel.kt         # 라운드 진행 · "참" 소리 · 수비 판정
│   ├── ChamModels.kt            # 규칙 · 박자 타이밍 · 손 애니메이션 계산
│   ├── ChamServers.kt           # Firebase 서버 / 봇 테스트 서버
│   └── ChamHand.kt              # 손 그리기 · 손목 회전
│
├── eye_work/                # 👀 눈 운동          (구현 완료)
│   ├── EyeWorkScreen.kt         # 가운데 점 + 안내 + 완료 UI
│   └── EyeWorkViewModel.kt      # 고개 각도 판정 · 단계 진행
│
├── stretch/                 # 🤸 스트레칭 가이드   (예정)
│   └── StretchScreen.kt
│
└── ui/theme/                # 공용 테마
```

**원칙**
- 각 기능은 `{기능}Screen` + `{기능}ViewModel` 한 쌍으로 독립 — 서로 import 하지 않는다.
- 공유되는 건 `common/` 에만 (얼굴 인식·카메라·공용 유틸).
- 화면 전환은 `MainActivity` 의 `Screen` enum + `when` 분기. 기능이 더 늘면 Compose Navigation 으로 승격.

---

## 9. 실행 방법 (Libri 앱)

### 로봇 없이 테스트 및 빌드

저장소 최상위에서 Android SDK와 Java 25(또는 Android Studio의 Java 25 JBR)를 설정한 뒤 실행합니다.

```bash
cd Libri
./gradlew testDebugUnitTest assembleDebug
```

Windows에서는 `gradlew.bat testDebugUnitTest assembleDebug`를 실행합니다. Kotlin 점수 테스트 결과는 `app/build/reports/tests/testDebugUnitTest/index.html`, APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

Firebase 규칙 검증은 Node.js 22와 Java가 설치된 환경에서 **저장소 최상위**에서 실행합니다. 로컬의 Auth·Realtime Database·Firestore 에뮬레이터에 테스트 데이터를 넣고, 자리 경합, 요청 수락·취소·만료, 설문 검증과 접근 차단을 확인합니다.

```bash
npx --yes firebase-tools@15.37.0 emulators:exec --project demo-libri --only auth,database,firestore "node firebase/tests/backend.mjs"
```

Firebase 테스트는 `demo-libri` 프로젝트의 로컬 에뮬레이터 전용입니다. 두 대의 Android 앱이 주고받는 화면과 네트워크 끊김 처리는 실제 기기에서 별도로 확인합니다.

### Android Studio에서 실행

1. Android Studio → **File → Open → `C:\temi\Libri`**
2. 기기(temi / 폰 / AVD) 선택 → **Run**
3. 대기실에서 모드와 자리를 고르기
   - **혼자 테스트 (봇)**: 설정 없이 기기 1대로 바로 가능
   - **온라인 요청**: L층과 1층 로봇이 모두 온라인 대기실에 들어갑니다. 한쪽에서 요청하고 상대가 수락하면 같은 방으로 자동 입장합니다.
   - **수동 입장**: 같은 방 코드와 서로 다른 자리(A/B)를 선택합니다.
   - 같은 자리를 먼저 입장한 기기가 차지합니다. 상대가 나가거나 연결이 끊기면 진행 중인 라운드를 취소하고, 이미 확정된 점수는 상대가 다시 입장할 때 유지합니다. 앱을 백그라운드로 보내면 온라인 방에서 나갑니다. 빈 방에 새로 입장하면 점수가 초기화됩니다.
   - 온라인 플레이를 시작하기 전에 두 기기의 APK와 Realtime Database 규칙을 함께 업데이트하세요. 구버전이 남긴 자리 정보가 방을 막으면 새 방 코드를 사용하세요.

<details>
<summary><b>Firebase 설정 (온라인 모드)</b></summary>

1. Firebase 프로젝트의 Android 앱 패키지 이름은 `kr.kw.libri`입니다.
2. 앱의 `app/google-services.json`과 `strings.xml`의 Realtime Database URL을 사용합니다.
3. Firebase Authentication의 로그인 제공업체에서 **익명**을 활성화합니다. 로봇이 계속 같은 UID를 쓰도록 익명 계정 자동 정리는 사용하지 않습니다.
4. 각 기기에서 층을 지정하고 **홈 → 로봇 설정**에 표시된 기기 UID를 확인합니다. Firebase 콘솔에서 UID를 층에 맞게 양쪽에 등록합니다.

   | 저장 위치 | L층 | 1층 |
   |---|---|---|
   | Realtime Database의 `robotAccess/{UID}` | 문자열 `temi_L` | 문자열 `temi_1` |
   | Firestore의 `robotAccess/{UID}` 문서 | `robotId` 문자열 `temi_L` | `robotId` 문자열 `temi_1` |

5. 기존 원격 규칙과 호환되는지 확인한 뒤 저장소 최상위에서 `firebase deploy --only database,firestore:rules --project mobilerobot-temi-library`로 배포합니다. 기존 설치본은 인증 기능이 포함된 APK로 함께 업데이트합니다. 두 기기에서 **Firebase 승인 다시 확인**을 누릅니다.

기기를 교체하거나 앱 데이터를 지우면 새 UID를 등록합니다. 사용하지 않는 UID는 두 데이터베이스에서 제거하고, Realtime Database에 이전 로봇 상태가 남아 있다면 해당 로봇 노드를 콘솔에서 정리합니다. 게임방은 승인된 두 팀 로봇이 공유하며, 방 안의 A/B 자리 점유는 로봇 인증과 별도로 관리됩니다.
</details>

<details>
<summary><b>Supabase 설정 (네컷 사진 — 예정)</b></summary>

사진 파일은 Supabase **Storage** 버킷에, 사진 메타데이터(촬영 시각·만료 시각·다운로드 경로)는 Supabase **Postgres DB** 로 관리한다. 만료된 사진은 주기적으로 정리해 temi·서버에 사진을 남기지 않는다.

1. [supabase.com](https://supabase.com) 프로젝트 생성 → Storage 버킷(예: `photos`) + 사진 메타 테이블 생성
2. `Libri/local.properties` 에 추가 — git 에 올라가지 않습니다

```properties
supabase.url=https://<프로젝트>.supabase.co
supabase.anonKey=<Project API anon key>
supabase.bucket=photos
```
</details>

<details>
<summary><b>참참참 고개 방향 조정</b></summary>

고개 방향이 반대로 판정되면 `common/FaceDetectionView.kt` 의 `YAW_SIGN = -1f` 로, 판정 각도는 `DIR_THRESHOLD` (기본 20°) 로 조정한다.
</details>

---

<sub>광운대학교 「모바일로봇의 이해」 팀 프로젝트</sub>

## 데이터 연결 설정

- 앱 ID: `kr.kw.libri`
- Android Studio에서 `Libri` 폴더를 열어 실행합니다.
- Firebase를 연결할 때 Android 패키지 이름으로 앱 ID를 등록합니다.
- 얼굴 인식은 카메라 권한을 허용하고 앱에서 카메라 켜기 버튼을 누르면 실행됩니다.

## 데이터 저장 계획

- Firebase Realtime Database: 로봇 연결 상태와 실시간 상태 전달.
- Cloud Firestore: 로봇 간 요청, 게임 기록, 만족도 및 이용 통계.
- Supabase Storage: 네컷 사진 임시 보관 및 QR 다운로드.
- 사진 버킷: `temi_photo`. 프로젝트 URL 및 공개용 publishable key는 앱 리소스에 설정되어 있습니다. 이 키는 비밀이 아니며 버킷의 접근 제어를 대체하지 않습니다.
- `LibriFirebase.realtimeDatabase(context)`는 지역별 데이터베이스 URL을 명시적으로 사용합니다.
- Firebase 멀티플레이 및 만족도 제출 코드는 실제 기기와 보안 규칙을 이용한 검증이 필요합니다. Supabase 업로드는 아직 구현되지 않았습니다.

### 백엔드 API와 현재 범위

- 앱 최초 실행 시 운영자가 층을 선택하면 기기에 저장됩니다. L층은 `temi_L`, 1층은 `temi_1`이며 홈의 **로봇 설정**에서 변경할 수 있습니다. 각 ID는 한 기기에만 배정해야 합니다.
- 앱이 보이는 동안 `robots/{로봇ID}`에 층·현재 서비스·사용 중 여부를 기록합니다. 화면이 홈이면 `idle`, 게임·눈 운동·설문 등에서는 사용 중으로 표시합니다. 앱이 중지되거나 연결이 끊기면 해당 상태 항목을 제거합니다. 재접속 시 다시 등록합니다.
- `LibriBackend.observePresence` / `RobotPresence.update` / `close`: 로봇 상태 등록·변경·해제. 호출자가 로봇 ID와 층을 지정하며 종료 시 `close()`를 호출해야 합니다.
- `observeRobots`, `observeIncomingRequests`: 변경 감지. 화면 종료 시 반환된 reference/query에서 `removeEventListener(listener)`를 호출합니다.
- 참참참 온라인 대기실은 상대 층 로봇의 상태를 표시합니다. 두 로봇의 요청은 `requests/pair_temi_L_temi_1`에서 관리하며, 요청을 보낸 쪽은 A, 수락한 쪽은 B로 입장합니다. 응답 대기 시간은 60초이며 보낸 쪽에서 취소할 수 있습니다.
- 요청 흐름을 테스트하려면 `robots` 상태 조회, 위 요청 경로의 읽기·쓰기, `rooms/{방코드}` 접근을 허용하는 팀 테스트용 Firebase 규칙이 필요합니다.
- `saveFeedback`: 1~5점, 서비스·로봇·층을 Firestore `feedback`에 저장합니다. 참참참과 눈 운동을 나갈 때 선택적으로 제출할 수 있으며 로봇 ID와 층은 기기 설정을 사용합니다.
- `recordUsage`: Firestore `usage_events`에 시작·완료 이벤트를 저장합니다. 호출 및 집계 화면은 아직 연결되지 않았습니다.
- 저장소 최상위 `firebase/`에 로봇 인증용 보안 규칙을 관리합니다. 온라인 기능은 승인된 UID와 기기의 층 설정이 일치할 때 사용할 수 있으며, 사진은 Supabase에서 관리합니다.

사진은 비공개 버킷과 만료되는 다운로드 링크를 사용하고 업로드 후 1시간이 지난 파일은 서버에서 Storage API로 삭제하도록 구성해야 합니다. 링크 만료만으로는 사진이 삭제되지 않습니다. 스케줄러 주기에 따라 삭제 시점이 1시간보다 늦어질 수 있으며 실패 시 재시도와 모니터링이 필요합니다. 이 삭제 작업은 아직 배포되지 않았습니다. Supabase 비밀 키나 서비스 역할 키를 Android 앱이나 Git에 넣지 마세요.
