# SSE 연결 수용 제한

Gateway 프로세스마다 독립적인 제한을 적용한다. 기본값:

| Spring 속성 | 값 | 의미 |
| --- | --- | --- |
| sse.max-connections | 1000 | 현재 수용 중인 SSE 요청의 최대 수 |
| sse.max-connections-per-ip | 100 | 같은 IP의 동시 연결 |
| sse.max-connections-per-browser | 3 | 같은 익명 쿠키의 동시 연결 |
| sse.connect-rate-per-second | 100 | 초당 토큰 충전량 |
| sse.connect-burst | 100 | 최대 토큰 수, 시작 시 충전량 |

유효한 경기 ID와 식별 정보가 있는 SSE 요청은 토큰 1개를 소비한다. 토큰이 없으면 429와 Retry-After: 1,
토큰은 있지만 연결 슬롯이 없으면 503과 Retry-After: 5를 반환한다.
X-SSE-Rejection 헤더는 각각 rate_limit / connection_limit이다.
거절은 SSE 응답을 시작하기 전에 수행하며, 대기 큐는 만들지 않는다.
이미 연결된 사용자의 이벤트 및 heartbeat는 토큰을 소비하지 않는다.
종료/실패/취소 시 슬롯만 반환한다. 토큰은 반환하지 않아 빠른 접속·종료 반복도 제한한다.

이는 고정된 분당 6000회 한도가 아니다. 초당 100개를 지속 충전하므로 초기 burst를 포함하면
60초 구간에 최대 약 6100개를 수용할 수 있다. 모든 수치는 인스턴스 단위이며 재시작 시 초기화된다.
실제 TCP 연결 및 HTTP 파싱 이전의 부하를 막지는 않으며, 필요 시 앞단 프록시에서 추가 보호한다.
전체/IP/쿠키 한도를 독립적으로 검사한다. IP 100개와 쿠키 3개는 초기 정책이며 실제 트래픽을 보고 조정한다.
브라우저별 한도를 3개로 두어 HTTP/1.1 환경의 브라우저 연결 한도보다 먼저 SSE 접속을 제한한다.
공유 Wi-Fi·학교·회사에서는 여러 이용자가 같은 IP 한도를 공유한다.

local 프로파일 GET /api/local/sse-gateway 응답의 admission:
active, maxConnections, ratePerSecond, burst, rateRejected, capacityRejected.
추가 지표: maxPerIp, maxPerBrowser, ipRejected, browserRejected, activeIps, activeBrowsers.
active는 응답 수명 동안 예약한 슬롯이라 등록된 subscribers 수와 순간적으로 다를 수 있다.
거절 카운터는 프로세스 시작 이후 누적이며 요청마다 로그를 출력하지 않는다.

프론트엔드는 오류 후 자체 백오프로 재접속한다. EventSource에서는 HTTP 응답 헤더를 읽을 수 없어
Retry-After 값을 직접 따르지는 않는다. 연결 제한 중에도 기존 MVC fallback은 유지한다.
인증 실패와 일시적인 과부하를 구분해야 하는 경우 별도 클라이언트 정책이 필요하다.

1,000개 부하 테스트 중 브라우저를 추가로 연결하면 한도를 초과할 수 있다.
1,500개 다중 경기 실험을 다시 실행하려면 실험 Gateway에서만 max-connections 값을 조정한다.
운영 설정 파일은 변경하지 않았으며 Spring 속성/환경 변수로 기본값을 덮어쓸 수 있다.

## 익명 브라우저 식별과 연결 수명

프론트엔드는 MVC `GET /api/v1/live/identity` 완료 후 EventSource를 연다.
MVC는 유효한 쿠키가 없을 때만 UUID와 만료 시각을 HMAC 서명해 발급한다.
쿠키는 30일, HttpOnly, SameSite=Lax, Path=/api/v1/live, 운영 Secure이며 Domain은 생략한다.
같은 출처의 탭들은 Web Locks로 첫 발급을 직렬화한다. 미지원 환경에서는 첫 발급 경쟁을 완전히 막지 못한다.
같은 페이지 내 중복 발급 요청도 공유한다. 실패하면 기존 백오프 및 MVC fallback을 사용한다.
재접속 전에도 발급 API를 확인하여 만료·누락 쿠키를 보충한다.

Gateway는 공유 모듈 `sse-identity`로 서명/만료를 검증한다. Redis 조회는 필요하지 않다.
누락·변조·만료 쿠키는 401 / browser_identity_required로 거절한다.
IP·쿠키 한도는 429 / ip_connection_limit 또는 browser_connection_limit, Retry-After: 5다.
유효한 IP를 확인하지 못하면 400 / invalid_client_ip다. 모두 SSE 응답 시작 전에 처리한다.
슬롯 예약은 전체/IP/쿠키에 대해 원자적으로 수행하고 완료·오류·취소·정체 종료 시 한 번만 반환한다.
연결이 0개가 된 집계 항목도 삭제한다. 실제 IP/쿠키/서명 키는 로그나 통계에 출력하지 않는다.

