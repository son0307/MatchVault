# 순위 캐시 부하 테스트

## Oracle 100→250 VU 혼합 조회 비교 브랜치

`codex/ranking-cache-mixed-ramp-baseline`은 비교용 임시 브랜치다. 첫 실험 커밋 `594c268`은 순위 캐시의 기본값을 비잠금형으로 설정했고, 다음 커밋부터는 기본값을 키별 잠금형으로 되돌렸다. 두 커밋 모두 세 순위 서비스가 캐시 미스 후 집계 메서드를 실행할 때 `Ranking aggregation started.` 로그를 남긴다. 로컬 프로필 제한은 이 브랜치에서 제거했으므로 `ranking.cache.baseline-nonlocking=true`를 운영 프로필에 지정하면 비잠금형으로 전환할 수 있다.

`ranking-cache-ramp.js`는 기존 단일 키 `stampede` 모드와 동일한 100 VU 20초 → 1초 증가 → 250 VU 10초 → 1초 감소 패턴을 3회 반복한다. 기본 조회 대상은 리그 39의 2024·2025·2026 시즌이며, 시즌마다 리그 선수 순위·리그 팀 순위·상위 4개 팀의 선수 순위를 순환한다. 준비 단계는 순위 API를 호출하지 않으며, 선택된 팀 ID를 시작 시 출력한다.

```powershell
k6 run -e BASE_URL=https://matchvault.site -e SEASONS=2024,2025,2026 -e TEAMS_PER_SEASON=4 performance-tests/ranking-cache-ramp.js
```

2026 시즌만 조회하려면 `SEASONS=2026`으로 실행한다. 이때 조회 키는 리그 선수 순위 1개, 리그 팀 순위 1개, 2026 시즌 상위 4팀 선수 순위 4개로 총 6개다. VU 단계와 나머지 조건은 동일하다.

```powershell
k6 run -e BASE_URL=https://matchvault.site -e SEASONS=2026 -e TEAMS_PER_SEASON=4 performance-tests/ranking-cache-ramp.js
```

2026 시즌 **리그 선수 순위 한 키만** 반복 조회하려면 `TARGET=league-player`를 추가한다. 이 모드의 준비 단계는 시즌 존재 여부만 확인하고 팀 순위 API를 호출하지 않는다.

```powershell
k6 run -e BASE_URL=https://matchvault.site -e SEASONS=2026 -e TARGET=league-player performance-tests/ranking-cache-ramp.js
```

두 모드에서 출력된 조회 키 목록이 같아야 한다. 전체 및 `phase=peak`의 HTTP 실패·p95/p99·처리량과, 각 실험 시간 범위의 `Ranking aggregation started.` 로그 건수를 함께 기록한다. 이 로그는 SQL 문 실행 횟수가 아니라 캐시 미스 후 서비스 집계 메서드 진입 횟수다. 첫 요청 후 캐시가 채워질 수 있으므로 세 피크를 모두 cold-cache 결과로 해석하지 않는다. 이전 단일 키 100→250 VU 테스트와도 직접적인 처리량 비교를 하지 않는다.

2026-11-02 로컬 50 VU 실행 결과는 [결과 기록](results-2026-11-02.md)에 정리했다.

## 운영 Hikari 커넥션 기록

이 테스트 브랜치의 운영 프로필은 Actuator `health,metrics`를 노출한다. 앱 포트는 `127.0.0.1`에만 바인딩되고 Nginx는 `/actuator/metrics`를 프록시하지 않는다. 배포 묶음의 `monitoring/record-hikari.py`가 Nginx의 활성 백엔드 포트(8082 또는 8083)를 읽어 Hikari `active`, `pending`, `max`를 CSV에 기록한다. Oracle Cloud 인스턴스에서 테스트 직전에 다음 명령을 실행하고, `Recording` 메시지가 보이면 별도 터미널에서 캐시를 비운 뒤 부하 테스트를 시작한다.

```bash
python3 /opt/match-vault/current/monitoring/record-hikari.py \
  --seconds 45 --interval 0.1 --output "$HOME/hikari-test.csv"
```

CSV의 최대 `active`와 `pending`을 집계 로그 시간대와 비교한다. 실험 후에는 메트릭 노출 설정이 없는 배포로 되돌린다.

`ranking-cache.js`는 1 VU당 1회 요청하는 버스트를 만든다. 기본값은 50 VU, 시즌 `2025,2024,2026`, 로컬 `http://127.0.0.1:8080`이다. `ranking_request_duration`과 `ranking_request_failed`는 준비 단계의 시즌 목록·팀 순위 조회를 제외하고 실제 순위 요청만 집계한다.

