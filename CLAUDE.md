# si-company 데모 앱

SI 회사의 프로젝트 인력 배치와 평가를 관리하는 DB 설계 수업 팀 프로젝트다.
화면은 실제 사내 업무 시스템처럼 권한별 메뉴로 구성한다. **먼저 데모 시나리오 2개에 쓰는 기능을 만들고**, 그다음에 나머지 기능(고도화)을 하나씩 넓혀 간다. 지금은 모든 메뉴를 만들었다. 새 메뉴를 먼저 "준비 중"으로 두려면 이름이 `PlaceholderController`인 컨트롤러에 그 주소를 매핑한다 (`common/MenuStatus.java`가 메뉴에 표시한다).
**DB 설계 프로젝트이지 웹 프로젝트가 아니다.** 화면 디자인은 평가하지 않으니 꾸미는 데 시간을 쓰지 않는다. 잘 돌아가고 어떤 SQL이 실행됐는지 보이는 것이 중요하다.

## 기술 스택과 규칙

- Spring Boot, Java 21, Gradle, Thymeleaf, Lombok
- DB 접근은 **JdbcTemplate만** 쓴다. JPA, MyBatis, Spring Data는 쓰지 않는다.
- SQL은 Repository 클래스 안에 Java 텍스트 블록(`"""`)으로 적는다. 보고서에 SQL을 그대로 옮길 수 있게 읽기 좋게 줄을 나눈다.
- 여러 테이블을 바꾸는 처리는 Service 메서드에 `@Transactional`을 붙여 한 트랜잭션으로 묶는다.
- Spring Security는 쓰지 않는다. 로그인 대신 화면 위쪽에서 "현재 사용자"를 고르고 세션에 직원ID를 저장한다.
- 화면은 Thymeleaf와 Bootstrap CDN으로 단순하게 만든다. 공통 헤더는 fragment로 둔다.
- 규칙을 어기는 요청은 예외를 던지고 화면에 한국어 오류 메시지를 보여 준다.
- **화면마다 그 화면이 실행한 주요 SQL을 보여 준다.** 페이지 아래쪽에 접었다 펼 수 있는 영역을 두고 SQL 원문을 표시한다. 발표 때 "이 결과가 어떤 쿼리에서 나왔는지"를 바로 보여 주기 위해서다. SQL은 Repository의 상수를 그대로 가져다 써서 실제 실행한 SQL과 다르지 않게 한다.

## DB

- MySQL 8.4.8을 Docker로 띄운다. 설정은 `docker/docker-compose.yml`, 접속 포트는 **3307**, DB 이름은 `si_company`다.
- 스키마와 데이터는 `docker/init/01_schema.sql`, `02_codes.sql`, `03_dummy.sql`이다. **이 파일들과 테이블 구조는 고치지 않는다.** 테이블이나 열이 더 필요해 보이면 먼저 물어본다.
- 트리거는 DB에 없다. 아래 업무 규칙은 Service 코드에서 지킨다.
- 데이터를 처음 상태로 되돌리려면 `docker` 폴더에서 `docker compose down -v` 후 `docker compose up -d`.

## 메뉴 (common/Menus.java)

현재 사용자에게 맞는 메뉴 그룹만 헤더와 홈에 보인다. 주소를 직접 쳐도 대상이 아니면 오류 화면으로 간다.

| 그룹 (대상) | 메뉴와 주소 |
| --- | --- |
| 공통 (모든 직원) | 내 정보 `/me`, 경력증명서 신청 `/certificates` |
| 내 업무 (개발직원, EMP_DIV='개발') | 내 프로젝트 `/my/projects`, 평가메모 `/memos` ★, 내 평가 `/evaluations` ★, 고객평가 입력 `/evaluations/customer` |
| 인력 관리 (경영관리팀 MGT) | 교체 요청 `/replacements` ★, 발주처 지정인력 `/designations` ★, 인력 배정 `/staffing` ★, 프로젝트 관리 `/projects`, 합격자 명부 `/passers`, 경력증명서 발급 `/certificates/issue` |
| 경영 현황 (경영진 EXE) | 평가 결과·보정 `/adjustments` ★, 프로젝트 현황 `/dashboard`, 직원 검색 `/employees`, 보정 실행 `/adjustments/run`, 인센티브 자료 `/incentives` |
| 로그인 전 | 회원가입 `/signup` |

