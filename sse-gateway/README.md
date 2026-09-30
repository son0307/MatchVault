# WebFlux / Reactor Netty SSE gateway

기존 MVC와 별도 JVM으로 실행하는 SSE 모듈이다. 첫 구현 범위는 **전체 스냅샷의 최신값 병합과 정체 연결 종료**다.
MVC 전송이 기본이며, 명시적으로 Redis 모드를 켠 경우에만 새 경로를 사용한다.

## 보호 정책

| 항목 | 동작 |
|---|---|
| 최신값 병합 | LIVE_SNAPSHOT, FIXTURE_EVENTS, PLAYER_STATS 각각 미전송 최신 1개 |
| 전송 중 상태 | 연결별 최대 1프레임. 실제 쓰기 완료 후 다음 프레임 전달 |
| 바이트 상한 | 기본 1MiB. UTF-8 기준 미전송 데이터와 전송 중 프레임 합계 |
| 정체 제한 | 데이터가 대기/전송 중인데 소켓 쓰기 진행이 15초간 없으면 종료 |
| 종료 처리 | 실제 Netty 채널 close, mailbox 해제, 구독 정보 제거 |
| heartbeat | 25초마다 전송. 데이터 적체 중에는 생략 |

연결별 mailbox에 넣는 작업은 소켓 쓰기를 기다리지 않는다. 이벤트 루프 예약도 연결별 하나로 제한한다.
Netty progressive write promise의 부분 쓰기 진행을 관찰해 정체 시계를 갱신하고,
전체 프레임 쓰기가 완료된 뒤에만 다음 데이터를 전달한다. 이는 클라이언트 화면 반영 ACK가 아니다.
스냅샷 교체는 미전송 데이터에만 적용한다. 이미 전송 중인 프레임을 중간에 바꾸지 않는다.

상한 초과는 `mailbox_limit`, 정체는 `stalled` 사유로 종료하며 서버 WARN 로그와 통계에 남긴다.
1MiB는 OS TCP 버퍼나 JVM 전체 메모리 상한이 아니다. 메시지 크기와 전체 연결 수에 따른 메모리는 별도로 측정한다.
세 이벤트 종류가 항상 같은 발행 세대라는 보장은 없다.

## 실행

IP 100개·익명 쿠키 3개·전체 1000개의 동시 연결 제한이 적용된다.
프론트엔드는 MVC에서 익명 쿠키를 발급받고 SSE를 연다. 운영에서는 양쪽 서비스에 동일한
`SSE_IDENTITY_SECRET`이 필요하다. 단일 PC의 아래 부하 실험 예제를 실행하기 전에
[연결 제한 문서](CONNECTION-LIMITS.md)의 **기존 부하 실험 실행** 절을 따라 local 한도와 테스트 쿠키를 준비한다.

두 애플리케이션이 같은 Redis에 연결해야 한다. gateway는 MVC의 리소스 설정을 읽지 않는다.
Redis가 기본 localhost:6379가 아니라면 gateway에도 SPRING_DATA_REDIS_HOST, SPRING_DATA_REDIS_PORT와
필요한 인증 설정을 제공한다. 인증 정보는 명령 이력이나 로그에 남기지 않는다.
실험과 운영은 Redis 인스턴스 또는 채널을 분리한다.

저장소 루트에서 터미널 A (MVC):

```powershell
.\gradlew.bat :bootRun --args="--spring.profiles.active=local --live.sse.transport=redis"
```

터미널 B (SSE):

```powershell
.\gradlew.bat :sse-gateway:bootRun --args="--spring.profiles.active=local"
```

gateway의 기본 주소는 127.0.0.1:8081이다. 두 서버가 시작된 뒤 브라우저에서 새 경기 실험을 준비하고
재생 버튼은 누르지 않는다. 발행은 mixed 실행기가 제어한다.

터미널 C:

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:STREAM_BASE_URL = 'http://127.0.0.1:8081'
node .\load-tests\sse\mixed.mjs --baseline

