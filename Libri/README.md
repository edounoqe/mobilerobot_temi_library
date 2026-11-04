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
| 눈 운동 | ⏳ 예정 (`eye_work` — 화면 자리만) |
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
├── eye_work/                # 👀 눈 운동          (예정)
│   └── EyeWorkScreen.kt
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

1. Android Studio → **File → Open → `C:\temi\Libri`**
2. 기기(temi / 폰 / AVD) 선택 → **Run**
3. 대기실에서 모드와 자리를 고르기
   - **혼자 테스트 (봇)**: 설정 없이 기기 1대로 바로 가능
   - **온라인**: 기기 2대, 같은 방 코드, 서로 다른 자리(A/B)

<details>
<summary><b>Firebase 설정 (온라인 모드)</b></summary>

1. Firebase 콘솔에서 프로젝트 생성 → Android 앱 추가 (패키지 이름 `kr.kw.libri`)
2. Realtime Database 생성 (테스트 모드)
3. `Libri/local.properties` 에 추가 — git 에 올라가지 않습니다

```properties
firebase.dbUrl=https://<프로젝트>-default-rtdb.<지역>.firebasedatabase.app
firebase.apiKey=<google-services.json 의 api_key.current_key>
firebase.appId=<google-services.json 의 mobilesdk_app_id>
firebase.projectId=<project_id>
```
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