```powershell
$env:BASE_URL='http://127.0.0.1:8080'
$env:SEASONS='2025,2024,2026'
$env:VUS='50'
k6 run -e TEST_CASE=same-key -e SEASONS=2025 performance-tests/ranking-cache.js
k6 run -e TEST_CASE=mixed-season performance-tests/ranking-cache.js
k6 run -e TEST_CASE=mixed-team -e TEAMS_PER_SEASON=4 performance-tests/ranking-cache.js
k6 run -e TEST_CASE=mixed-all -e TEAMS_PER_SEASON=4 performance-tests/ranking-cache.js
```

| 테스트 | 요청 분포 | 확인할 점 |
| --- | --- | --- |
| `same-key` | 모든 VU가 같은 시즌의 리그 선수 순위 | 이전 2025 단일 키 테스트와 비교, 동일 키 집계 중복 여부 |
| `mixed-season` | 시즌별 리그 선수 순위 | 같은 캐시 이름의 다른 키가 서로 막지 않는지 |
| `mixed-team` | 시즌별 상위 N팀의 선수 순위 | 일부 VU는 같은 키를 공유하고, 다른 팀 키는 병렬로 계산되는지 |
| `mixed-all` | 리그 선수·리그 팀·팀 선수 순위 혼합 | 서로 다른 캐시와 키가 겹칠 때 응답 지연과 실패율 |

`mixed-team`과 `mixed-all`은 준비 단계에 `/api/v1/teams/standings?season=...`을 호출해 실제 팀 ID를 얻는다. 팀이 2개 미만인 시즌은 실패시킨다. 응답이 비어 있으면 해당 시즌에 집계 데이터가 없는 것으로 보고 `ranking has data` 검증이 실패한다.

**cold-cache 결과**는 해당 순위 키가 비어 있을 때만 해석한다. 같은 테스트를 반복하면 캐시가 채워져 warm-cache 결과가 된다. 키 삭제나 Redis 초기화는 이 스크립트에 넣지 않았다. 기존 문서의 2025 결과와 비교하려면 동일한 데이터량·서버·DB 조건을 맞추고, 요청 대상 키의 캐시 상태를 확인한다. 현재 순위 캐시의 값 TTL은 10분이다.

로컬 Redis(`127.0.0.1:6379`)에서만 순위 캐시를 cold 상태로 만들려면 아래처럼 세대 번호를 올린다. 다른 Redis 키를 삭제하지 않고, 이전 세대의 캐시 값은 TTL이 끝날 때까지 남는다. 공유 환경에서는 실행하지 않는다.

```powershell
python performance-tests/bump-ranking-cache-epoch.py leaguePlayerRankings
python performance-tests/bump-ranking-cache-epoch.py teamPlayerRankings leagueTeamRankings
```

## 비잠금형 기준선과 비교

로컬 프로필에서만 `--ranking.cache.baseline-nonlocking=true` 프로그램 인자로 서버를 시작하면 순위 조회도 실제 기본 `cacheManager`를 사용한다. `@Cacheable(sync = true)`와 캐시 값 TTL은 유지된다. 비교용 서버를 실행하기 전에 기존 8080 서버를 종료하고 같은 DB·Redis·JVM 조건을 유지한다. 테스트 후에는 인자를 제거하고 다시 시작한다.

비잠금형 기준선에는 세대 번호가 적용되지 않으므로, 버스트 직전에 아래 명령으로 **로컬 Redis의 선택한 순위 캐시 엔트리만** 제거한다. `--clear-baseline` 없이 실행하면 현재 키별 잠금 방식의 세대 번호를 올린다. 두 방식 모두 실행 직전에 cold 상태로 만들고 같은 `TEST_CASE`, `SEASONS`, `VUS`를 사용한다.

```powershell
python performance-tests/bump-ranking-cache-epoch.py --clear-baseline leaguePlayerRankings
k6 run -e TEST_CASE=same-key -e SEASONS=2025 -e VUS=50 performance-tests/ranking-cache.js
```

`--inspect`는 키를 바꾸지 않고 기본 매니저의 raw 키와 키별 잠금 매니저의 세대 키 개수만 보여 준다. 비잠금형 비교를 마친 뒤에는 프로그램 인자를 제거하고 로컬 서버를 재시작한다.

비잠금형 모드는 동일 키의 중복 집계로 DB에 부하를 줄 수 있다. 이전 기록의 50 VU를 상한으로 사용하고, 먼저 10 VU로 서버 상태를 확인한다. 비교할 때는 k6의 `ranking_request_duration`, 실패율과 함께 DB 커넥션·CPU, 서버의 집계 실행 횟수를 기록한다.

기록할 값은 `ranking_request_duration`의 p95/p99/최대, `ranking_request_failed`, HTTP 503 건수, `ranking has data` 실패, 서버의 `ranking.cache.load`와 `ranking.cache.wait` 분포, `ranking.cache.fallback_rejected` 건수다. k6의 HTTP 지연만으로 집계 실행 횟수를 증명할 수는 없다. 서버 메트릭을 함께 수집해야 한다.