# 브라우저에서 기존 실험 종료 후 새 경기 준비
node .\load-tests\sse\mixed.mjs
```

990개 k6와 10개 Node.js 연결을 모두 gateway로 연결한다. BASE_URL은 경기 제어,
STREAM_BASE_URL은 SSE 구독·gateway 통계에 사용한다. 기존 5분/1초 발행 및 60초 읽기 중단 조건을 유지한다.
MVC 구독자 수는 gateway 연결을 포함하지 않으므로 실행기 `gateway` 로그의 subscribers를 확인한다.
Vite 개발 서버는 SSE 경로를 기본적으로 Gateway(127.0.0.1:8081)로 전달한다.
경기 조회와 익명 쿠키 발급 등 다른 API는 MVC(8080)로 전달한다.
기존 MVC SSE 전송을 비교하려면 프론트엔드 실행 터미널에서 SSE_PROXY_TARGET=http://localhost:8080을
명시하고 Vite를 재시작한다. 운영 환경에서는 Nginx의 SSE 경로를 Gateway로 연결한다.

일반 k6도 주소를 나눌 수 있다.

```powershell
.\load-tests\sse\run.ps1 -Connections 1000 -ConnectionsPerSecond 100 -HoldSeconds 300 -StreamBaseUrl http://127.0.0.1:8081
```

MVC 비교로 돌아가려면 MVC를 redis 옵션 없이 재시작하고 다음으로 실험 주소를 해제한다.

```powershell
Remove-Item Env:STREAM_BASE_URL -ErrorAction SilentlyContinue
```

## 결과와 설정

normal-phases.json과 summary.json은 정상 990개만 집계한다. gateway-summary.json에는 구독자 수,
retainedBytes, 종료 사유별 누적 수를 저장하며 timeline.jsonl에도 주기적으로 기록한다.
느린 연결의 정체 종료는 정상 연결 실패에 합산하지 않는다. 실행기는 읽기 중단 후 종료된 연결 수와
서버의 이번 실행 중 stalled 증가량을 대조한다. 이는 그룹 단위 검증이며, 실험 중 다른 부하 연결을 만들지 않는다.
서버가 닫아도 읽기 중단 중인 Node.js는 재개 후에야 종료를 관측할 수 있다.

| 앱 | 설정 | 기본값 |
|---|---|---|
| MVC | live.sse.transport | 기존 MVC (redis 지정 시 전환) |
| MVC | live.sse.redis-channel | soccer:sse:v1 |
| gateway | sse.redis.channel | soccer:sse:v1 |
| gateway | sse.mailbox-bytes | 1048576 |
| gateway | sse.stall-ms | 15000 |

재접속 백오프와 전체/IP/익명 쿠키 연결 제한은 적용되어 있다. 상세 설정은 CONNECTION-LIMITS.md를 따른다.
Gateway는 loopback에 기본 바인딩하며 운영 프록시와 배포 drain 정책은 환경에 맞게 구성해야 한다.

Redis Pub/Sub은 과거 메시지를 보관하지 않는다. 신규 연결은 CONNECT 후 다음 발행부터 받는다.
Redis 재구독 후 MVC 최신 상태 보충은 REDIS-RECOVERY.md, 프론트엔드 재연결 시 MVC 보충은 기존 클라이언트 정책을 따른다.
전역 버전 충돌 방지 체계는 포함하지 않는다. JPA 등 blocking 호출은 gateway 이벤트 루프에 추가하지 않는다.

WebFlux HTTP framing을 유지하면서 Reactor Netty pipeline의 실제 쓰기 promise를 관찰하므로
라이브러리 업그레이드 시 아래 실제 소켓 테스트를 반드시 실행한다.

## 검증

```powershell
.\gradlew.bat :sse-gateway:test
# PATH에 redis-server가 있으면 임시 포트·임시 폴더의 격리 Redis로 통합 검증
.\gradlew.bat :sse-gateway:test -PredisIntegration
node .\load-tests\sse\mixed-self-test.mjs --gateway
```

최신값 병합·바이트 상한·정체 시계, 실제 Netty 소켓의 느린 연결 종료/정상 연결 유지,
Redis envelope의 SSE 전달을 검증한다. 이는 실제 1,000개 부하 검증을 대신하지 않는다.
