# Libri — temi 참참참

중앙도서관 temi 리프레시용 미니게임. 공격자는 터치로 손가락 방향을 고르고, 수비자는 "참!"에 맞춰 고개를 돌린다.
DetectionDemo 의 얼굴 검출(FaceDetectionView, ML Kit)을 가져와 고개 좌우(Yaw)로 판정한다.

## 실행
1. Android Studio → File → Open → `C:\temi\libri`
2. 기기(temi / 폰 / AVD) 선택 → Run
3. 대기실에서 모드·자리 선택
   - **혼자 테스트(봇)**: Firebase 설정 없이 기기 1대로 동작
   - **온라인**: 기기 2대, 같은 방 코드, 서로 다른 자리(A/B)

## Firebase (온라인 모드)
`libri/local.properties` 에 추가 (git 에 올라가지 않음):
```
firebase.dbUrl=https://<프로젝트>-default-rtdb.<지역>.firebasedatabase.app
firebase.apiKey=<google-services.json 의 api_key.current_key>
firebase.appId=<google-services.json 의 mobilesdk_app_id>
firebase.projectId=<project_id>
```
Firebase 콘솔에서 Android 앱 패키지 이름은 `kr.kw.libri`, Realtime Database 는 테스트 모드로 생성.

## 파일
| 파일 | 역할 |
|---|---|
| `ChamModels.kt` | 규칙·박자 타이밍·손 애니메이션 계산 |
| `ChamServers.kt` | Firebase 서버 / 봇 테스트 서버 |
| `ChamViewModel.kt` | 라운드 진행·"참" 소리·수비 판정 |
| `ChamHand.kt` | 손 그리기·손목 회전 |
| `ChamScreen.kt` | 대기실·공격/수비 화면 |
| `FaceDetectionView.kt` | 카메라 + ML Kit 얼굴 검출 (DetectionDemo 에서 가져옴) |

## 조정
- 고개 방향이 반대로 판정되면 `FaceDetectionView.kt` 의 `YAW_SIGN = -1f`
- 판정 각도는 `DIR_THRESHOLD` (기본 20°)
