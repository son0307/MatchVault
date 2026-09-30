# Redis 재구독 후 상태 보충

RedisSnapshotListener는 SubscriptionListener.onChannelSubscribed 알림을 받으면 복구 세대를 증가시킨다.
TCP 연결 완료가 아니라 Redis 채널 구독 확인이 기준이다. 최초 구독에도 실행된다.

RedisRecovery는 현재 구독자가 있는 경기마다 MVC의
GET /api/v1/live/recovery/fixtures/{fixtureId}를 호출하고 세 전체 스냅샷을 기존 SSE로 전달한다.
같은 경기를 보는 사용자 수와 관계없이 경기당 한 번 조회한다.
주기적 작업은 미보충 경기만 찾으며 성공한 경기를 계속 조회하지 않는다.
복구 도중 새로 만들어진 경기 방도 다음 검사에 포함한다.

| Gateway 속성 | 기본값 |
| --- | --- |
| sse.recovery.mvc-base-url | http://127.0.0.1:8080 |
| sse.recovery.concurrency | 4 |

MVC와 Gateway를 모두 새 코드로 재시작한다. 별도 호스트이면 위 MVC 주소를 지정한다.
기존 Redis 및 local 프로파일 설정은 유지한다. 운영 설정 파일은 변경하지 않았다.
조회 timeout은 5초, 오류 후 재시도는 최소 5초 뒤이며 경기 구독자가 없어지면 대상에서 사라진다.
복구 자체를 위해 SSE 연결을 끊거나 Redis에 다시 publish하지 않는다.

MVC 복구 조회는 Redis 캐시를 읽거나 쓰지 않는다. 같은 REPEATABLE_READ 트랜잭션에서
경기·팀 스탯·이벤트·선수 스탯을 읽는다.
정상 발행과 DB 동기화 경로는 기존 방식을 유지하며 별도 버전 메타데이터를 추가하지 않는다.
local replay는 진행 중인 메모리 상태를 반환한다.
가상 경기 multi 실험은 DB에 경기 자체가 없으므로 복구 endpoint 대상 데이터가 없다.

Gateway는 조회 시작 시 경기 방의 수신 횟수를 기록한다.
조회 도중 Redis 데이터가 들어오거나 방이 교체되면 조회 결과를 버리고 다시 조회한다.
이 보호는 조회 도중 수신한 이벤트를 오래된 조회 응답이 덮어쓰는 상황에 한정한다.
조회 적용 후 뒤늦게 도착하는 Redis 메시지까지 내용의 최신성을 비교하는 버전 체계는 없다.
최종 상태 보충은 FIXTURE_EVENTS/PLAYER_STATS 후 LIVE_SNAPSHOT 순으로 제안해
브라우저가 종료 상태를 받아 연결을 닫기 전에 다른 데이터를 적용할 수 있게 한다.

local 관찰 API의 recovery:
- epoch: Redis 구독 확인 횟수
- recoveredFixtures: 상태 보충을 적용한 경기 횟수
- failedReads: 실패한 조회/응답 검증 횟수

복구 endpoint는 공개 경기 데이터만 반환하지만 DB를 직접 조회한다.
운영 프록시에서는 gateway 내부 통신용으로 접근 범위를 제한하는 배치를 권장한다.
이번 변경은 자동 테스트로 검증하고 1,000개 부하 테스트는 수행하지 않는다.
실제 Redis/프록시 단절 후 마지막 발행이 없는 redis/final을 한 번 재확인하면 통합 환경 검증이 된다.
