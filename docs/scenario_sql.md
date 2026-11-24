# 시나리오별 화면·버튼과 실행 SQL

시연 순서대로 화면마다 버튼을 누르면 실행되는 SQL을 실행 순서대로 정리했다. SQL은 각 Repository의 상수(`클래스.상수`)에서 그대로 옮겼으므로 실제 실행한 SQL과 같다. 화면 아래쪽 "이 화면이 실행한 SQL"에 나오는 것도 같은 상수다.

> 이 문서는 `scripts/gen_scenario_sql.py`로 만든다. 직접 고치지 말고, 코드의 SQL을 바꾼 뒤 프로젝트 폴더에서 `python scripts/gen_scenario_sql.py`를 다시 실행한다.

- **트랜잭션 범위:** 데이터를 바꾸는 버튼은 Service 메서드 하나에 `@Transactional`이 붙어 있어, 그 버튼이 실행하는 SQL 전부가 한 트랜잭션이다. 규칙 위반(`BusinessException`)이나 DB 오류가 나면 전부 롤백한다. 조회 화면은 트랜잭션 없이 SQL마다 자동 커밋한다.
- **`?`:** JdbcTemplate에 넘기는 값이다. 직원ID, 프로젝트ID 같은 값은 현재 사용자와 화면에서 고른 값으로 채운다.
- 데이터를 처음 상태로 되돌리려면 `docker` 폴더에서 `docker compose down -v` 후 `docker compose up -d`.

## 0. 공통: 현재 사용자 (모든 화면)

로그인 대신 화면 오른쪽 위에서 현재 사용자를 고른다. 세션에는 직원ID만 저장한다.

#### 모든 화면을 열 때 (헤더)

트랜잭션 없음 (조회만)

1. 현재 사용자 드롭다운: 재직 직원 목록 — `CurrentUserRepository.FIND_ACTIVE_EMPLOYEES`

```sql
SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.AUTH_ID, a.AUTH_NM
FROM TB_EMP e
JOIN TB_AUTH a ON a.AUTH_ID = e.AUTH_ID
WHERE e.EMPL_STAT = '재직'
ORDER BY e.AUTH_ID DESC, e.EMP_NM
```

2. 세션의 직원ID로 현재 사용자 조회 (권한·직원구분 확인, 요청마다) — `CurrentUserRepository.FIND_ACTIVE_EMPLOYEE_BY_ID`

```sql
SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.AUTH_ID, a.AUTH_NM
FROM TB_EMP e
JOIN TB_AUTH a ON a.AUTH_ID = e.AUTH_ID
WHERE e.EMP_ID = ?
  AND e.EMPL_STAT = '재직'
```

아래 화면별 목록에서는 이 두 SQL을 반복해 적지 않는다.

## 시나리오 1: 평가메모 → 평가 제출 → 보정점수 조회

### 1. 내 프로젝트

- 주소: `GET /my/projects`
- 시연 사용자: 김다니엘 (PM, 11-0007)

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 내가 참여한 진행/지연 프로젝트 — `MemoRepository.FIND_MY_ACTIVE_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM,
       MIN(h.STRT_DT) AS MY_STRT_DT,
       CASE WHEN COUNT(*) = COUNT(h.END_DT) THEN MAX(h.END_DT) END AS MY_END_DT
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p      ON p.PRJ_ID = pt.PRJ_ID
JOIN TB_CLIENT c   ON c.CLIENT_ID = p.CLIENT_ID
JOIN TB_ROLE r     ON r.ROLE_ID = pt.ROLE_ID
JOIN TB_PRJ_HIST h ON h.EMP_ID = pt.EMP_ID AND h.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND p.PRJ_STAT IN ('진행', '지연')
GROUP BY p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM
ORDER BY p.STRT_DT DESC
```

### 2. 내 프로젝트 › 팀원 보기

- 주소: `GET /my/projects/{prjId}`
- 시연 사용자: 김다니엘

#### [팀원 보기]

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 이 프로젝트에서 내 참여 (참여자만 볼 수 있음) — `MemoRepository.FIND_PARTICIPATION`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND pt.PRJ_ID = ?
```

2. 프로젝트 팀원 전체 — `MemoRepository.FIND_TEAM_MEMBERS`

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       MIN(h.STRT_DT) AS STRT_DT,
       CASE WHEN COUNT(*) = COUNT(h.END_DT) THEN MAX(h.END_DT) END AS END_DT
FROM TB_PRJ_PTCP t
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
JOIN TB_PRJ_HIST h ON h.EMP_ID = t.EMP_ID AND h.PRJ_ID = t.PRJ_ID
WHERE t.PRJ_ID = ?
GROUP BY t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. 메모 대상 ([메모] 버튼을 띄울 팀원) — `MemoRepository.FIND_MEMO_TARGETS`

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_MEMO m
        WHERE m.WRTR_EMP_ID = me.EMP_ID
          AND m.TRGT_EMP_ID = t.EMP_ID
          AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
FROM TB_PRJ_PTCP me
JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
WHERE me.EMP_ID = ?
  AND me.PRJ_ID = ?
  AND ( me.ROLE_ID = 'PM'
        OR EXISTS (
             SELECT 1
             FROM TB_PRJ_HIST mh
             JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
             WHERE mh.EMP_ID = me.EMP_ID
               AND mh.PRJ_ID = me.PRJ_ID
               AND GREATEST(mh.STRT_DT, th.STRT_DT)
                   <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
           ) )
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

### 3. 대상별 메모

- 주소: `GET /memos/{prjId}/{trgtEmpId}`
- 시연 사용자: 김다니엘

#### [메모] (화면 열기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 내 참여 — `MemoRepository.FIND_PARTICIPATION`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND pt.PRJ_ID = ?
```

2. 대상 확인 (메모 대상 목록에 있는지) — `MemoRepository.FIND_MEMO_TARGETS`

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_MEMO m
        WHERE m.WRTR_EMP_ID = me.EMP_ID
          AND m.TRGT_EMP_ID = t.EMP_ID
          AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
FROM TB_PRJ_PTCP me
JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
WHERE me.EMP_ID = ?
  AND me.PRJ_ID = ?
  AND ( me.ROLE_ID = 'PM'
        OR EXISTS (
             SELECT 1
             FROM TB_PRJ_HIST mh
             JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
             WHERE mh.EMP_ID = me.EMP_ID
               AND mh.PRJ_ID = me.PRJ_ID
               AND GREATEST(mh.STRT_DT, th.STRT_DT)
                   <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
           ) )
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. 제출된 평가가 있는지 (있으면 작성·수정 막음) — `MemoRepository.COUNT_SUBMITTED_EVAL` _(프로젝트가 진행/지연일 때만 실행)_

```sql
SELECT COUNT(*)
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
WHERE e.PRJ_ID = ?
  AND e.EVLTE_EMP_ID = ?
  AND e.EVAL_STAT = '제출'
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

4. 이 대상에게 내가 쓴 메모 — `MemoRepository.FIND_MEMOS_BY_TARGET`

```sql
SELECT MEMO_ID, MEMO_CN, WRT_DTTM, MOD_DTTM
FROM TB_EVAL_MEMO
WHERE WRTR_EMP_ID = ?
  AND TRGT_EMP_ID = ?
  AND PRJ_ID = ?
ORDER BY WRT_DTTM DESC, MEMO_ID DESC
```

#### [저장] (새 메모)

**한 트랜잭션** (`MemoService.create`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 내 참여 (프로젝트 상태) — `MemoRepository.FIND_PARTICIPATION`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND pt.PRJ_ID = ?
```

2. 허용된 대상인지 — `MemoRepository.FIND_MEMO_TARGETS`

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_MEMO m
        WHERE m.WRTR_EMP_ID = me.EMP_ID
          AND m.TRGT_EMP_ID = t.EMP_ID
          AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
