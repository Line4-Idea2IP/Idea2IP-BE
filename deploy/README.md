# 미니 PC 배포

미니 PC의 Compose가 Spring, FastAPI, PostgreSQL을 실행합니다. FastAPI는 데스크탑에서 실행 중인 llama.cpp의 사설 주소를 사용합니다. 인터뷰는 현재 mock이고 창작 기여와 Brief는 live입니다.

## 이미지 게시

PR에서는 두 Docker 이미지 빌드와 Compose 설정 검사를 실행합니다. main의 성공한 워크플로는 GHCR에 백엔드와 RAG 이미지를 **같은 백엔드 전체 커밋 SHA**로 게시합니다. 두 이미지의 게시가 모두 성공한 실행을 선택하세요. RAG의 실제 소스 커밋은 `deploy/rag-revision.txt`에서 고정하며, 업데이트할 때 이 값을 검토해 변경합니다.

## 첫 배포

이미지는 amd64(x86_64) 기준으로 빌드합니다. 미니 PC에 Docker Engine, Compose v2, Bash와 flock이 있는 상태에서 저장소 루트에서 실행하세요. 처음에는 테스트 서버 또는 빈 데이터 볼륨에서 확인합니다.

```bash
umask 077
cp -n deploy/.env.example deploy/.env
```

`deploy/.env`에 DB 관리자 비밀번호, 앱 DB 비밀번호, JWT 키, 서비스 토큰을 각각 설정합니다. JWT 키는 32바이트 이상을 사용하세요. 앱 역할과 DB 소유권은 빈 PostgreSQL 볼륨의 첫 기동에서 초기화됩니다. 이후 비밀번호 교체는 DB 역할과 환경설정을 함께 갱신해야 합니다.

데스크탑 llama.cpp의 사설 URL도 설정합니다. 예를 들어 `http://<데스크탑-사설주소>:8081/v1`입니다. WSL에서 실행하는 경우 그 주소가 WSL의 모델까지 연결되는지 먼저 확인합니다.

기본 Spring 바인딩은 `127.0.0.1:8080`입니다. 외부 접속은 미니 PC의 리버스 프록시나 사설망 연결에 맞춰 설정합니다. FastAPI와 PostgreSQL은 Compose 네트워크에서 접근합니다. GHCR 패키지가 비공개라면 미니 PC에서 패키지 읽기 권한으로 ghcr.io에 로그인하세요.

```bash
bash deploy/deploy.sh <이미지-게시가-완료된-전체-백엔드-SHA>
```

배포 명령은 이미지를 받은 뒤 Docker 네트워크에서 데스크탑 모델과 별칭을 검증하고 서비스를 시작합니다. PostgreSQL, 업로드, PDF는 각각 이름 있는 볼륨에 저장됩니다. 한글 TTF는 Spring 이미지에 포함됩니다.

## 배포 확인과 복구

로그인 후 테스트 프로젝트에서 최종 작품 등록, 인터뷰 완료, 기여 확정, live Brief 생성과 PDF 다운로드를 확인하세요. Spring 컨테이너를 다시 만들어도 같은 버전의 PDF 해시가 유지되는지 확인합니다. 상태나 로그를 조회할 때는 `RELEASE_SHA=$(cat deploy/.release) docker compose --env-file deploy/.env ps`를 사용할 수 있습니다.

정상 배포 SHA는 `deploy/.release`에 저장됩니다. 이전 성공 SHA를 배포 명령에 전달하면 해당 이미지 쌍으로 돌아갑니다. 컨테이너 시작이 실패하면 스크립트는 저장된 이전 SHA의 로컬 이미지로 복구를 시도합니다. 이미지 복구는 DB 데이터나 스키마를 되돌리지 않으므로 DB 스키마가 바뀌는 배포 전에 백업하세요. 이 구성은 기존 Spring의 Hibernate 스키마 갱신 설정을 사용합니다. 볼륨을 삭제하는 `docker compose down -v`는 개발 데이터를 폐기할 때만 사용하세요.
