import http from 'k6/http';
import { check, sleep } from 'k6';

// 💡 1. 테스트 설정 (부하량 조절)
export let options = {
    vus: 50,           // Virtual Users (동시 접속자 수 50명)
    duration: '30s',   // 테스트 진행 시간 (10초 동안 융단폭격)
};

// 팀 목록 배열 (더 넓은 범위의 랜덤 테스트를 위해 팀도 랜덤화)
const teams = ['forge', 'tottenham', 'arsenal', 'mancity', 'liverpool'];

// 💡 2. 실제 유저가 할 행동 (10초 동안 50명이 이 함수를 미친듯이 반복 실행함)
export default function () {
    // 1부터 15 사이의 랜덤 숫자 생성 (각 팀당 15명의 더미 선수가 있으므로)
    let randomNum = Math.floor(Math.random() * 15) + 1;

    // 5개의 팀 중 하나를 랜덤으로 선택
    let randomTeam = teams[Math.floor(Math.random() * teams.length)];

    // 백틱(`)을 사용하여 랜덤 팀과 랜덤 번호가 조합된 동적 URL 생성
    // 예: /players/player_arsenal_7, /players/player_tottenham_12 등
    let url = `http://localhost:8080/api/v1/players/player_${randomTeam}_${randomNum}/stats`;

    // 동적 URL로 API 호출
    let res = http.get(url);

    // 정상적으로 200 OK가 떨어졌는지 검증
    check(res, {
        'status is 200': (r) => r.status === 200,
        'transaction is fast': (r) => r.timings.duration < 200, // 200ms 이내 응답인가?
    });

    // 유저가 새로고침하는 텀을 약간 줌 (0.1초 대기)
    sleep(0.1);
}