FROM TB_PRJ_PTCP me
JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
WHERE me.EMP_ID = ?
  AND me.PRJ_ID = ?
  AND ( me.ROLE_ID = 'PM'
        OR EXISTS (
             SELECT 1
             FROM TB_PRJ_HIST mh
             JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
             WHERE mh.EMP_ID = me.EMP_ID
               AND mh.PRJ_ID = me.PRJ_ID
               AND GREATEST(mh.STRT_DT, th.STRT_DT)
                   <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
           ) )
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. 제출된 평가가 있는지 — `MemoRepository.COUNT_SUBMITTED_EVAL`

```sql
SELECT COUNT(*)
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
WHERE e.PRJ_ID = ?
  AND e.EVLTE_EMP_ID = ?
  AND e.EVAL_STAT = '제출'
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

4. 메모 작성 — `MemoRepository.INSERT_MEMO`

```sql
INSERT INTO TB_EVAL_MEMO (MEMO_CN, WRT_DTTM, WRTR_EMP_ID, TRGT_EMP_ID, PRJ_ID)
VALUES (?, NOW(), ?, ?, ?)
```

#### [수정]

**한 트랜잭션** (`MemoService.update`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 본인 메모인지 — `MemoRepository.FIND_OWNED_MEMO`

```sql
SELECT MEMO_ID, PRJ_ID, TRGT_EMP_ID
FROM TB_EVAL_MEMO
WHERE MEMO_ID = ?
  AND WRTR_EMP_ID = ?
```

2. 프로젝트 상태 — `MemoRepository.FIND_PARTICIPATION`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND pt.PRJ_ID = ?
```

3. 제출된 평가가 있는지 — `MemoRepository.COUNT_SUBMITTED_EVAL`

```sql
SELECT COUNT(*)
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
WHERE e.PRJ_ID = ?
  AND e.EVLTE_EMP_ID = ?
  AND e.EVAL_STAT = '제출'
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

4. 메모 수정 — `MemoRepository.UPDATE_MEMO`

```sql
UPDATE TB_EVAL_MEMO
SET MEMO_CN = ?,
    MOD_DTTM = NOW()
WHERE MEMO_ID = ?
  AND WRTR_EMP_ID = ?
```

#### [삭제] → 확인 창

**한 트랜잭션** (`MemoService.delete`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 본인 메모인지 — `MemoRepository.FIND_OWNED_MEMO`

```sql
SELECT MEMO_ID, PRJ_ID, TRGT_EMP_ID
FROM TB_EVAL_MEMO
WHERE MEMO_ID = ?
  AND WRTR_EMP_ID = ?
```

2. 메모 삭제 — `MemoRepository.DELETE_MEMO`

```sql
DELETE FROM TB_EVAL_MEMO
WHERE MEMO_ID = ?
  AND WRTR_EMP_ID = ?
```

### 4. 평가메모 (메뉴)

- 주소: `GET /memos`
- 시연 사용자: 김다니엘

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 새 메모 ① 프로젝트 목록 — `MemoRepository.FIND_MY_ACTIVE_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM,
       MIN(h.STRT_DT) AS MY_STRT_DT,
       CASE WHEN COUNT(*) = COUNT(h.END_DT) THEN MAX(h.END_DT) END AS MY_END_DT
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p      ON p.PRJ_ID = pt.PRJ_ID
JOIN TB_CLIENT c   ON c.CLIENT_ID = p.CLIENT_ID
JOIN TB_ROLE r     ON r.ROLE_ID = pt.ROLE_ID
JOIN TB_PRJ_HIST h ON h.EMP_ID = pt.EMP_ID AND h.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND p.PRJ_STAT IN ('진행', '지연')
GROUP BY p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM
ORDER BY p.STRT_DT DESC
```

2. 새 메모 ② 팀원 목록 — `MemoRepository.FIND_MEMO_TARGETS` _(내 진행/지연 프로젝트마다 한 번씩 실행)_

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_MEMO m
        WHERE m.WRTR_EMP_ID = me.EMP_ID
          AND m.TRGT_EMP_ID = t.EMP_ID
          AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
FROM TB_PRJ_PTCP me
JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
WHERE me.EMP_ID = ?
  AND me.PRJ_ID = ?
  AND ( me.ROLE_ID = 'PM'
        OR EXISTS (
             SELECT 1
             FROM TB_PRJ_HIST mh
             JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
             WHERE mh.EMP_ID = me.EMP_ID
               AND mh.PRJ_ID = me.PRJ_ID
               AND GREATEST(mh.STRT_DT, th.STRT_DT)
                   <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
           ) )
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. 내가 쓴 메모 전체 — `MemoRepository.FIND_MY_MEMOS`

```sql
SELECT m.MEMO_ID, m.PRJ_ID, p.PRJ_NM, p.PRJ_STAT,
       m.TRGT_EMP_ID, e.EMP_NM AS TRGT_EMP_NM,
       m.MEMO_CN, m.WRT_DTTM, m.MOD_DTTM
FROM TB_EVAL_MEMO m
JOIN TB_PRJ p ON p.PRJ_ID = m.PRJ_ID
JOIN TB_EMP e ON e.EMP_ID = m.TRGT_EMP_ID
WHERE m.WRTR_EMP_ID = ?
ORDER BY m.WRT_DTTM DESC, m.MEMO_ID DESC
```

#### [저장] (새 메모)

**한 트랜잭션** (`MemoService.create`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 내 참여 — `MemoRepository.FIND_PARTICIPATION`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
FROM TB_PRJ_PTCP pt
JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
WHERE pt.EMP_ID = ?
  AND pt.PRJ_ID = ?
```

2. 허용된 대상인지 — `MemoRepository.FIND_MEMO_TARGETS`

```sql
SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_MEMO m
        WHERE m.WRTR_EMP_ID = me.EMP_ID
          AND m.TRGT_EMP_ID = t.EMP_ID
          AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
FROM TB_PRJ_PTCP me
JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
WHERE me.EMP_ID = ?
  AND me.PRJ_ID = ?
  AND ( me.ROLE_ID = 'PM'
        OR EXISTS (
             SELECT 1
             FROM TB_PRJ_HIST mh
             JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
             WHERE mh.EMP_ID = me.EMP_ID
               AND mh.PRJ_ID = me.PRJ_ID
               AND GREATEST(mh.STRT_DT, th.STRT_DT)
                   <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
           ) )
ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. 제출된 평가가 있는지 — `MemoRepository.COUNT_SUBMITTED_EVAL`

```sql
SELECT COUNT(*)
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
WHERE e.PRJ_ID = ?
  AND e.EVLTE_EMP_ID = ?
  AND e.EVAL_STAT = '제출'
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

4. 메모 작성 — `MemoRepository.INSERT_MEMO`

```sql
INSERT INTO TB_EVAL_MEMO (MEMO_CN, WRT_DTTM, WRTR_EMP_ID, TRGT_EMP_ID, PRJ_ID)
VALUES (?, NOW(), ?, ?, ?)
```

### 5. 내 평가

- 주소: `GET /evaluations`
- 시연 사용자: 김직수 (평가자, 25-0001)

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 내가 평가자인 PM·동료 평가 — `EvaluationRepository.FIND_MY_EVALUATIONS`

```sql
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
       e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM,
       (SELECT COUNT(*)
        FROM TB_EVAL_QSTN_SCORE s
        WHERE s.EVAL_ID = e.EVAL_ID) AS SCORE_CNT,
       (SELECT COUNT(*)
        FROM TB_EVAL_QSTN q
        WHERE (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
           OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')) AS QSTN_CNT
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
WHERE (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
ORDER BY FIELD(e.EVAL_STAT, '작성중', '제출'), e.EVAL_DT DESC, e.EVAL_ID
```

### 6. 평가 화면

