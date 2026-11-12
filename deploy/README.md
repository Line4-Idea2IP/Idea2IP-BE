# 미니 PC 배포

미니 PC의 Compose가 Spring, FastAPI, PostgreSQL을 실행합니다. Spring은 인터뷰 질문을 생성할 때 데스크탑의 llama.cpp를 직접 호출합니다. 창작 기여와 Brief는 FastAPI를 통해 같은 모델을 사용합니다. 인터뷰 답변의 완료 여부는 기존 규칙으로 판정합니다.

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

로그인 후 새 테스트 프로젝트에서 최종 작품 등록, 인터뷰 첫 질문과 후속 질문, 기여 확정, live Brief 생성과 PDF 다운로드를 확인하세요. 모델 호출이 실패하면 인터뷰 API는 `503 INTERVIEW_503`을 반환합니다. 답변 제출 뒤 다음 질문 생성에 실패했다면 같은 `requestId`와 답변으로 다시 요청할 수 있습니다. Spring 컨테이너를 다시 만들어도 같은 버전의 PDF 해시가 유지되는지 확인합니다. 상태나 로그를 조회할 때는 `RELEASE_SHA=$(cat deploy/.release) docker compose --env-file deploy/.env ps`를 사용할 수 있습니다.

정상 배포 SHA는 `deploy/.release`에 저장됩니다. 이전 성공 SHA를 배포 명령에 전달하면 해당 이미지 쌍으로 돌아갑니다. 컨테이너 시작이 실패하면 스크립트는 저장된 이전 SHA의 로컬 이미지로 복구를 시도합니다. 이미지 복구는 DB 데이터나 스키마를 되돌리지 않으므로 DB 스키마가 바뀌는 배포 전에 백업하세요. 이 구성은 기존 Spring의 Hibernate 스키마 갱신 설정을 사용합니다. 볼륨을 삭제하는 `docker compose down -v`는 개발 데이터를 폐기할 때만 사용하세요.

## 자동 배포 (CD)

미니 PC의 systemd 타이머가 3분마다 `deploy/auto-deploy.sh`를 실행합니다. 스크립트는 GitHub API에서 **main push로 이미지 게시까지 성공한 마지막 워크플로 실행**의 커밋 SHA를 확인하고, `deploy/.release`와 다르면 그 SHA로 배포합니다. 미니 PC가 GitHub의 공개 정보를 읽기만 하므로 외부에서 미니 PC로 들어오는 연결이나 GitHub 쪽 비밀값이 필요 없습니다. 공개 저장소에서 권장되지 않는 자체 호스팅 러너도 쓰지 않습니다.

한 번의 배포는 다음 순서로 진행됩니다.

1. 현재 운영 중인 버전의 PostgreSQL을 `deploy/backups/`에 `pg_dump`로 백업합니다(최근 10개 보관). 실패하면 배포를 미루고 다음 주기에 다시 시도합니다.
2. 저장소를 새 SHA로 체크아웃해 `compose.yaml`과 배포 스크립트를 이미지와 같은 버전으로 맞춥니다. `deploy/.env`는 Git이 추적하지 않으므로 유지됩니다.
3. 기존 `deploy/deploy.sh <SHA>`로 이미지 받기, 데스크탑 모델 확인, 서비스 교체, 실패 시 이전 이미지 복구를 수행합니다.
4. 성공하면 현재와 직전 버전을 제외한 오래된 이미지를 정리합니다. 실패하면 SHA를 `deploy/.failed`에 기록해 같은 배포를 반복하지 않고, 체크아웃을 운영 중인 버전으로 되돌립니다.

### 설치

`deploy` 사용자(도커 그룹 포함)로 저장소를 `/opt/idea2ip`에 받고 위의 첫 배포를 한 번 수동으로 성공시킨 뒤 진행합니다. 다른 경로나 사용자를 쓰면 `deploy/systemd/idea2ip-deploy.service`의 `User`, `WorkingDirectory`, `ExecStart`를 바꾸세요. 미니 PC에는 `git`, `curl`, `flock`이 필요합니다.

```bash
sudo install -d -m 700 -o deploy /etc/idea2ip
sudo install -m 600 -o deploy deploy/systemd/auto-deploy.env.example /etc/idea2ip/auto-deploy.env
sudo cp deploy/systemd/idea2ip-deploy.service deploy/systemd/idea2ip-deploy.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now idea2ip-deploy.timer
```

처음에는 `/etc/idea2ip/auto-deploy.env`에 `IDEA2IP_DRY_RUN=1`을 넣고 `sudo systemctl start idea2ip-deploy.service`로 배포 대상 SHA만 확인한 다음 값을 지우는 것을 권장합니다. GHCR 패키지가 비공개라면 `deploy` 사용자로 `read:packages` 권한 토큰을 사용해 `docker login ghcr.io`를 한 번 실행해 두세요. 저장소가 공개이므로 패키지를 공개로 바꾸면 이 단계가 필요 없습니다.

### 운영

- 로그: `journalctl -u idea2ip-deploy -f`, 다음 실행 시각: `systemctl list-timers idea2ip-deploy.timer`
- 자동 배포 일시 중지: `touch /opt/idea2ip/deploy/.hold` (재개는 파일 삭제)
- 되돌리기: 자동 배포를 멈춘 뒤 `bash deploy/deploy.sh <이전 SHA>`. 이미지 복구는 DB를 되돌리지 않으므로 필요하면 `deploy/backups/`의 백업으로 복원합니다.
- 실패한 배포 재시도: 원인을 해결한 뒤 `deploy/.failed`에서 해당 SHA 줄을 지웁니다.
- 알림: `IDEA2IP_DEPLOY_WEBHOOK`에 Discord 또는 Slack 웹훅을 넣으면 성공·실패를 보냅니다.
- PDF 템플릿 버전이 바뀌는 배포는 배포 시점에 대기 중인 리포트 작업을 실패시키므로, 사용이 적은 시간에 main에 병합하세요.
