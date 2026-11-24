# 성능 실험: 평가 결과 조회 SQL

실험 대상은 보정점수 화면의 **평가 결과 조회 SQL**(`AdjustmentRepository.FIND_EVAL_RESULTS`)이다.
하드웨어가 아니라 SQL과 DB 설계(인덱스, 반정규화, SQL 재작성)로 얼마나 빨라지는지 비교한다.
이 폴더의 SQL은 **데모 DB(`docker/init`)와 앱 코드에는 적용하지 않는다.** 적용 SQL과 되돌리는 SQL을 짝으로 둔다.

## 파일

| 파일 | 내용 | 되돌리기 |
| --- | --- | --- |
| `10_scale_up.sql` | 제출된 PM·동료 평가 1,254건을 80번 복제해 평가 약 10만 건으로 늘린다 | `11_scale_down.sql` |
| `20_query_baseline.sql` | 기준: 앱 SQL 그대로 EXPLAIN ANALYZE | – |
| `30_index_apply.sql` | 후보 A: `TB_EVAL (EVAL_STAT, EVAL_DT, EVAL_DIV)` 인덱스 | `31_index_rollback.sql` |
| `40_denorm_apply.sql` | 후보 B: `TB_EVAL.RAW_SCORE`(원점수) 열 추가와 채우기 | `41_denorm_rollback.sql` |
| `42_query_denorm.sql` | 후보 B를 쓰는 SQL 측정 + 기준 SQL과 결과 비교 | – |
| `50_query_lateral.sql` | 후보 C: 파생 테이블을 LATERAL 조인으로 바꾼 SQL 측정 + 결과 비교 | – |

## 실행 방법

프로젝트 폴더에서 PowerShell로 실행한다.

```powershell
cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\10_scale_up.sql"
```

- 파일 이름만 바꿔 같은 방식으로 실행한다.
- **`Get-Content 파일 | docker exec ...`는 쓰지 않는다.** Windows PowerShell 5.1은 파이프로 넘기는 글자를 다른 인코딩으로 바꿔서 `'제출'`, `'동료'` 같은 한글 조건이 깨진다. 그러면 오류 없이 0건이 처리된다.
- Git Bash에서는 `docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker/experiment/파일.sql`로 실행한다.

### 실험 순서

1. `10_scale_up.sql` (평가 약 10만 건, 약 7초)
2. `20_query_baseline.sql`, `50_query_lateral.sql` (인덱스 없음)
3. `40_denorm_apply.sql` → `42_query_denorm.sql`
4. `30_index_apply.sql` → `20`, `50`, `42` 다시 측정
5. 되돌리기: `31_index_rollback.sql` → `41_denorm_rollback.sql` → `11_scale_down.sql`

전부 처음 상태로 돌리려면 `docker` 폴더에서 `docker compose down -v` 후 `docker compose up -d`를 해도 된다.

## EXPLAIN ANALYZE 읽는 법

`EXPLAIN ANALYZE`는 SQL을 **실제로 실행**하고, 실행 계획의 각 단계에 걸린 시간과 처리한 행 수를 보여 준다.

```
-> Sort: te.EMP_NM, e.EVAL_DT, e.EVAL_ID  (cost=... rows=...) (actual time=328..331 rows=27540 loops=1)
    -> Nested loop left join  ... (actual time=0.9..290 rows=27540 loops=1)
        -> Table scan on e  (cost=9310 rows=95897) (actual time=0.179..20.9 rows=101888 loops=1)
        -> Index lookup on s using PRIMARY (EVAL_ID=e.EVAL_ID)  (actual time=0.0013..0.0017 rows=7 loops=27540)
```

| 항목 | 뜻 |
| --- | --- |
| 맨 윗줄 `actual time=A..B` | B가 **전체 실행 시간(ms)**. 결과표에는 이 값을 적는다 |
| `rows=` | 그 단계가 실제로 내보낸 행 수 (`cost=` 옆의 rows는 옵티마이저 **추정치**) |
| `loops=` | 그 단계를 반복한 횟수. 한 번에 걸린 시간 × loops가 그 단계의 총 시간 |
| `Table scan on 테이블` | 테이블 전체를 읽음 |
| `Index lookup / Index range scan ... using 인덱스` | 인덱스로 찾음 |
| `Materialize` + `Table scan on <derived>` | 파생 테이블을 먼저 전부 만들어 놓고 읽음 |
| `Aggregate using temporary table` | GROUP BY를 임시 테이블로 집계 |

- 같은 파일을 3번 실행해 중간값을 쓴다. 첫 실행은 디스크에서 읽어 느릴 수 있다.
- 결과만 보고 싶으면 `EXPLAIN ANALYZE`를 지우고 실행한다. mysql 클라이언트가 `27540 rows in set (0.41 sec)`처럼 시간을 함께 보여 준다.

## 측정 조건

- MySQL 8.4.8 (Docker, `innodb_buffer_pool_size` 512MB), 캐시에 올라온 상태에서 3회 측정한 중간값
- 데이터: 평가 101,888건(PM 19,365 / 동료 82,265 / 고객 258), 문항별 점수 751,454행
- 조건 ① 2025년 동료 평가(결과 27,540행), ② 2025년 PM 평가(결과 5,994행)

## 결과 (2026-11-24 측정)