★는 데모 시나리오에 쓰는 메뉴다. 메뉴에 없는 화면: 특정 시점 참여 조회 `/employees/as-of`(경영진), 경력증명서 보기 `/certificates/{id}`.

## 패키지 구조 (기본 패키지 아래)

```
common/        현재 사용자 세션, 공통 예외 처리, 레이아웃
memo/          평가메모 (시나리오 1)
evaluation/    평가 작성과 제출 (시나리오 1)
adjustment/    점수 보정 조회와 확정 (시나리오 1)
replacement/   교체 요청과 처리 (시나리오 2)
designation/   발주처 지정인력과 배정 후보 (시나리오 2)
project/       프로젝트 등록·수정·종료·중단, 투입 기간 정정·철수
dashboard/     프로젝트 현황, 인센티브 기초자료 (조회만)
employee/      직원 검색, 특정 시점 참여 조회, 내 정보
certificate/   경력증명서 신청·발급
signup/        합격자 명부, 회원가입
```
기능 폴더마다 Controller, Service, Repository, DTO를 둔다.

## 사람과 권한

- 직원: `TB_EMP` (EMP_ID, EMP_NM, AUTH_ID, EMPL_STAT). 재직상태 '퇴사'인 직원은 현재 사용자로 고를 수 없다.
- 권한 `AUTH_ID`: 'EMP' 일반직원, 'MGT' 경영관리팀, 'EXE' 경영진
- 개발직원만 프로젝트에 투입된다: `TB_DEV_EMP`
- 프로젝트 안의 직무는 `TB_PRJ_PTCP.ROLE_ID` ('PM', 'PL', 'AN', 'DS', 'PG', 'TS'). 프로젝트당 PM은 1명이고 직무는 바꿀 수 없다.

## 시나리오에서 쓰는 테이블

| 테이블 | 열 |
| --- | --- |
| TB_PRJ 프로젝트 | PRJ_ID, PRJ_NM, STRT_DT, SCHD_END_DT, END_DT, PRJ_STAT('진행','지연','종료','중단'), CLIENT_ID, MGR_ID |
| TB_PRJ_PTCP 프로젝트참여 | PK(EMP_ID, PRJ_ID), ROLE_ID |
| TB_PRJ_HIST 투입 이력 | PRJ_HIST_ID(자동), EMP_ID, PRJ_ID, STRT_DT, END_DT, WTHDR_RSN_ID |
| TB_WTHDR_RSN 철수사유 | 'PLAN' 계획된 종료, 'CUST' 고객 교체 요청, 'MOVE' 차출, 'SELF' 본인 사유, 'RET' 퇴사, 'STOP' 중단 |
| TB_EVAL_MEMO 평가메모 | MEMO_ID(자동), MEMO_CN, WRT_DTTM, MOD_DTTM, WRTR_EMP_ID, TRGT_EMP_ID, PRJ_ID |
| TB_EVAL 평가 | EVAL_ID(자동), EVAL_DIV('PM','동료','고객'), EVAL_STAT('작성중','제출'), EVAL_DT, EVLTE_EMP_ID(피평가자), PRJ_ID |
| TB_PM_EVAL / TB_PEER_EVAL | EVAL_ID, EVLTR_EMP_ID(평가자), PRJ_ID |
| TB_EVAL_QSTN 평가문항 | QSTN_ID, QSTN_CN, PM_USE_YN, PEER_USE_YN, CUST_USE_YN, EVAL_ITEM_ID('WORK','COMM') |
| TB_EVAL_QSTN_SCORE 문항별 점수 | PK(EVAL_ID, QSTN_ID), SCORE(0~10) |
| TB_EVAL_CN 평가내용 | PK(EVAL_ID, EVAL_ITEM_ID), ADV 장점, DISADV 단점, EXPC 바라는 점 |
| TB_ADJ_RND 보정회차 | ADJ_YR, TNDCY_CALC_PRD, MIN_PRJ_CNT, MAX_ADJ_WDTH, EXEC_DT, CNFM_DT(확정일자), EXECR_EMP_ID |
| TB_EVLTR_ADJ_VAL 평가자보정값 | PK(ADJ_YR, EVLTR_EMP_ID, EVAL_DIV), EVAL_CNT, EVAL_PRJ_CNT, EVLTR_AVG_SCORE, APLY_ADJ_VAL |
| TB_CLIENT / TB_CLIENT_MGR | 발주처(CLIENT_ID, CLIENT_NM), 담당자 PK(CLIENT_ID, MGR_ID), MGR_NM |
| TB_STAFF_REPL_REQ 교체 요청 | REPL_REQ_ID(자동), REQ_DT, REQ_RSN, PROC_STAT('접수','수용','철회'), PROC_DT, REPL_TRGT_HIST_ID(대상 이력), SUCC_HIST_ID(후임 이력), CLIENT_ID, MGR_ID |
| TB_CLIENT_DESIG_STAFF 발주처지정인력 | DESIG_ID(자동), DESIG_DIV('지명','제외'), DESIG_RSN, DESIG_DT, RLS_DT(해제일자), CLIENT_ID, EMP_ID, REPL_REQ_ID |