모든 경기의 실제 연결 수를 합산한다. 같은 경기를 세 탭에서 열면 3개다.
크롬 3개와 엣지 2개라면 같은 IP에서 합계 5개, 쿠키별로는 3개와 2개다.
쿠키는 사람이 아닌 브라우저 저장소의 식별자다. 서명은 임의 위조를 막지만 쿠키 삭제·시크릿 창·
다른 브라우저를 통한 재발급은 가능하다. IP/전체 한도가 함께 필요하다.
모든 집계는 Gateway 인스턴스 메모리 기준이며 다중 인스턴스 전역 한도는 아니다.

## 운영 설정

- MVC `/etc/match-vault/match-vault.env`와 Gateway `/etc/match-vault/sse-gateway.env`에
  같은 `SSE_IDENTITY_SECRET`(Spring 속성 `sse.identity.secret`)을 설정한다.
  최소 32바이트의 무작위 비밀값을 사용하고 저장소·명령 이력·로그에 남기지 않는다.
  Redis 전송 모드의 운영 MVC와 Gateway는 키 누락 시 시작을 거절한다.
  두 서버 시간을 동기화한다. 키 교체 후 기존 쿠키는 재발급된다.
- local & !prod에서만 개발용 공통 키와 HTTP 쿠키를 허용한다.
  기존 MVC 전송 모드의 발급 API는 쿠키 없이 204를 반환한다.
- `sse.trusted-proxies` 기본값은 `127.0.0.1,::1`이다. 직전 TCP peer가 이 목록에 있을 때만
  Nginx가 덮어쓴 `X-Real-IP: $remote_addr`를 신뢰한다. 그 외에는 peer IP를 사용한다.
  X-Forwarded-For는 읽지 않으며 IPv4/IPv6를 DNS 조회 없이 정규화한다.
- Gateway의 `server.forward-headers-strategy=none`을 유지한다. Spring이 peer 주소를 먼저
  변환하지 않도록 다른 전략은 시작 시 거절한다. 별도 호스트의 프록시는 실제 IP를 목록에 추가한다.
  Gateway 직접 접근도 제한해야 한다. 프록시 없는 테스트에서는 trusted-proxies를 빈 문자열로 둘 수 있다.
- 프론트엔드/MVC/Gateway를 함께 반영한다. 구 프론트엔드는 쿠키 없이 연결할 수 없다.
  운영 환경 파일, Nginx, systemd, GitHub Actions는 이번 작업에서 수정하지 않았다.

## 기존 부하 실험 실행

한 PC에서 실행하는 느린 소비자·churn·outage 등 기존 실험은 IP/쿠키 한도에 걸린다.
이 실험에서는 **local Gateway에만** 한도를 높이고 공통 테스트 쿠키를 전달한다.
관찰 브라우저 연결도 포함하여 여유를 둔다. 예:

```powershell
.\gradlew.bat :sse-gateway:bootRun --args="--spring.profiles.active=local --sse.max-connections=1100 --sse.max-connections-per-ip=1100 --sse.max-connections-per-browser=1100"
```

MVC는 local + live.sse.transport=redis로 실행한다. 실험할 PowerShell에서 아래처럼
쿠키를 임시 파일로 준비한다. 쿠키 값은 출력하지 않는다.

```powershell
$null = Invoke-WebRequest 'http://localhost:8080/api/v1/live/identity' -SessionVariable sseTestSession
$sseTestCookie = $sseTestSession.Cookies.GetCookies([uri]'http://localhost:8080/api/v1/live/identity')['sse_browser']
if (-not $sseTestCookie) { throw 'MVC를 local + redis 전송 모드로 실행하세요.' }
$env:SSE_COOKIE_FILE = Join-Path ([IO.Path]::GetTempPath()) ('sse-load-' + [guid]::NewGuid() + '.txt')
[IO.File]::WriteAllText($env:SSE_COOKIE_FILE, 'sse_browser=' + $sseTestCookie.Value)
# 같은 터미널에서 기존 run.ps1 또는 Node 실험 실행
# 실험 종료 후:
Remove-Item -LiteralPath $env:SSE_COOKIE_FILE
Remove-Item Env:SSE_COOKIE_FILE
```

run.ps1/mixed.mjs는 k6에 파일 경로만 전달하며 Node 클라이언트도 같은 파일을 읽는다.
이는 전송 성능 비교용이며 브라우저별 제한 검증은 아니다. 제한 검증에는 기본 한도와 별도 발급 쿠키를 사용한다.

## 검증 범위

자동 검증: 발급/유지/변조/만료, 운영 키 필수, 신뢰 프록시 IP 선택, IPv6 정규화,
독립적인 IP/쿠키 한도, 동시 예약 경쟁, 중복 반환, 실제 HTTP 401/429/503 거절,
다중 경기 합산, 소켓 종료 후 재접속, 느린 소켓 정리, 프론트엔드 발급 대기·백오프·취소.
운영 프록시와 브라우저 여러 탭의 수동 확인 및 대규모 부하 재실험은 별도 확인 대상이다.