- 주소: `GET /evaluations/{evalId}`
- 시연 사용자: 김직수 (예: 평가 1495)

#### [작성] (화면 열기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 평가 한 건 (내가 평가자인지) — `EvaluationRepository.FIND_MY_EVALUATION`

```sql
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
       e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
WHERE e.EVAL_ID = ?
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

2. 평가구분에 맞는 문항과 입력한 점수 — `EvaluationRepository.FIND_QUESTIONS`

```sql
SELECT q.QSTN_ID, q.QSTN_CN, q.EVAL_ITEM_ID, s.SCORE
FROM TB_EVAL e
JOIN TB_EVAL_QSTN q
  ON (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
  OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')
LEFT JOIN TB_EVAL_QSTN_SCORE s ON s.EVAL_ID = e.EVAL_ID AND s.QSTN_ID = q.QSTN_ID
WHERE e.EVAL_ID = ?
ORDER BY q.EVAL_ITEM_ID DESC, q.QSTN_ID
```

3. 평가항목별 내용 — `EvaluationRepository.FIND_CONTENTS`

```sql
SELECT i.EVAL_ITEM_ID, i.EVAL_ITEM_NM, c.ADV, c.DISADV, c.EXPC
FROM TB_EVAL_ITEM i
LEFT JOIN TB_EVAL_CN c ON c.EVAL_ID = ? AND c.EVAL_ITEM_ID = i.EVAL_ITEM_ID
ORDER BY i.EVAL_ITEM_ID DESC
```

4. 오른쪽: 이 피평가자에 대해 내가 쓴 메모 — `MemoRepository.FIND_MEMOS_BY_TARGET` _(작성중인 평가만 실행 (제출된 평가는 메모가 이미 삭제됨))_

```sql
SELECT MEMO_ID, MEMO_CN, WRT_DTTM, MOD_DTTM
FROM TB_EVAL_MEMO
WHERE WRTR_EMP_ID = ?
  AND TRGT_EMP_ID = ?
  AND PRJ_ID = ?
ORDER BY WRT_DTTM DESC, MEMO_ID DESC
```

#### [임시 저장]

**한 트랜잭션** (`EvaluationService.saveDraft`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 평가 한 건 (작성중인지) — `EvaluationRepository.FIND_MY_EVALUATION`

```sql
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
       e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
WHERE e.EVAL_ID = ?
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

2. 받을 수 있는 문항 — `EvaluationRepository.FIND_QUESTIONS`

```sql
SELECT q.QSTN_ID, q.QSTN_CN, q.EVAL_ITEM_ID, s.SCORE
FROM TB_EVAL e
JOIN TB_EVAL_QSTN q
  ON (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
  OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')
LEFT JOIN TB_EVAL_QSTN_SCORE s ON s.EVAL_ID = e.EVAL_ID AND s.QSTN_ID = q.QSTN_ID
WHERE e.EVAL_ID = ?
ORDER BY q.EVAL_ITEM_ID DESC, q.QSTN_ID
```

3. 입력한 점수 저장 — `EvaluationRepository.UPSERT_SCORE` _(문항마다: 입력한 칸)_

```sql
INSERT INTO TB_EVAL_QSTN_SCORE (EVAL_ID, QSTN_ID, SCORE)
VALUES (?, ?, ?) AS new
ON DUPLICATE KEY UPDATE SCORE = new.SCORE
```

4. 비운 점수 지우기 — `EvaluationRepository.DELETE_SCORE` _(문항마다: 비운 칸)_

```sql
DELETE FROM TB_EVAL_QSTN_SCORE
WHERE EVAL_ID = ?
  AND QSTN_ID = ?
```

5. 평가항목 목록 — `EvaluationRepository.FIND_CONTENTS`

```sql
SELECT i.EVAL_ITEM_ID, i.EVAL_ITEM_NM, c.ADV, c.DISADV, c.EXPC
FROM TB_EVAL_ITEM i
LEFT JOIN TB_EVAL_CN c ON c.EVAL_ID = ? AND c.EVAL_ITEM_ID = i.EVAL_ITEM_ID
ORDER BY i.EVAL_ITEM_ID DESC
```

6. 평가내용 저장 — `EvaluationRepository.UPSERT_CONTENT` _(항목마다 (WORK, COMM))_

```sql
INSERT INTO TB_EVAL_CN (EVAL_ID, EVAL_ITEM_ID, ADV, DISADV, EXPC)
VALUES (?, ?, ?, ?, ?) AS new
ON DUPLICATE KEY UPDATE ADV = new.ADV,
                        DISADV = new.DISADV,
                        EXPC = new.EXPC
```

#### [제출] → 확인 창 → [제출]

**한 트랜잭션** (`EvaluationService.submit`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 평가 한 건 — `EvaluationRepository.FIND_MY_EVALUATION`

```sql
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
       e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM
FROM TB_EVAL e
LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
WHERE e.EVAL_ID = ?
  AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
```

2. ① 화면 입력 저장: 문항 — `EvaluationRepository.FIND_QUESTIONS`

```sql
SELECT q.QSTN_ID, q.QSTN_CN, q.EVAL_ITEM_ID, s.SCORE
FROM TB_EVAL e
JOIN TB_EVAL_QSTN q
  ON (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
  OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')
LEFT JOIN TB_EVAL_QSTN_SCORE s ON s.EVAL_ID = e.EVAL_ID AND s.QSTN_ID = q.QSTN_ID
WHERE e.EVAL_ID = ?
ORDER BY q.EVAL_ITEM_ID DESC, q.QSTN_ID
```

3. ① 점수 저장 — `EvaluationRepository.UPSERT_SCORE` _(문항마다)_

```sql
INSERT INTO TB_EVAL_QSTN_SCORE (EVAL_ID, QSTN_ID, SCORE)
VALUES (?, ?, ?) AS new
ON DUPLICATE KEY UPDATE SCORE = new.SCORE
```

4. ① 비운 점수 지우기 — `EvaluationRepository.DELETE_SCORE` _(문항마다)_

```sql
DELETE FROM TB_EVAL_QSTN_SCORE
WHERE EVAL_ID = ?
  AND QSTN_ID = ?
```

5. ① 평가항목 목록 — `EvaluationRepository.FIND_CONTENTS`

```sql
SELECT i.EVAL_ITEM_ID, i.EVAL_ITEM_NM, c.ADV, c.DISADV, c.EXPC
FROM TB_EVAL_ITEM i
LEFT JOIN TB_EVAL_CN c ON c.EVAL_ID = ? AND c.EVAL_ITEM_ID = i.EVAL_ITEM_ID
ORDER BY i.EVAL_ITEM_ID DESC
```

6. ① 평가내용 저장 — `EvaluationRepository.UPSERT_CONTENT` _(항목마다)_

```sql
INSERT INTO TB_EVAL_CN (EVAL_ID, EVAL_ITEM_ID, ADV, DISADV, EXPC)
VALUES (?, ?, ?, ?, ?) AS new
ON DUPLICATE KEY UPDATE ADV = new.ADV,
                        DISADV = new.DISADV,
                        EXPC = new.EXPC
```

7. ② 완성 여부 확인 (빠지면 예외 → 전부 롤백) — `EvaluationRepository.FIND_COMPLETENESS`

```sql
SELECT
  (SELECT COUNT(*)
   FROM TB_EVAL_QSTN q
   WHERE (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
      OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')) AS QSTN_CNT,
  (SELECT COUNT(*)
   FROM TB_EVAL_QSTN_SCORE s
   JOIN TB_EVAL_QSTN q ON q.QSTN_ID = s.QSTN_ID
   WHERE s.EVAL_ID = e.EVAL_ID
     AND ((e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
       OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y'))) AS SCORE_CNT,
  (SELECT COUNT(*) FROM TB_EVAL_ITEM) AS ITEM_CNT,
  (SELECT COUNT(*)
   FROM TB_EVAL_CN c
   WHERE c.EVAL_ID = e.EVAL_ID
     AND TRIM(c.ADV) <> ''
     AND TRIM(c.DISADV) <> ''
     AND TRIM(c.EXPC) <> '') AS CN_CNT
FROM TB_EVAL e
WHERE e.EVAL_ID = ?
```

8. ③ 상태 '제출', 평가일자 오늘 — `EvaluationRepository.SUBMIT_EVALUATION`

```sql
UPDATE TB_EVAL
SET EVAL_STAT = '제출',
    EVAL_DT = CURDATE()
WHERE EVAL_ID = ?
  AND EVAL_STAT = '작성중'
```

9. ④ 그 평가의 메모 삭제 — `EvaluationRepository.DELETE_MEMOS_OF_EVALUATION`

```sql
DELETE FROM TB_EVAL_MEMO
WHERE WRTR_EMP_ID = ?
  AND TRGT_EMP_ID = ?
  AND PRJ_ID = ?
```

### 7. 평가 결과·보정 (보정회차)

- 주소: `GET /adjustments`
- 시연 사용자: 강현준 (경영진, 10-0002)

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 보정회차 목록 — `AdjustmentRepository.FIND_ROUNDS`

```sql
SELECT r.ADJ_YR, r.TNDCY_CALC_PRD, r.MIN_PRJ_CNT, r.MAX_ADJ_WDTH,
       r.EXEC_DT, r.CNFM_DT, r.EXECR_EMP_ID, x.EMP_NM AS EXECR_NM,
       (SELECT COUNT(*)
        FROM TB_EVLTR_ADJ_VAL v
        WHERE v.ADJ_YR = r.ADJ_YR) AS ADJ_VAL_CNT
FROM TB_ADJ_RND r
JOIN TB_EMP x ON x.EMP_ID = r.EXECR_EMP_ID
ORDER BY r.ADJ_YR DESC
```

#### [확정] → 확인 창

**한 트랜잭션** (`AdjustmentService.confirm`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 회차 확인 (있는지, 이미 확정인지, 실행일자) — `AdjustmentRepository.FIND_ROUNDS`

```sql
SELECT r.ADJ_YR, r.TNDCY_CALC_PRD, r.MIN_PRJ_CNT, r.MAX_ADJ_WDTH,
       r.EXEC_DT, r.CNFM_DT, r.EXECR_EMP_ID, x.EMP_NM AS EXECR_NM,
       (SELECT COUNT(*)
        FROM TB_EVLTR_ADJ_VAL v
        WHERE v.ADJ_YR = r.ADJ_YR) AS ADJ_VAL_CNT
FROM TB_ADJ_RND r
JOIN TB_EMP x ON x.EMP_ID = r.EXECR_EMP_ID
ORDER BY r.ADJ_YR DESC
```

2. 확정일자 = 오늘 (미확정일 때만) — `AdjustmentRepository.CONFIRM_ROUND`

```sql
UPDATE TB_ADJ_RND
SET CNFM_DT = CURDATE()
WHERE ADJ_YR = ?
  AND CNFM_DT IS NULL
```

### 8. 평가 결과 조회

- 주소: `GET /adjustments/results?year=&div=`
- 시연 사용자: 강현준

#### [조회] (연도, 평가구분)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 연도 선택 목록 — `AdjustmentRepository.FIND_EVAL_YEARS`

```sql
SELECT DISTINCT YEAR(EVAL_DT) AS EVAL_YR
FROM TB_EVAL
WHERE EVAL_STAT = '제출'
ORDER BY EVAL_YR DESC
```

2. 그 연도 회차의 확정 여부 — `AdjustmentRepository.FIND_ROUNDS`

```sql
SELECT r.ADJ_YR, r.TNDCY_CALC_PRD, r.MIN_PRJ_CNT, r.MAX_ADJ_WDTH,
       r.EXEC_DT, r.CNFM_DT, r.EXECR_EMP_ID, x.EMP_NM AS EXECR_NM,
       (SELECT COUNT(*)
        FROM TB_EVLTR_ADJ_VAL v
        WHERE v.ADJ_YR = r.ADJ_YR) AS ADJ_VAL_CNT
FROM TB_ADJ_RND r
JOIN TB_EMP x ON x.EMP_ID = r.EXECR_EMP_ID
ORDER BY r.ADJ_YR DESC
```

3. ② 평가별 결과 ★ 성능 실험 대상 — `AdjustmentRepository.FIND_EVAL_RESULTS`

```sql
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_DT,
       e.EVLTE_EMP_ID, te.EMP_NM AS EVLTE_EMP_NM,
       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
       COALESCE(re.EMP_NM, cm.MGR_NM) AS EVLTR_NM,
       e.PRJ_ID, p.PRJ_NM,
       ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
            WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
       END AS ADJ_SCORE
FROM TB_EVAL e
JOIN (SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE
      FROM TB_EVAL_QSTN_SCORE
      GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p  ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP te ON te.EMP_ID = e.EVLTE_EMP_ID
LEFT JOIN TB_PM_EVAL pm    ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe  ON pe.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_EMP re        ON re.EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
LEFT JOIN TB_CUST_EVAL ce  ON ce.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_CLIENT_MGR cm ON cm.CLIENT_ID = ce.CLIENT_ID AND cm.MGR_ID = ce.EVAL_MGR_ID
LEFT JOIN TB_ADJ_RND r     ON r.ADJ_YR = YEAR(e.EVAL_DT)
LEFT JOIN TB_EVLTR_ADJ_VAL av
       ON av.ADJ_YR = YEAR(e.EVAL_DT)
      AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
      AND av.EVAL_DIV = e.EVAL_DIV
WHERE e.EVAL_STAT = '제출'
  AND e.EVAL_DT >= ? AND e.EVAL_DT < ?
  AND (? IS NULL OR e.EVAL_DIV = ?)
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID
```

4. ③ 피평가자별 요약 (평가 결과 SQL을 감싼다) — `AdjustmentRepository.FIND_EVALUATEE_SUMMARY`

```sql
SELECT x.EVLTE_EMP_ID, x.EVLTE_EMP_NM,
       COUNT(*) AS EVAL_CNT,
       ROUND(AVG(x.RAW_SCORE), 2) AS RAW_AVG,
       ROUND(AVG(x.ADJ_SCORE), 2) AS ADJ_AVG
FROM (
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_DT,
       e.EVLTE_EMP_ID, te.EMP_NM AS EVLTE_EMP_NM,
       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
       COALESCE(re.EMP_NM, cm.MGR_NM) AS EVLTR_NM,
       e.PRJ_ID, p.PRJ_NM,
       ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
            WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
       END AS ADJ_SCORE
FROM TB_EVAL e
JOIN (SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE
      FROM TB_EVAL_QSTN_SCORE
      GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p  ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP te ON te.EMP_ID = e.EVLTE_EMP_ID
LEFT JOIN TB_PM_EVAL pm    ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe  ON pe.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_EMP re        ON re.EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
LEFT JOIN TB_CUST_EVAL ce  ON ce.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_CLIENT_MGR cm ON cm.CLIENT_ID = ce.CLIENT_ID AND cm.MGR_ID = ce.EVAL_MGR_ID
LEFT JOIN TB_ADJ_RND r     ON r.ADJ_YR = YEAR(e.EVAL_DT)
LEFT JOIN TB_EVLTR_ADJ_VAL av
       ON av.ADJ_YR = YEAR(e.EVAL_DT)
      AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
      AND av.EVAL_DIV = e.EVAL_DIV
WHERE e.EVAL_STAT = '제출'
  AND e.EVAL_DT >= ? AND e.EVAL_DT < ?
  AND (? IS NULL OR e.EVAL_DIV = ?)
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID
) x
GROUP BY x.EVLTE_EMP_ID, x.EVLTE_EMP_NM
ORDER BY x.EVLTE_EMP_NM
```

## 시나리오 2: 교체 요청 → 수용 → 제외 등록 → 배정 후보 목록

### 1. 교체 요청 목록

- 주소: `GET /replacements?stat=`
- 시연 사용자: 이현규 (경영관리팀, 13-0001)

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 교체 요청 목록 (제외 등록 여부 포함) — `ReplacementRepository.FIND_REQUESTS`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.REQ_DT, r.PROC_DT, r.REQ_RSN,
       c.CLIENT_NM, m.MGR_NM,
       th.PRJ_ID, p.PRJ_NM,
       th.EMP_ID AS TRGT_EMP_ID, te.EMP_NM AS TRGT_EMP_NM, tp.ROLE_ID AS TRGT_ROLE_ID,
       sh.EMP_ID AS SUCC_EMP_ID, se.EMP_NM AS SUCC_EMP_NM,
       (SELECT MIN(d.DESIG_ID)
        FROM TB_CLIENT_DESIG_STAFF d
        WHERE d.REPL_REQ_ID = r.REPL_REQ_ID) AS EXCL_DESIG_ID
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c      ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST th   ON th.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_PRJ p         ON p.PRJ_ID = th.PRJ_ID
JOIN TB_EMP te        ON te.EMP_ID = th.EMP_ID
JOIN TB_PRJ_PTCP tp   ON tp.EMP_ID = th.EMP_ID AND tp.PRJ_ID = th.PRJ_ID
LEFT JOIN TB_PRJ_HIST sh ON sh.PRJ_HIST_ID = r.SUCC_HIST_ID
LEFT JOIN TB_EMP se      ON se.EMP_ID = sh.EMP_ID
WHERE (? IS NULL OR r.PROC_STAT = ?)
ORDER BY FIELD(r.PROC_STAT, '접수', '수용', '철회'), r.REQ_DT DESC, r.REPL_REQ_ID DESC
```

#### [철회] → 확인 창

**한 트랜잭션** (`ReplacementService.withdraw`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 요청 확인 (접수 상태인지) — `ReplacementRepository.FIND_REQUEST_DETAIL`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.REQ_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
       h.PRJ_HIST_ID, h.EMP_ID AS TRGT_EMP_ID, e.EMP_NM AS TRGT_EMP_NM, pt.ROLE_ID AS TRGT_ROLE_ID,
       h.STRT_DT AS TRGT_STRT_DT, h.END_DT AS TRGT_END_DT,
       p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c      ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST h    ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_PRJ_PTCP pt   ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
JOIN TB_EMP e         ON e.EMP_ID = h.EMP_ID
JOIN TB_PRJ p         ON p.PRJ_ID = h.PRJ_ID
WHERE r.REPL_REQ_ID = ?
```

2. '철회', 처리일자 오늘 — `ReplacementRepository.WITHDRAW_REQUEST`

```sql
UPDATE TB_STAFF_REPL_REQ
SET PROC_STAT = '철회',
    PROC_DT = ?
WHERE REPL_REQ_ID = ?
  AND PROC_STAT = '접수'
```

### 2. 교체 요청 등록

- 주소: `GET /replacements/new?prjId=`
- 시연 사용자: 이현규

#### [요청 등록] (화면 열기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 진행/지연 프로젝트 — `ReplacementRepository.FIND_ACTIVE_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_ID, c.CLIENT_NM
FROM TB_PRJ p
JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_STAT IN ('진행', '지연')
ORDER BY c.CLIENT_NM, p.PRJ_ID
```

#### ① 프로젝트 선택

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 진행/지연 프로젝트 (고른 프로젝트 확인) — `ReplacementRepository.FIND_ACTIVE_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_ID, c.CLIENT_NM
FROM TB_PRJ p
JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_STAT IN ('진행', '지연')
ORDER BY c.CLIENT_NM, p.PRJ_ID
```

2. ② 교체 대상 후보: 열린 투입 이력 (PM 제외) — `ReplacementRepository.FIND_REPLACEABLE_HISTS`

```sql
SELECT h.PRJ_HIST_ID, h.EMP_ID, e.EMP_NM, pt.ROLE_ID, ro.ROLE_NM, h.STRT_DT,
       (SELECT COUNT(*)
        FROM TB_STAFF_REPL_REQ q
        WHERE q.REPL_TRGT_HIST_ID = h.PRJ_HIST_ID
          AND q.PROC_STAT = '접수') AS OPEN_REQ_CNT
FROM TB_PRJ_HIST h
JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
JOIN TB_EMP e       ON e.EMP_ID = h.EMP_ID
JOIN TB_ROLE ro     ON ro.ROLE_ID = pt.ROLE_ID
WHERE h.PRJ_ID = ?
  AND h.END_DT IS NULL
  AND pt.ROLE_ID <> 'PM'
ORDER BY FIELD(pt.ROLE_ID, 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
```

3. ③ 그 발주처의 담당자 — `ReplacementRepository.FIND_CLIENT_MANAGERS`

```sql
SELECT m.MGR_ID, m.MGR_NM, (m.MGR_ID = p.MGR_ID) AS PRJ_MGR_YN
FROM TB_PRJ p
JOIN TB_CLIENT_MGR m ON m.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_ID = ?
ORDER BY PRJ_MGR_YN DESC, m.MGR_NM
```

#### [접수 등록]

**한 트랜잭션** (`ReplacementService.register`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 대상 이력 (프로젝트 상태, 열림, PM 아님, 투입 시작일) — `ReplacementRepository.FIND_TARGET_HIST`

```sql
SELECT h.PRJ_HIST_ID, h.EMP_ID, h.PRJ_ID, h.STRT_DT, h.END_DT,
       pt.ROLE_ID, p.PRJ_STAT, p.CLIENT_ID
FROM TB_PRJ_HIST h
JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
JOIN TB_PRJ p       ON p.PRJ_ID = h.PRJ_ID
WHERE h.PRJ_HIST_ID = ?
```

2. 담당자가 그 발주처 소속인지 — `ReplacementRepository.COUNT_CLIENT_MANAGER`

```sql
SELECT COUNT(*)
FROM TB_CLIENT_MGR
WHERE CLIENT_ID = ?
  AND MGR_ID = ?
```

3. 같은 이력에 '접수' 요청이 이미 있는지 — `ReplacementRepository.FIND_OPEN_REQUEST_ID`

```sql
SELECT REPL_REQ_ID
FROM TB_STAFF_REPL_REQ
WHERE REPL_TRGT_HIST_ID = ?
  AND PROC_STAT = '접수'
```

4. 교체 요청 등록 ('접수') — `ReplacementRepository.INSERT_REQUEST`

```sql
INSERT INTO TB_STAFF_REPL_REQ (REQ_DT, REQ_RSN, PROC_STAT, REPL_TRGT_HIST_ID, CLIENT_ID, MGR_ID)
VALUES (?, ?, '접수', ?, ?, ?)
```

### 3. 교체 요청 수용

- 주소: `GET /replacements/{id}/accept`
- 시연 사용자: 이현규 (예: 요청 10, 후임 김지원)

#### [수용] (화면 열기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 요청 한 건 — `ReplacementRepository.FIND_REQUEST_DETAIL`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.REQ_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
       h.PRJ_HIST_ID, h.EMP_ID AS TRGT_EMP_ID, e.EMP_NM AS TRGT_EMP_NM, pt.ROLE_ID AS TRGT_ROLE_ID,
       h.STRT_DT AS TRGT_STRT_DT, h.END_DT AS TRGT_END_DT,
       p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c      ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST h    ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_PRJ_PTCP pt   ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
JOIN TB_EMP e         ON e.EMP_ID = h.EMP_ID
JOIN TB_PRJ p         ON p.PRJ_ID = h.PRJ_ID
WHERE r.REPL_REQ_ID = ?
```

2. 후임 후보 (투입 중 아님, 발주처 지명·제외 표시) — `ReplacementRepository.FIND_SUCCESSOR_CANDIDATES`

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
       (SELECT MAX(h2.END_DT)
        FROM TB_PRJ_HIST h2
        WHERE h2.EMP_ID = d.EMP_ID) AS LAST_END_DT,
       EXISTS (SELECT 1
               FROM TB_PRJ_PTCP pt
               WHERE pt.EMP_ID = d.EMP_ID
                 AND pt.PRJ_ID = ?) AS PTCP_YN,
       ds.DESIG_DIV, ds.DESIG_RSN, ds.REPL_REQ_ID AS DESIG_REQ_ID,
       rq.REQ_DT AS DESIG_REQ_DT, mg.MGR_NM AS DESIG_REQ_MGR_NM
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
LEFT JOIN TB_CLIENT_DESIG_STAFF ds
       ON ds.CLIENT_ID = ?
      AND ds.EMP_ID = d.EMP_ID
      AND ds.DESIG_DT <= CURDATE()
      AND (ds.RLS_DT IS NULL OR ds.RLS_DT > CURDATE())
LEFT JOIN TB_STAFF_REPL_REQ rq ON rq.REPL_REQ_ID = ds.REPL_REQ_ID
LEFT JOIN TB_CLIENT_MGR mg     ON mg.CLIENT_ID = rq.CLIENT_ID AND mg.MGR_ID = rq.MGR_ID
WHERE e.EMPL_STAT = '재직'
  AND d.EMP_ID <> ?
  AND NOT EXISTS (SELECT 1
                  FROM TB_PRJ_HIST h
                  WHERE h.EMP_ID = d.EMP_ID
                    AND h.END_DT IS NULL)
ORDER BY CASE ds.DESIG_DIV WHEN '지명' THEN 0 WHEN '제외' THEN 2 ELSE 1 END,
         PTCP_YN DESC, FIELD(d.GRD_ID, 'EX', 'SR', 'MD', 'JR'), e.EMP_NM
```

#### [수용] → 확인 창 → [확인]

**한 트랜잭션** (`ReplacementService.accept`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 요청 확인 (접수, 프로젝트 진행/지연, 대상 이력 열림) — `ReplacementRepository.FIND_REQUEST_DETAIL`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.REQ_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
       h.PRJ_HIST_ID, h.EMP_ID AS TRGT_EMP_ID, e.EMP_NM AS TRGT_EMP_NM, pt.ROLE_ID AS TRGT_ROLE_ID,
       h.STRT_DT AS TRGT_STRT_DT, h.END_DT AS TRGT_END_DT,
       p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c      ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST h    ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_PRJ_PTCP pt   ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
JOIN TB_EMP e         ON e.EMP_ID = h.EMP_ID
JOIN TB_PRJ p         ON p.PRJ_ID = h.PRJ_ID
WHERE r.REPL_REQ_ID = ?
```

2. 후임이 후보 안에 있는지 — `ReplacementRepository.FIND_SUCCESSOR_CANDIDATES` _(후임을 골랐을 때만)_

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
       (SELECT MAX(h2.END_DT)
        FROM TB_PRJ_HIST h2
        WHERE h2.EMP_ID = d.EMP_ID) AS LAST_END_DT,
       EXISTS (SELECT 1
               FROM TB_PRJ_PTCP pt
               WHERE pt.EMP_ID = d.EMP_ID
                 AND pt.PRJ_ID = ?) AS PTCP_YN,
       ds.DESIG_DIV, ds.DESIG_RSN, ds.REPL_REQ_ID AS DESIG_REQ_ID,
       rq.REQ_DT AS DESIG_REQ_DT, mg.MGR_NM AS DESIG_REQ_MGR_NM
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
LEFT JOIN TB_CLIENT_DESIG_STAFF ds
       ON ds.CLIENT_ID = ?
      AND ds.EMP_ID = d.EMP_ID
      AND ds.DESIG_DT <= CURDATE()
      AND (ds.RLS_DT IS NULL OR ds.RLS_DT > CURDATE())
LEFT JOIN TB_STAFF_REPL_REQ rq ON rq.REPL_REQ_ID = ds.REPL_REQ_ID
LEFT JOIN TB_CLIENT_MGR mg     ON mg.CLIENT_ID = rq.CLIENT_ID AND mg.MGR_ID = rq.MGR_ID
WHERE e.EMPL_STAT = '재직'
  AND d.EMP_ID <> ?
  AND NOT EXISTS (SELECT 1
                  FROM TB_PRJ_HIST h
                  WHERE h.EMP_ID = d.EMP_ID
                    AND h.END_DT IS NULL)
ORDER BY CASE ds.DESIG_DIV WHEN '지명' THEN 0 WHEN '제외' THEN 2 ELSE 1 END,
         PTCP_YN DESC, FIELD(d.GRD_ID, 'EX', 'SR', 'MD', 'JR'), e.EMP_NM
```

3. ① 요청 '수용', 처리일자 오늘 — `ReplacementRepository.ACCEPT_REQUEST`

```sql
UPDATE TB_STAFF_REPL_REQ
SET PROC_STAT = '수용',
    PROC_DT = ?
WHERE REPL_REQ_ID = ?
  AND PROC_STAT = '접수'
```

4. ② 대상 이력 종료, 철수사유 CUST — `ReplacementRepository.CLOSE_TARGET_HIST`

```sql
UPDATE TB_PRJ_HIST
SET END_DT = ?,
    WTHDR_RSN_ID = 'CUST'
WHERE PRJ_HIST_ID = ?
  AND END_DT IS NULL
```

5. ③-1 후임 참여가 있는지 — `ReplacementRepository.COUNT_PTCP` _(후임을 골랐을 때만 (③~⑤))_

```sql
SELECT COUNT(*)
FROM TB_PRJ_PTCP
WHERE EMP_ID = ?
  AND PRJ_ID = ?
```

6. ③-2 없으면 대상과 같은 직무로 참여 생성 — `ReplacementRepository.INSERT_PTCP` _(참여가 없을 때만 (재투입이면 건너뜀))_

```sql
INSERT INTO TB_PRJ_PTCP (EMP_ID, PRJ_ID, ROLE_ID)
VALUES (?, ?, ?)
```

7. ④ 후임 이력 (처리일자 다음 날부터) — `ReplacementRepository.INSERT_SUCCESSOR_HIST`

```sql
INSERT INTO TB_PRJ_HIST (EMP_ID, PRJ_ID, STRT_DT)
VALUES (?, ?, ?)
```

8. ⑤ 후임이력ID 연결 — `ReplacementRepository.LINK_SUCCESSOR_HIST`

```sql
UPDATE TB_STAFF_REPL_REQ
SET SUCC_HIST_ID = ?
WHERE REPL_REQ_ID = ?
```

### 4. 제외 등록

- 주소: `GET /designations/exclude?replReqId=`
- 시연 사용자: 이현규 (수용 직후 안내 버튼 또는 목록의 [제외 등록])

#### [제외 등록하러 가기] (화면 열기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 교체 요청 (발주처, 대상 직원) — `DesignationRepository.FIND_REQUEST_FOR_EXCLUSION`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.PROC_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
       h.EMP_ID, e.EMP_NM, h.PRJ_ID, p.PRJ_NM
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c     ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST h   ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_EMP e        ON e.EMP_ID = h.EMP_ID
JOIN TB_PRJ p        ON p.PRJ_ID = h.PRJ_ID
WHERE r.REPL_REQ_ID = ?
```

2. 이 요청으로 이미 등록한 제외 — `DesignationRepository.FIND_DESIG_BY_REQUEST`

```sql
SELECT DESIG_ID
FROM TB_CLIENT_DESIG_STAFF
WHERE REPL_REQ_ID = ?
```

#### [제외 등록]

**한 트랜잭션** (`DesignationService.registerExclusion`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 요청이 '수용'인지 — `DesignationRepository.FIND_REQUEST_FOR_EXCLUSION`

```sql
SELECT r.REPL_REQ_ID, r.PROC_STAT, r.PROC_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
       h.EMP_ID, e.EMP_NM, h.PRJ_ID, p.PRJ_NM
FROM TB_STAFF_REPL_REQ r
JOIN TB_CLIENT c     ON c.CLIENT_ID = r.CLIENT_ID
JOIN TB_CLIENT_MGR m ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
JOIN TB_PRJ_HIST h   ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
JOIN TB_EMP e        ON e.EMP_ID = h.EMP_ID
JOIN TB_PRJ p        ON p.PRJ_ID = h.PRJ_ID
WHERE r.REPL_REQ_ID = ?
```

2. 이 요청으로 이미 등록했는지 — `DesignationRepository.FIND_DESIG_BY_REQUEST`

```sql
SELECT DESIG_ID
FROM TB_CLIENT_DESIG_STAFF
WHERE REPL_REQ_ID = ?
```

3. 같은 발주처·직원의 유효 기간이 겹치는 지정 — `DesignationRepository.FIND_OVERLAPS`

```sql
SELECT DESIG_ID, DESIG_DIV, DESIG_DT, RLS_DT
FROM TB_CLIENT_DESIG_STAFF
WHERE CLIENT_ID = ?
  AND EMP_ID = ?
  AND (RLS_DT IS NULL OR RLS_DT > ?)
```

4. '제외' 등록 (지정일자 오늘, 근거 요청ID) — `DesignationRepository.INSERT_DESIGNATION`

```sql
INSERT INTO TB_CLIENT_DESIG_STAFF (DESIG_DIV, DESIG_RSN, DESIG_DT, CLIENT_ID, EMP_ID, REPL_REQ_ID)
VALUES (?, ?, CURDATE(), ?, ?, ?)
```

### 5. 발주처 지정인력

- 주소: `GET /designations?clientId=`
- 시연 사용자: 이현규

#### 화면 열기 (발주처 거르기)

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 발주처 목록 — `DesignationRepository.FIND_CLIENTS`

```sql
SELECT CLIENT_ID, CLIENT_NM
FROM TB_CLIENT
ORDER BY CLIENT_NM
```

2. 지정인력 목록 (유효 여부 계산) — `DesignationRepository.FIND_DESIGNATIONS`

```sql
SELECT d.DESIG_ID, d.DESIG_DIV, d.DESIG_RSN, d.DESIG_DT, d.RLS_DT,
       d.CLIENT_ID, c.CLIENT_NM, d.EMP_ID, e.EMP_NM,
       d.REPL_REQ_ID, m.MGR_NM AS REQ_MGR_NM,
       (d.DESIG_DT <= CURDATE() AND (d.RLS_DT IS NULL OR d.RLS_DT > CURDATE())) AS VALID_YN
FROM TB_CLIENT_DESIG_STAFF d
JOIN TB_CLIENT c ON c.CLIENT_ID = d.CLIENT_ID
JOIN TB_EMP e    ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_STAFF_REPL_REQ r ON r.REPL_REQ_ID = d.REPL_REQ_ID
LEFT JOIN TB_CLIENT_MGR m     ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
WHERE (? IS NULL OR d.CLIENT_ID = ?)
ORDER BY c.CLIENT_NM, VALID_YN DESC, d.DESIG_DT DESC, d.DESIG_ID DESC
```

3. 지명 대상: 재직 개발직원 — `DesignationRepository.FIND_DEV_EMPLOYEES`

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
WHERE e.EMPL_STAT = '재직'
ORDER BY e.EMP_NM
```

#### [지명 등록]

**한 트랜잭션** (`DesignationService.registerNomination`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 발주처 확인 — `DesignationRepository.FIND_CLIENTS`

```sql
SELECT CLIENT_ID, CLIENT_NM
FROM TB_CLIENT
ORDER BY CLIENT_NM
```

2. 재직 개발직원인지 — `DesignationRepository.FIND_DEV_EMPLOYEES`

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
WHERE e.EMPL_STAT = '재직'
ORDER BY e.EMP_NM
```

3. 유효 기간이 겹치는 지정 — `DesignationRepository.FIND_OVERLAPS`

```sql
SELECT DESIG_ID, DESIG_DIV, DESIG_DT, RLS_DT
FROM TB_CLIENT_DESIG_STAFF
WHERE CLIENT_ID = ?
  AND EMP_ID = ?
  AND (RLS_DT IS NULL OR RLS_DT > ?)
```

4. '지명' 등록 (요청ID 없음) — `DesignationRepository.INSERT_DESIGNATION`

```sql
INSERT INTO TB_CLIENT_DESIG_STAFF (DESIG_DIV, DESIG_RSN, DESIG_DT, CLIENT_ID, EMP_ID, REPL_REQ_ID)
VALUES (?, ?, CURDATE(), ?, ?, ?)
```

#### [해제] → 확인 창

**한 트랜잭션** (`DesignationService.release`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 해제일자 = 오늘 (행은 지우지 않음) — `DesignationRepository.RELEASE_DESIGNATION`

```sql
UPDATE TB_CLIENT_DESIG_STAFF
SET RLS_DT = CURDATE()
WHERE DESIG_ID = ?
  AND (RLS_DT IS NULL OR RLS_DT > CURDATE())
```

### 6. 인력 배정

- 주소: `GET /staffing?prjId=`
- 시연 사용자: 이현규 (예: P26-011, 조민경)

#### 화면 열기

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 진행/지연 프로젝트 — `DesignationRepository.FIND_STAFFING_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, p.SCHD_END_DT, p.CLIENT_ID, c.CLIENT_NM
FROM TB_PRJ p
JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_STAT IN ('진행', '지연')
ORDER BY c.CLIENT_NM, p.PRJ_ID
```

#### ① 프로젝트 선택

트랜잭션 없음 (조회만, SQL마다 자동 커밋)

1. 고른 프로젝트 확인 — `DesignationRepository.FIND_STAFFING_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, p.SCHD_END_DT, p.CLIENT_ID, c.CLIENT_NM
FROM TB_PRJ p
JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_STAT IN ('진행', '지연')
ORDER BY c.CLIENT_NM, p.PRJ_ID
```

2. ② 배정 후보 목록 ★ 보고서용 (지명·제외와 근거 요청 포함) — `DesignationRepository.FIND_CANDIDATES`

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
       (SELECT GROUP_CONCAT(s.SKILL_NM ORDER BY s.SKILL_NM SEPARATOR ', ')
        FROM TB_HOLD_SKILL hs
        JOIN TB_SKILL s ON s.SKILL_ID = hs.SKILL_ID
        WHERE hs.EMP_ID = d.EMP_ID) AS SKILLS,
       ds.DESIG_ID, ds.DESIG_DIV, ds.DESIG_DT, ds.DESIG_RSN,
       ds.REPL_REQ_ID, rq.REQ_DT, rq.REQ_RSN, mg.MGR_NM AS REQ_MGR_NM,
       pt.ROLE_ID AS PTCP_ROLE_ID
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
JOIN TB_PRJ p ON p.PRJ_ID = ?
LEFT JOIN TB_CLIENT_DESIG_STAFF ds
       ON ds.CLIENT_ID = p.CLIENT_ID
      AND ds.EMP_ID = d.EMP_ID
      AND ds.DESIG_DT <= CURDATE()
      AND (ds.RLS_DT IS NULL OR ds.RLS_DT > CURDATE())
LEFT JOIN TB_STAFF_REPL_REQ rq ON rq.REPL_REQ_ID = ds.REPL_REQ_ID
LEFT JOIN TB_CLIENT_MGR mg     ON mg.CLIENT_ID = rq.CLIENT_ID AND mg.MGR_ID = rq.MGR_ID
LEFT JOIN TB_PRJ_PTCP pt       ON pt.EMP_ID = d.EMP_ID AND pt.PRJ_ID = p.PRJ_ID
WHERE e.EMPL_STAT = '재직'
  AND NOT EXISTS (SELECT 1
                  FROM TB_PRJ_HIST h
                  WHERE h.EMP_ID = d.EMP_ID
                    AND h.END_DT IS NULL)
ORDER BY CASE ds.DESIG_DIV WHEN '지명' THEN 0 WHEN '제외' THEN 2 ELSE 1 END,
         FIELD(d.GRD_ID, 'EX', 'SR', 'MD', 'JR'), e.EMP_NM
```

3. 직무 목록 — `DesignationRepository.FIND_ROLES`

```sql
SELECT ROLE_ID, ROLE_NM
FROM TB_ROLE
ORDER BY FIELD(ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS')
```

4. PM이 있는지 (있으면 PM 선택 막음) — `DesignationRepository.COUNT_PM`

```sql
SELECT COUNT(*)
FROM TB_PRJ_PTCP
WHERE PRJ_ID = ?
  AND ROLE_ID = 'PM'
```

#### [배정] (제외 인력이면 확인 창)

**한 트랜잭션** (`DesignationService.assign`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. 중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.

1. 프로젝트가 진행/지연인지, 시작일 — `DesignationRepository.FIND_STAFFING_PROJECTS`

```sql
SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, p.SCHD_END_DT, p.CLIENT_ID, c.CLIENT_NM
FROM TB_PRJ p
JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
WHERE p.PRJ_STAT IN ('진행', '지연')
ORDER BY c.CLIENT_NM, p.PRJ_ID
```

2. 직원이 후보 안에 있는지 (제외 여부) — `DesignationRepository.FIND_CANDIDATES`

```sql
SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
       (SELECT GROUP_CONCAT(s.SKILL_NM ORDER BY s.SKILL_NM SEPARATOR ', ')
        FROM TB_HOLD_SKILL hs
        JOIN TB_SKILL s ON s.SKILL_ID = hs.SKILL_ID
        WHERE hs.EMP_ID = d.EMP_ID) AS SKILLS,
       ds.DESIG_ID, ds.DESIG_DIV, ds.DESIG_DT, ds.DESIG_RSN,
       ds.REPL_REQ_ID, rq.REQ_DT, rq.REQ_RSN, mg.MGR_NM AS REQ_MGR_NM,
       pt.ROLE_ID AS PTCP_ROLE_ID
FROM TB_DEV_EMP d
JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
JOIN TB_PRJ p ON p.PRJ_ID = ?
LEFT JOIN TB_CLIENT_DESIG_STAFF ds
       ON ds.CLIENT_ID = p.CLIENT_ID
      AND ds.EMP_ID = d.EMP_ID
      AND ds.DESIG_DT <= CURDATE()
      AND (ds.RLS_DT IS NULL OR ds.RLS_DT > CURDATE())
LEFT JOIN TB_STAFF_REPL_REQ rq ON rq.REPL_REQ_ID = ds.REPL_REQ_ID
LEFT JOIN TB_CLIENT_MGR mg     ON mg.CLIENT_ID = rq.CLIENT_ID AND mg.MGR_ID = rq.MGR_ID
LEFT JOIN TB_PRJ_PTCP pt       ON pt.EMP_ID = d.EMP_ID AND pt.PRJ_ID = p.PRJ_ID
WHERE e.EMPL_STAT = '재직'
  AND NOT EXISTS (SELECT 1
                  FROM TB_PRJ_HIST h
                  WHERE h.EMP_ID = d.EMP_ID
                    AND h.END_DT IS NULL)
ORDER BY CASE ds.DESIG_DIV WHEN '지명' THEN 0 WHEN '제외' THEN 2 ELSE 1 END,
         FIELD(d.GRD_ID, 'EX', 'SR', 'MD', 'JR'), e.EMP_NM
```

3. 투입 기간이 겹치는 이력 — `DesignationRepository.FIND_LATEST_OVERLAP_END`

```sql
SELECT MAX(COALESCE(END_DT, DATE '9999-12-31'))
FROM TB_PRJ_HIST
WHERE EMP_ID = ?
  AND (END_DT IS NULL OR END_DT >= ?)
```

4. 이 프로젝트 참여가 있는지 (재투입이면 기존 직무) — `DesignationRepository.FIND_PTCP_ROLE`

```sql
SELECT ROLE_ID
FROM TB_PRJ_PTCP
WHERE EMP_ID = ?
  AND PRJ_ID = ?
```

5. 직무가 있는 값인지 — `DesignationRepository.FIND_ROLES` _(참여가 없을 때만)_

```sql
SELECT ROLE_ID, ROLE_NM
FROM TB_ROLE
ORDER BY FIELD(ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS')
```

6. PM 중복인지 — `DesignationRepository.COUNT_PM` _(참여가 없고 PM으로 배정할 때만)_

```sql
SELECT COUNT(*)
FROM TB_PRJ_PTCP
WHERE PRJ_ID = ?
  AND ROLE_ID = 'PM'
```

7. 참여 생성 — `DesignationRepository.INSERT_PTCP` _(참여가 없을 때만)_

```sql
INSERT INTO TB_PRJ_PTCP (EMP_ID, PRJ_ID, ROLE_ID)
VALUES (?, ?, ?)
```

8. 투입 이력 생성 — `DesignationRepository.INSERT_HIST`

```sql
INSERT INTO TB_PRJ_HIST (EMP_ID, PRJ_ID, STRT_DT)
VALUES (?, ?, ?)
```

## 부록: 한 트랜잭션으로 묶인 버튼 요약

| 시나리오 | 화면 | 버튼 | Service 메서드 | 바뀌는 테이블 |
| --- | --- | --- | --- | --- |
| 1 | 대상별 메모 / 평가메모 | [저장] [수정] [삭제] | MemoService.create / update / delete | TB_EVAL_MEMO |
| 1 | 평가 화면 | [임시 저장] | EvaluationService.saveDraft | TB_EVAL_QSTN_SCORE, TB_EVAL_CN |
| 1 | 평가 화면 | [제출] | EvaluationService.submit | TB_EVAL_QSTN_SCORE, TB_EVAL_CN, TB_EVAL, TB_EVAL_MEMO |
| 1 | 평가 결과·보정 | [확정] | AdjustmentService.confirm | TB_ADJ_RND |
| 2 | 교체 요청 등록 | [접수 등록] | ReplacementService.register | TB_STAFF_REPL_REQ |
| 2 | 교체 요청 목록 | [철회] | ReplacementService.withdraw | TB_STAFF_REPL_REQ |
| 2 | 교체 요청 수용 | [수용] | ReplacementService.accept | TB_STAFF_REPL_REQ, TB_PRJ_HIST, TB_PRJ_PTCP |
| 2 | 제외 등록 | [제외 등록] | DesignationService.registerExclusion | TB_CLIENT_DESIG_STAFF |
| 2 | 발주처 지정인력 | [지명 등록] [해제] | DesignationService.registerNomination / release | TB_CLIENT_DESIG_STAFF |
| 2 | 인력 배정 | [배정] | DesignationService.assign | TB_PRJ_PTCP, TB_PRJ_HIST |

- 여러 테이블을 함께 바꾸는 버튼은 **평가 [제출]**(4개 테이블), **교체 요청 [수용]**(3개 테이블), **인력 배정 [배정]**(2개 테이블)이다.
- 교체 요청 [수용]의 롤백은 `src/test/.../replacement/ReplacementRollbackTest`가 확인한다. ④ 후임 이력 생성에서 일부러 예외를 내면 이미 실행한 ①(요청 상태)과 ②(대상 이력 종료)까지 되돌아간다.
