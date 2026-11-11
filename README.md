# 🚀 Idea2IP Backend

> **AI 활용 창작물의 제작 과정을 체계적으로 관리하고, 인간의 창작적 기여를 정리하여 저작권 등록 준비를 지원하는 서비스**

## 📌 프로젝트 소개

**Idea2IP**는 생성형 AI를 활용한 창작 과정에서 인간의 창작적 기여와 AI 활용 내역을 체계적으로 정리할 수 있도록 지원하는 서비스입니다.

창작자는 프로젝트별로 창작 자료와 최종 작품을 관리하고, AI 인터뷰를 통해 제작 과정에서의 인간 기여를 구체화할 수 있습니다. 이를 바탕으로 저작권 등록 준비에 활용할 수 있는 리포트를 생성합니다.

---

## 🛠️ 기술 스택

| 구분 | 기술 |
| :---: | --- |
| **Language** | Java |
| **Framework** | Spring Boot |
| **ORM** | Spring Data JPA |
| **Database** | PostgreSQL |
| **Build Tool** | Gradle |

---

## 로컬 실행

PostgreSQL에 데이터베이스를 생성하고, 아래 환경변수를 설정한 뒤 애플리케이션을 실행합니다.

- `DATABASE_URL` (예시 `jdbc:postgresql://localhost:5432/idea2ip`)
- `DATABASE_USERNAME`, `DATABASE_PASSWORD` (PostgreSQL 접속 정보)
- `JWT_SECRET`, `JWT_ACCESS_EXPIRATION` (JWT 서명 키와 만료 시간, 밀리초)

Windows에서는 저장소 루트에서 `.\gradlew.bat bootRun`을 실행합니다. Hibernate가 현재 설정에 따라 스키마를 생성·갱신하며, 인터뷰의 항목별 상태와 질문 출처 목록은 PostgreSQL `jsonb`로 저장합니다.

## 창작 기여와 Copyright Brief

Docker 이미지 빌드와 미니 PC 배포 절차는 [Docker 배포 안내](deploy/README.md)를 참고하세요.

기여 후보 생성과 리포트 생성은 서버가 백그라운드에서 처리합니다. 기본 AI 모드는 `mock`입니다. 실제 생성에는 `BRIEF_AI_MODE=live`, `BRIEF_AI_BASE_URL`과 FastAPI 설정에 맞는 `BRIEF_AI_TOKEN`을 지정합니다.

- `BRIEF_PDF_FONT`에는 한글 글리프를 포함한 TrueType 폰트 파일의 경로를 지정합니다. Windows에서는 기본으로 맑은 고딕을 사용합니다. Linux 배포 환경에는 서버가 읽을 수 있는 폰트를 제공해야 합니다.
- `BRIEF_PDF_DIR`은 비공개 PDF 저장 디렉터리입니다. 기본값은 `private/report-pdfs`입니다. 배포 시 서버가 읽고 쓸 수 있는 영속 디렉터리로 연결합니다. 여러 백엔드 인스턴스를 운영하는 경우 같은 저장소를 사용합니다.

## ✨ 주요 기능

| 구분 | 주요 기능 |
| --- | --- |
| 👤 **사용자 관리** | 사용자 정보 관리 및 인증 |
| 📁 **프로젝트 관리** | 프로젝트 생성, 조회, 수정 및 삭제 |
| 🎨 **창작 자료 관리** | 창작 자료 등록, 조회, 수정 및 삭제 |
| 🖼️ **최종 작품 관리** | 최종 작품 등록 및 관리 |
| 🤖 **AI 인터뷰** | AI 인터뷰 진행 및 인터뷰 메시지 관리 |
| 🧩 **창작 기여 관리** | 인간의 창작적 기여 정리 및 단계별 관리 |
| 📄 **리포트 관리** | 리포트 생성, 조회 및 버전 관리 |

---

## 🌿 브랜치 전략

GitHub Issue를 기반으로 작업 브랜치를 생성하고, Pull Request를 통해 `main` 브랜치에 병합합니다.

**브랜치 네이밍 규칙:** `태그/#이슈번호`

| 구분 | 브랜치 예시 | 설명 |
| --- | --- | --- |
| `main` | `main` | 안정적인 코드 관리 |
| `feat` | `feat/#2` | 새로운 기능 개발 |
| `fix` | `fix/#3` | 버그 수정 |
| `refactor` | `refactor/#4` | 코드 리팩토링 |
| `docs` | `docs/#5` | 문서 작성 및 수정 |
| `chore` | `chore/#6` | 환경 설정 및 기타 작업 |


---

## 📝 커밋 컨벤션

**커밋 메시지 형식:** `타입: 작업 내용`

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 또는 기존 기능 개선 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 리팩토링 |
| `docs` | README 등 문서 작업 |
| `test` | 테스트 코드 추가 및 수정 |
| `chore` | 패키지 및 환경 설정, 기타 수정 |
| `comment` | 주석 추가 및 수정 |
| `merge` | 브랜치 병합 |
| `rename` | 파일 및 폴더명 수정 |
| `remove` | 파일 삭제 |
| `init` | 프로젝트 초기 세팅 |
