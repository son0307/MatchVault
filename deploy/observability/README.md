# Match Vault 로컬 로그 수집

이 구성은 로컬 개발 환경에서 다음 흐름을 검증하기 위한 것이다.

```text
Logback -> logs/application.json -> Logstash -> Elasticsearch -> Kibana
```

Elasticsearch 보안 기능을 비활성화했으므로 운영 환경에서 그대로 사용하면 안 된다.
호스트 포트는 외부에 노출되지 않도록 `127.0.0.1`에만 바인딩한다.

## 1. 애플리케이션 로그 생성

프로젝트 루트에서 운영 프로필로 실행한다. 기본 로그 경로가
`logs/application.json`이므로 별도의 로그 경로 환경 변수는 필요하지 않다.

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
.\gradlew.bat bootRun
```

경로를 변경해야 하는 환경에서만 `LOG_FILE_NAME`과 `LOG_ARCHIVE_PATTERN`을 지정한다.

## 2. Elasticsearch, Logstash, Kibana 실행

다른 터미널에서 다음 명령을 실행한다.

```powershell
docker compose -f deploy/observability/compose.yml up -d
docker compose -f deploy/observability/compose.yml ps
```

Logstash는 첫 실행 시 `application.json`을 처음부터 읽는다. 이후 읽은 위치는
`logstash-data` 볼륨의 sincedb 파일에 저장하므로 컨테이너를 재시작해도 이미 읽은
로그를 처음부터 다시 적재하지 않는다.

## 3. 수집 결과 확인

Elasticsearch 상태와 생성된 인덱스를 확인한다.

```powershell
Invoke-RestMethod http://localhost:9200/_cluster/health
Invoke-RestMethod http://localhost:9200/_cat/indices/match-vault-logs-*?v
```

최근 로그 문서를 확인한다.

```powershell
$body = '{"size":5,"sort":[{"@timestamp":{"order":"desc"}}]}'
Invoke-RestMethod -Method Post `
  -Uri http://localhost:9200/match-vault-logs-*/_search `
  -ContentType application/json `
  -Body $body
```

Logstash 자체 상태나 오류는 다음 명령으로 확인한다.

```powershell
Invoke-RestMethod http://localhost:9600/_node/stats/pipelines
docker compose -f deploy/observability/compose.yml logs logstash
```

## 4. Kibana에서 로그 확인

브라우저에서 `http://localhost:5601`에 접속한다.

1. **Management > Stack Management > Data Views**로 이동한다.
2. `Create data view`를 선택한다.
3. 이름과 인덱스 패턴에 `match-vault-logs-*`를 입력한다.
4. Timestamp field로 `@timestamp`를 선택해 생성한다.
5. **Analytics > Discover**에서 생성한 Data View를 선택한다.

필요에 따라 `log.level`, `service.name`, `http.request.id`,
`http.request.method`, `url.path`, `event.outcome` 필드를 열로 추가하거나 필터로 사용할 수 있다.

## 종료

데이터 볼륨을 유지하면서 컨테이너만 종료한다.

```powershell
docker compose -f deploy/observability/compose.yml down
```