| 후보 | 인덱스 | ① 2025 동료 | ② 2025 PM | 기준 대비 (①/②) |
| --- | --- | --- | --- | --- |
| 기준 SQL | 없음 | 416 ms | 251 ms | – |
| A. 인덱스 | 있음 | 410 ms | 266 ms | 변화 없음 |
| C. LATERAL 재작성 | 없음 | 333 ms | 117 ms | −20% / −53% |
| C. LATERAL 재작성 | 있음 | 332 ms | 118 ms | −20% / −53% |
| B. 반정규화 | 없음 | 223 ms | 92 ms | **−46% / −63%** |
| B. 반정규화 | 있음 | 226 ms | 92 ms | −46% / −63% |

- **결과가 같은지 확인:** 모든 연도와 평가구분(101,832행)에서 B와 C의 결과를 기준 SQL과 비교했다(`42`, `50` 파일의 ③). 빠진 행 0, 값이 다른 행 0이었다.

## 해석

### 기준 SQL이 느린 이유

파생 테이블 `(SELECT EVAL_ID, AVG(SCORE) FROM TB_EVAL_QSTN_SCORE GROUP BY EVAL_ID)`가 **조회 조건과 관계없이 문항별 점수 75만 행 전체를 먼저 집계**한다.
2025년 PM 평가 6천 건만 필요해도 10만 건 평가의 점수를 모두 평균 낸 뒤 조인한다.

### A. 인덱스만으로는 빨라지지 않았다

- 인덱스 자체는 동작한다. 같은 조건의 단순 `COUNT(*)`에서는 `Covering index range scan using idx_eval_stat_dt_div`를 쓴다.
- 그런데 평가 결과 조회에서는 옵티마이저가 **일부러 쓰지 않고 `Table scan on e`를 고른다.** 한 해의 제출 평가가 전체의 약 3분의 1(3만 3천 건)이라, 인덱스로 찾은 뒤 행마다 본문을 다시 읽는 것보다 전체를 한 번 읽는 편이 싸다고 판단하기 때문이다.
- 또 시간의 대부분은 평가를 고르는 단계(약 20ms)가 아니라 **점수 전체 집계와 수만 행의 조인, 정렬**에서 쓰인다. 평가를 고르는 단계를 빠르게 해도 전체 시간은 거의 줄지 않는다.
- 연도 수가 많아져 한 해가 전체의 작은 비율이 되면 이 인덱스가 쓰이기 시작한다. 지금 데이터는 원본처럼 2023~2026년 4년에 몰려 있다.

### C. SQL 재작성 (구조 변경 없음)

- `JOIN LATERAL (SELECT AVG(s.SCORE) FROM TB_EVAL_QSTN_SCORE s WHERE s.EVAL_ID = e.EVAL_ID)`로 바꾸면 **고른 평가마다 그 평가의 점수만** PK(`EVAL_ID, QSTN_ID`)로 찾아 평균을 낸다(`Index lookup on s using PRIMARY`).
- 결과가 적을수록 효과가 크다. PM(6천 건)은 53%, 동료(2만 7천 건)는 20% 줄었다.
- 테이블을 바꾸지 않으므로 비용이 없다. 앱 SQL을 이렇게 바꾸는 것만으로 적용할 수 있다.

### B. 반정규화 (가장 빠름)

- 평가에 원점수(`RAW_SCORE`)를 저장해 두면 점수 테이블을 아예 읽지 않는다. 46~63% 줄어 가장 빠르다.
- 남은 시간은 수만 행의 조인과 이름순 정렬이다.
- **비용:**
  - 같은 값이 점수 테이블과 평가 테이블 두 곳에 있게 된다.
  - 이 DB에는 트리거가 없으므로 평가 [제출](`EvaluationService.submit`)이 `RAW_SCORE`도 함께 저장해야 한다.
  - 점수를 고치는 다른 경로가 생기면 값이 어긋날 수 있다.
  - 다만 "제출된 평가의 점수는 고칠 수 없다"는 업무 규칙이 있어서, 제출 시점에 한 번만 계산하면 이후에 어긋날 일은 거의 없다.

### 정리

| 후보 | 빨라진 정도 | 바꾸는 것 | 비용·위험 |
| --- | --- | --- | --- |
| A. 인덱스 | 없음 (이 데이터 분포에서는) | 인덱스 1개 | 쓰기 때마다 인덱스 갱신, 저장 공간 |
| C. SQL 재작성 | 20~53% | SQL만 | 없음 |
| B. 반정규화 | 46~63% | 열 1개 + 제출 처리 수정 | 중복 데이터, 동기화 책임 |

- 결과를 많이 돌려주는 조회일수록 B가 유리하고, 적게 돌려주는 조회에서는 C도 B에 가깝다.
- 원점수는 제출 후 바뀌지 않는 값이라 반정규화의 동기화 위험이 작다. 그래서 B가 이 SQL에 잘 맞는 설계 변경이다.

## 주의

- `10_scale_up.sql`은 새 평가ID를 `회차 × 10000 + 원래 ID`로 직접 넣는다. 그래서 `TB_EVAL`의 자동 증가 값이 약 80만으로 올라간다. `11_scale_down.sql`이 원래대로(1569) 되돌린다.
- 복제한 평가는 원본과 평가자, 피평가자, 프로젝트, 평가일자가 같다. 따라서 같은 사람에게 같은 프로젝트의 평가가 여러 건 생긴다. 성능 측정용 데이터이므로 데모에는 쓰지 않는다.