정확한 자료형과 제약은 `docker/init/01_schema.sql`을 본다.

## 시나리오 1: 평가메모 → 평가 제출 → 보정점수 조회

**평가메모**
- 진행 또는 지연 중인 프로젝트에서만 쓴다. '중단' 프로젝트에는 쓸 수 없다.
- 작성 대상: PM은 팀원 전원, PM이 아닌 사람은 투입 기간이 하루 이상 겹친 같은 프로젝트 직원. 자기 자신은 안 된다.
- 메모는 작성자 본인만 조회, 수정, 삭제한다.
- 작성자가 평가자이고 대상자가 피평가자인 그 프로젝트의 평가가 이미 '제출'이면 작성과 수정을 막는다.

**평가 작성과 제출**
- 평가 행은 프로젝트 종료 때 이미 '작성중'으로 만들어져 있다. 현재 사용자가 평가자(TB_PM_EVAL 또는 TB_PEER_EVAL의 EVLTR_EMP_ID)인 '작성중' 평가만 작성한다.
- 문항은 평가구분에 맞는 사용여부가 'Y'인 것만 보여 준다 (PM은 PM_USE_YN, 동료는 PEER_USE_YN).
- 점수는 0~10 정수. 평가내용은 WORK, COMM 두 항목.
- 임시 저장은 점수와 내용만 저장하고 상태는 '작성중'으로 둔다.
- 제출은 한 트랜잭션: 모든 문항 점수와 두 항목 내용이 있는지 확인 → EVAL_STAT = '제출', EVAL_DT = 오늘 → 그 (평가자, 피평가자, 프로젝트)의 평가메모 삭제.
- 제출된 평가의 점수와 내용은 고칠 수 없다.
- 평가 화면에는 그 피평가자에 대해 내가 쓴 메모를 함께 보여 준다.

**보정점수 조회 (경영진 'EXE'만)**
- 원점수 = 그 평가의 문항 점수 평균 (`AVG(SCORE)`).
- 보정연도 = 평가일자(EVAL_DT)의 연도.
- 적용보정값 = TB_EVLTR_ADJ_VAL에서 (보정연도, 평가자, 평가구분)으로 찾은 APLY_ADJ_VAL. 행이 없으면 0.
- 보정점수 = 원점수 − 적용보정값.
- 그 연도의 보정회차가 확정(CNFM_DT 있음)됐을 때만 보정점수를 보여 준다. 확정 전에는 원점수만 보여 주고 "확정 전"이라고 표시한다.
- 고객평가는 보정하지 않는다.
- 확정 버튼: CNFM_DT = 오늘. 확정된 회차는 다시 바꿀 수 없다.
- 지금 데이터는 2025 회차가 확정, 2026 회차가 미확정이다.

