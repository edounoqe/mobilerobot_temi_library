# Libri

DetectionDemo를 기반으로 시작한 독립 Android 앱입니다. 얼굴 검출과 고개 방향 표시 기능을 포함합니다.

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
- 연결 설정과 라이브러리만 준비되어 있으며, 데이터 읽기/쓰기 및 Supabase 업로드는 아직 구현되지 않았습니다.

사진은 비공개 버킷과 만료되는 다운로드 링크를 사용하고 업로드 후 1시간이 지난 파일은 서버에서 Storage API로 삭제하도록 구성해야 합니다. 링크 만료만으로는 사진이 삭제되지 않습니다. 스케줄러 주기에 따라 삭제 시점이 1시간보다 늦어질 수 있으며 실패 시 재시도와 모니터링이 필요합니다. 이 삭제 작업은 아직 배포되지 않았습니다. Supabase 비밀 키나 서비스 역할 키를 Android 앱이나 Git에 넣지 마세요.
