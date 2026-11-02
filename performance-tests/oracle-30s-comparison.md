# Oracle 순위 캐시 30초 반복 부하 비교

이 비교는 `codex/ranking-cache-30s-comparison` 브랜치를 수동으로 바꾸어 배포한다. `main`은 변경하지 않는다. 이 브랜치의 첫 배포는 순위 조회에 기본 `cacheManager`의 non-locking Redis writer를 사용한다. 기준선 측정을 마치면 같은 브랜치에 키별 잠금 설정을 복구하는 커밋을 올려 다시 배포한다.

두 모드 모두 [`league-rankings-load-test.js`](league-rankings-load-test.js)의 `MODE=steady`를 사용한다. 각 VU는 30초 동안 같은 리그·시즌의 선수 순위를 반복 조회하고, 요청 사이에 기본 0.1초 쉰다. `WARM_UP=false`는 스크립트의 사전 적재만 생략한다. Redis 캐시 항목을 삭제하지는 않는다.

```powershell
k6 run `
  -e BASE_URL=https://matchvault.site `
  -e MODE=steady `
  -e TARGET=player `
  -e LEAGUE_ID=39 `
  -e SEASON=2025 `
  -e VUS=50 `
  -e DURATION=30s `
  -e WARM_UP=false `
  performance-tests/league-rankings-load-test.js
```

이전 테스트처럼 Cloudflare를 우회하려면 실행 시 `-e ORIGIN_IP=<운영 원본 IP>`를 추가한다. IP 값을 저장소에 기록하지 않는다. 두 모드에서 동일한 경로를 사용한다.

각 실행 직전에 대상 캐시 키가 비어 있는지 확인한다. 필요하면 선택한 순위 캐시 항목만 무효화한다. 운영 Redis 전체 삭제는 하지 않는다. 캐시 TTL은 10분이며, 30초 반복 부하의 p95/p99에는 첫 캐시 미스와 뒤이은 캐시 적중이 함께 들어간다. 최초 요청 지연, 최대 지연, 요청 수, 오류율, 집계 실행 횟수도 함께 기록한다. 서버 메트릭이나 집계 로그를 수집하지 못하면 응답 지연만으로 중복 집계 횟수를 판단하지 않는다.

현재 브랜치의 기본값은 비교용 non-locking이다. 키별 잠금 모드로 복구하는 두 번째 배포가 끝나기 전에는 운영 서버가 비잠금형으로 동작한다.
