# Match Vault 운영 배포

GitHub Actions가 Spring Boot jar와 React 프론트엔드를 빌드해 릴리즈로 업로드합니다. 배포 스크립트는 비활성 Blue/Green 서비스를 시작하고 해당 포트를 직접 헬스체크한 뒤 Nginx를 전환합니다. 이전 서비스는 진행 중인 작업이 끝나기를 기다린 후 종료합니다.

## GitHub Secrets

배포를 활성화하기 전에 repository secret 또는 production environment secret으로 아래 값을 등록합니다.

- `SSH_HOST`: 운영 서버 호스트 또는 IP.
- `SSH_USER`: 배포 경로에 쓸 수 있고 Nginx 및 Blue/Green systemd 서비스를 비대화형 sudo로 관리할 수 있는 SSH 사용자.
- `SSH_PRIVATE_KEY`: 배포용 SSH private key.
- `SSH_PORT`: SSH 포트. 선택 값이며 없으면 `22`를 사용합니다.
- `DEPLOY_PATH`: 배포 루트 경로. 선택 값이며 없으면 `/opt/match-vault`를 사용합니다.

## 서버 디렉터리 구조

배포 스크립트는 아래 경로를 기준으로 동작합니다.

- `/opt/match-vault`: 배포 루트.
- `/opt/match-vault/releases/<github-sha>`: GitHub commit SHA별 릴리즈 디렉터리.
- `/opt/match-vault/blue`, `/opt/match-vault/green`: 각 서비스가 실행할 릴리즈를 가리키는 symlink.
- `/opt/match-vault/current`: Nginx가 제공할 프론트엔드 릴리즈를 가리키는 symlink.
- `/etc/nginx/match-vault-active-backend.conf`: API 요청을 보낼 활성 포트(`8080`, `8082`, `8083`).
- `/etc/match-vault/match-vault.env`: 운영 환경 변수 파일.
- `match-vault-blue`, `match-vault-green`: Blue/Green systemd 서비스 이름.

`/opt/match-vault/current`는 배포 스크립트가 자동으로 만드는 symlink입니다. 서버 최초 설정 때 `current` 디렉터리를 직접 만들지 않습니다.

활성 백엔드가 기존 `8080`이면 첫 배포는 Blue(`8082`)로 진행합니다. 이후 Blue와 Green(`8083`)을 번갈아 사용합니다. 배포에 실패하면 기존 Nginx 대상과 프론트엔드 symlink를 복원하며, 릴리즈 디렉터리는 보관합니다.

### 실제 배포 전 Linux 모의 검증

로컬 프로젝트 루트에서 두 스크립트를 서버 홈 디렉터리로 복사한 뒤 서버에서 모의 검증을 실행할 수 있습니다. 이 검증은 임시 디렉터리에서 가짜 `systemctl`, `sudo`, `nginx`, `curl`을 사용하며 실제 서비스와 Nginx 설정을 변경하지 않습니다.

```bash
scp deploy/scripts/deploy-production.sh deploy/scripts/test-deploy-production.sh ubuntu@SERVER_HOST:~/
```

```bash
bash ~/test-deploy-production.sh
```

`Deployment simulation passed.`가 출력되면 헬스체크 실패 시 기존 경로 유지, 첫 Blue 배포, Nginx 전환 실패 시 복구, 다음 Green 배포 흐름이 통과한 것입니다. GitHub Actions도 실제 배포 전에 이 검증을 실행합니다.

## Blue/Green 슬롯 준비

기존 단일 서비스에서 전환하는 동안 `/opt/match-vault/current`는 그대로 둡니다. Blue와 Green 서비스는 각각 `/opt/match-vault/blue`, `/opt/match-vault/green` symlink가 가리키는 릴리즈를 실행합니다. 두 슬롯의 symlink는 서로 다른 릴리즈를 가리킬 수 있습니다.

먼저 기존 릴리즈를 Blue 슬롯에 연결합니다. 아래 명령은 Blue 경로가 이미 있으면 덮어쓰지 않고 중단합니다.

```bash
(
  set -e
  blue_release="$(readlink -f /opt/match-vault/current)"
  case "$blue_release" in
    /opt/match-vault/releases/*) ;;
    *) echo "Unexpected current release path" >&2; exit 1 ;;
  esac
  test -f "$blue_release/backend/app.jar" || { echo "Backend jar is missing" >&2; exit 1; }
  test ! -e /opt/match-vault/blue && test ! -L /opt/match-vault/blue || { echo "Blue slot already exists" >&2; exit 1; }
  sudo ln -s -- "$blue_release" /opt/match-vault/blue
  readlink -f /opt/match-vault/blue
)
```

