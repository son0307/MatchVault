# 순위 캐시 잠금 운영 메모

순위 캐시(`teamPlayerRankings`, `leaguePlayerRankings`, `leagueTeamRankings`)는 캐시 키별 Redis 잠금을 사용한다. 기본 캐시는 이 변경의 대상이 아니다.

## 현재 기본값

| 속성 | 기본값 | 의미 |
| --- | --- | --- |
| `ranking.cache.lock-ttl` | `60s` | 잠금 보유자가 종료되었을 때 잠금 키가 자동으로 사라지는 시간 |
| `ranking.cache.max-wait` | `10s` | 다른 요청이 계산 중일 때 캐시 값을 기다리는 최대 시간 |
| `ranking.cache.fallback-permits` | `2` | 대기 초과 또는 Redis 오류 시 인스턴스별 동시 DB 직접 조회 수 |

대기 시간이 초과되면 제한된 수의 요청만 DB에서 순위를 계산하고, 이 결과는 캐시에 쓰지 않는다. 여유가 없으면 `RANKING_CACHE_BUSY`(503)를 반환한다. 잠금 해제는 소유자 ID가 일치할 때만 수행한다. 캐시 전체 무효화는 세대 번호를 증가시켜, 이전 계산이 늦게 끝나더라도 새 세대의 캐시 키에 값을 쓰지 못하게 한다.

## 관측과 조정

- `ranking.cache.load`: 실제 순위 계산 시간. `cache`, `path`, `outcome` 태그가 있다.
- `ranking.cache.wait`: 잠금 경합 시 대기 시간. `cache` 태그가 있다.
- `ranking.cache.request`: 캐시 적중, 대기 초과, Redis 오류 건수. `outcome` 태그가 있다.
- `ranking.cache.lock_lost`, `ranking.cache.fallback_rejected`: 잠금 소유권 상실과 DB 직접 조회 제한 건수.

두 시간 지표는 히스토그램을 기록한다. 운영 트래픽에서 계산 시간의 p99와 최대값, 대기 시간, 503 비율을 확인한 뒤 TTL과 대기 상한을 조정한다. 위 기본값은 운영 측정치가 없는 상태의 초기값이다. Actuator의 `metrics` 엔드포인트는 별도로 허용된 관리 경로 또는 연결된 메트릭 수집기를 통해 확인한다.

잠금 TTL이 끝난 직후 기존 보유자가 소유권을 확인하고 캐시에 쓰는 사이에 극히 짧은 경합 구간은 남는다. 엄격한 단일 쓰기가 필요해지면 소유권 확인과 캐시 쓰기를 하나의 Redis Lua 명령으로 묶거나 fencing token을 적용해야 한다. 순위 캐시는 TTL과 무효화로 갱신되는 조회 데이터이므로 현재는 이 비용을 들이지 않았다.