## 시나리오 2: 교체 요청 → 수용 → 제외 등록 → 배정 후보 목록

**교체 요청 접수 (경영관리팀 'MGT'가 대신 등록)**
- 진행 또는 지연 중인 프로젝트의 열린 투입 이력(END_DT IS NULL)만 대상이 된다. PM의 이력은 대상이 될 수 없다.
- 요청 담당자는 그 프로젝트 발주처의 담당자 중에서 고른다 (프로젝트 담당자가 아니어도 된다).
- 요청일자는 대상 투입 기간 안이어야 한다. 등록하면 PROC_STAT = '접수'.

**처리 (경영관리팀)**
- 철회: PROC_STAT = '철회', PROC_DT = 처리일자.
- 수용은 한 트랜잭션:
  1. PROC_STAT = '수용', PROC_DT = 처리일자
  2. 대상 이력 END_DT = 처리일자, WTHDR_RSN_ID = 'CUST'
  3. 후임을 골랐으면 후임의 프로젝트참여가 없을 때 대상과 같은 직무로 만들고, 후임 이력을 처리일자 다음 날부터 열고, SUCC_HIST_ID에 연결
- 후임은 대상과 다른 개발직원이고 지금 다른 프로젝트에 투입 중이 아니어야 한다.

**제외 등록과 지명 등록 (경영관리팀)**
- 수용된 요청에서 "제외 등록": DESIG_DIV = '제외', DESIG_DT = 오늘, REPL_REQ_ID = 그 요청, CLIENT_ID와 EMP_ID는 요청의 발주처와 대상 직원.
- 지명은 교체 요청 없이 등록한다 (REPL_REQ_ID는 비움).
- 같은 발주처와 직원에 유효 기간이 겹치는 지정이 있으면 막는다.
- 해제는 행을 지우지 않고 RLS_DT를 넣는다.

**배정 후보 목록**
- 프로젝트를 고르면 지금 다른 프로젝트에 투입 중이 아닌 개발직원을 보여 준다.
- 그 프로젝트 발주처의 유효한 지정(DESIG_DT ≤ 오늘이고 RLS_DT가 없거나 오늘 이후)을 함께 보여 준다.
- 지명된 직원은 목록 위쪽에, 제외된 직원은 경고 표시와 근거(교체 요청, 요청 담당자)를 함께 보여 준다.
- 제외된 직원도 배정은 막지 않고 확인 창만 띄운다. 배정하면 투입 이력을 만들고 그 프로젝트의 참여가 없을 때만 프로젝트참여도 만든다 (재투입이면 참여는 이미 있음).
- 투입 시작일은 프로젝트 시작일 이후여야 한다.

## 성능 실험

- 시나리오 1의 평가 결과 조회 SQL을 실험 대상으로 쓴다. 이 SQL은 Repository 한 곳에 하나의 SELECT로 둔다.
- 실험은 하드웨어가 아니라 SQL과 DB 설계로 빨라지는 것을 보여야 한다 (인덱스, 반정규화 등).
- 실험용 데이터 확대와 인덱스, 반정규화 SQL은 `docker/experiment/`에 따로 두고 데모 DB에는 적용하지 않는다. 적용 SQL과 되돌리는 SQL을 짝으로 만든다.

## 작업 방식

- 한 번에 요청받은 기능만 만든다. 다른 폴더는 필요할 때만 고친다.
- 코드를 쓰기 전에 만들 파일과 SQL을 먼저 보여 준다.
- 기능을 만든 뒤에는 결과를 DB에서 확인하는 SELECT 쿼리를 함께 알려 준다.
- 커밋 메시지는 한국어로 짧게 쓴다.