Green 슬롯은 새 릴리즈가 준비된 뒤 그 릴리즈에 연결합니다. 기존 `match-vault` 서비스와 Nginx는 이 준비 단계에서 변경하지 않습니다.

### Blue/Green systemd 서비스 파일 설치

프로젝트가 서버에 체크아웃되어 있지 않다면 로컬 프로젝트 루트에서 다음 두 파일을 서버 홈 디렉터리로 복사합니다. `SERVER_HOST`는 실제 SSH 접속 호스트로 바꾸고, SSH 포트가 기본값이 아니면 `scp -P <port>`를 사용합니다.

```bash
scp deploy/systemd/match-vault-blue.service.example deploy/systemd/match-vault-green.service.example ubuntu@SERVER_HOST:~/
```

서버에서 두 unit을 설치하고 문법을 검사합니다. 기존 unit이 있으면 덮어쓰지 않습니다. Green 릴리즈가 준비되기 전까지 `enable` 또는 `start`하지 않습니다.

```bash
(
  set -e
  test ! -e /etc/systemd/system/match-vault-blue.service && test ! -L /etc/systemd/system/match-vault-blue.service
  test ! -e /etc/systemd/system/match-vault-green.service && test ! -L /etc/systemd/system/match-vault-green.service
  sudo install -m 644 ~/match-vault-blue.service.example /etc/systemd/system/match-vault-blue.service
  sudo install -m 644 ~/match-vault-green.service.example /etc/systemd/system/match-vault-green.service
  sudo systemd-analyze verify /etc/systemd/system/match-vault-blue.service /etc/systemd/system/match-vault-green.service
  sudo systemctl daemon-reload
  systemctl show match-vault-blue match-vault-green -p Id -p LoadState -p ActiveState
)
```

### 운영 Nginx에 활성 백엔드 연결

기존 Nginx 사이트 설정의 인증서, SSE(`8081`), `/api/v1/live/recovery/`, 정적 파일 설정은 유지합니다. 처음에는 활성 백엔드를 기존 `8080`으로 두어 설정 적용만으로 트래픽 대상이 바뀌지 않게 합니다.

```bash
(
  set -e
  test ! -e /etc/nginx/match-vault-active-backend.conf && test ! -L /etc/nginx/match-vault-active-backend.conf
  sudo cp -a /etc/nginx/sites-available/match-vault.conf "/etc/nginx/sites-available/match-vault.conf.before-blue-green-$(date +%Y%m%d%H%M%S)"
  printf 'server 127.0.0.1:8080;\n' | sudo tee /etc/nginx/match-vault-active-backend.conf
)
sudoedit /etc/nginx/sites-available/match-vault.conf
```

사이트 설정의 첫 번째 `server {` 앞에 다음 블록을 추가합니다. 기존 `log_format` 선언 뒤에 놓아도 됩니다.

```nginx
upstream match_vault_backend {
    include /etc/nginx/match-vault-active-backend.conf;
}
```

기존 `location /api/`와 `location /actuator/health`의 `proxy_pass http://127.0.0.1:8080;` 두 줄만 아래처럼 바꿉니다.

```nginx
proxy_pass http://match_vault_backend;
```

설정 적용 전후에 문법과 활성 경로를 확인합니다. `nginx -t`가 실패하면 reload하지 않고 위 백업 파일로 사이트 설정을 복원합니다.

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo grep -nE 'upstream match_vault_backend|proxy_pass|include /etc/nginx/match-vault-active-backend.conf' /etc/nginx/sites-available/match-vault.conf
cat /etc/nginx/match-vault-active-backend.conf
```

## 기존 단일 서비스 환경 설정 기록

아래는 Blue/Green 전환 전의 `match-vault` 단일 서비스 설정 예시입니다. 현재 서버는 위의 Blue/Green 슬롯, systemd 서비스, Nginx 활성 백엔드 준비 절차를 따릅니다.

런타임 사용자와 배포 디렉터리를 생성합니다. 아래 명령의 `deploy`는 GitHub Secret `SSH_USER`에 등록한 실제 SSH 사용자로 바꿔서 실행합니다.

```bash
sudo useradd --system --home /opt/match-vault --shell /usr/sbin/nologin match-vault
sudo usermod -aG match-vault deploy
sudo mkdir -p /opt/match-vault/releases /opt/match-vault/incoming /etc/match-vault
sudo chown -R deploy:match-vault /opt/match-vault
sudo chmod -R 2775 /opt/match-vault
```

`/etc/match-vault/match-vault.env` 파일을 생성합니다.

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

DB_URL=jdbc:mysql://127.0.0.1:3306/match_vault?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=match_vault
DB_PASSWORD=change-me

SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379

API_FOOTBALL_KEY=change-me
API_FOOTBALL_HOST=
API_FOOTBALL_LEAGUE_SEASONS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_TEAMS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_STANDINGS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_FIXTURES_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_FIXTURE_DETAILS_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_PLAYERS_REGISTERED_SYNC_RUN_ON_STARTUP=false
API_FOOTBALL_INJURIES_SYNC_RUN_ON_STARTUP=false
```

운영 배포 안정성을 위해 startup sync는 기본적으로 꺼두는 것을 권장합니다. 외부 API가 503을 반환하거나 응답이 느린 경우, 앱 재시작과 GitHub Actions 헬스체크가 외부 API 상태에 영향을 받을 수 있습니다. 초기 데이터 적재가 필요하면 배포가 끝난 뒤 관리자 기능이나 별도 수동 작업으로 실행합니다.

환경 변수 파일을 작성한 뒤 권한을 제한합니다.

```bash
sudo chown root:match-vault /etc/match-vault/match-vault.env
sudo chmod 640 /etc/match-vault/match-vault.env
```

systemd unit을 설치합니다.

```bash
sudo cp deploy/systemd/match-vault.service.example /etc/systemd/system/match-vault.service
sudo systemctl daemon-reload
sudo systemctl enable match-vault
```

Nginx site 설정을 설치합니다.

```bash
sudo cp deploy/nginx/match-vault.conf.example /etc/nginx/sites-available/match-vault.conf
sudo ln -s /etc/nginx/sites-available/match-vault.conf /etc/nginx/sites-enabled/match-vault.conf
sudo nginx -t
sudo systemctl reload nginx
```

Nginx 예시 파일에는 `server_name example.com;`이 들어 있습니다. 활성화하기 전에 실제 도메인 또는 서버 IP에 맞게 `server_name`을 변경합니다.

## 배포 사용자 sudo 권한

GitHub Actions의 SSH 사용자는 비밀번호 입력 없이 Blue/Green 및 기존 서비스의 `systemctl start`, `stop`, `enable`, `disable`, Nginx의 설정 검사와 reload, `/etc/nginx/match-vault-active-backend.conf` 설치를 실행할 수 있어야 합니다. 배포 스크립트는 모든 sudo 호출에 `-n`을 사용하므로 권한이 없으면 대화형 암호 입력을 기다리지 않고 실패합니다.

## 헬스체크 대기 시간 조정

배포 스크립트는 비활성 서비스 시작 후 해당 포트의 `/actuator/health`가 HTTP 200을 반환할 때까지 최대 10분 기다립니다. 검증에 실패하면 Nginx를 전환하지 않고 후보 서비스를 종료합니다.

필요하면 GitHub Actions의 remote deployment 단계나 서버 환경에서 아래 환경 변수로 조정할 수 있습니다.

- `HEALTH_TIMEOUT_SECONDS`: 전체 헬스체크 최대 대기 시간. 기본값은 `600`.
- `HEALTH_INTERVAL_SECONDS`: 헬스체크 반복 간격. 기본값은 `5`.

## 배포 후 검증

배포가 끝난 뒤 서버에서 활성 포트와 서비스 상태를 확인합니다.

```bash
cat /etc/nginx/match-vault-active-backend.conf
systemctl is-active match-vault-blue match-vault-green
readlink -f /opt/match-vault/current
active_port="$(grep -oE '[0-9]+;' /etc/nginx/match-vault-active-backend.conf | tr -d ';')"
curl -f "http://127.0.0.1:$active_port/actuator/health"
```

그 다음 Nginx를 거치는 공개 경로도 확인합니다.

- `/`
- `/league/overview`
- `/api/v1/home/summary?season=2025`
