"""
docs/scenario_sql.md 생성기

Repository 클래스의 SQL 상수(텍스트 블록)를 그대로 읽어, 시나리오별 화면·버튼 순서대로 문서를 만든다.
코드의 SQL을 바꾼 뒤 이 스크립트를 다시 돌리면 문서가 최신 SQL로 맞춰진다.

실행 (프로젝트 폴더에서, 파이썬 3.9 이상):
    python scripts/gen_scenario_sql.py

화면이나 버튼, 실행 순서가 바뀌면 아래 S1, S2 목록도 함께 고친다.
"""
import re
import sys
from pathlib import Path

# 인자가 없으면 이 파일 위치 기준으로 프로젝트 폴더를 찾는다.
ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/databasedesign/SiCompany"
REPOS = {
    "CurrentUserRepository": SRC / "common/CurrentUserRepository.java",
    "MemoRepository": SRC / "memo/MemoRepository.java",
    "EvaluationRepository": SRC / "evaluation/EvaluationRepository.java",
    "AdjustmentRepository": SRC / "adjustment/AdjustmentRepository.java",
    "ReplacementRepository": SRC / "replacement/ReplacementRepository.java",
    "DesignationRepository": SRC / "designation/DesignationRepository.java",
}


def dedent_text_block(raw: str) -> str:
    """Java 텍스트 블록 본문(여는 \"\"\" 다음 줄부터 닫는 \"\"\" 앞까지)의 공통 들여쓰기를 지운다."""
    lines = raw.split("\n")
    # 마지막 줄은 닫는 따옴표 앞의 공백 (들여쓰기 계산에 포함)
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    indents.append(len(lines[-1]) - len(lines[-1].lstrip()))
    n = min(indents)
    body = "\n".join(l[n:] if len(l) >= n else l.lstrip() for l in lines[:-1])
    return body + "\n"


def parse_constants(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    consts = {}
    for m in re.finditer(r"public static final String (\w+) =(.*?);\n", text, re.S):
        name, expr = m.group(1), m.group(2)
        parts = re.findall(r'"""\n(.*?)"""|(\b[A-Z_]{3,}\b)', expr, re.S)
        value = ""
        for block, ident in parts:
            if ident:
                value += consts[ident]
            else:
                value += dedent_text_block(block)
        consts[name] = value
    return consts


SQL = {}
for repo, path in REPOS.items():
    for k, v in parse_constants(path).items():
        SQL[f"{repo}.{k}"] = v.rstrip("\n")

used = set()


def sql(ref, label, note=None):
    used.add(ref)
    return {"ref": ref, "label": label, "note": note}


# ---- 문서 구조: (화면, 주소, 사용자, 버튼 목록) ----
S1 = [
    ("내 프로젝트", "GET /my/projects", "김다니엘 (PM, 11-0007)", [
        ("화면 열기", None, [sql("MemoRepository.FIND_MY_ACTIVE_PROJECTS", "내가 참여한 진행/지연 프로젝트")]),
    ]),
    ("내 프로젝트 › 팀원 보기", "GET /my/projects/{prjId}", "김다니엘", [
        ("[팀원 보기]", None, [
            sql("MemoRepository.FIND_PARTICIPATION", "이 프로젝트에서 내 참여 (참여자만 볼 수 있음)"),
            sql("MemoRepository.FIND_TEAM_MEMBERS", "프로젝트 팀원 전체"),
            sql("MemoRepository.FIND_MEMO_TARGETS", "메모 대상 ([메모] 버튼을 띄울 팀원)"),
        ]),
    ]),
    ("대상별 메모", "GET /memos/{prjId}/{trgtEmpId}", "김다니엘", [
        ("[메모] (화면 열기)", None, [
            sql("MemoRepository.FIND_PARTICIPATION", "내 참여"),
            sql("MemoRepository.FIND_MEMO_TARGETS", "대상 확인 (메모 대상 목록에 있는지)"),
            sql("MemoRepository.COUNT_SUBMITTED_EVAL", "제출된 평가가 있는지 (있으면 작성·수정 막음)", "프로젝트가 진행/지연일 때만 실행"),
            sql("MemoRepository.FIND_MEMOS_BY_TARGET", "이 대상에게 내가 쓴 메모"),
        ]),
        ("[저장] (새 메모)", "MemoService.create", [
            sql("MemoRepository.FIND_PARTICIPATION", "내 참여 (프로젝트 상태)"),
            sql("MemoRepository.FIND_MEMO_TARGETS", "허용된 대상인지"),
            sql("MemoRepository.COUNT_SUBMITTED_EVAL", "제출된 평가가 있는지"),
            sql("MemoRepository.INSERT_MEMO", "메모 작성"),
        ]),
        ("[수정]", "MemoService.update", [
            sql("MemoRepository.FIND_OWNED_MEMO", "본인 메모인지"),
            sql("MemoRepository.FIND_PARTICIPATION", "프로젝트 상태"),
            sql("MemoRepository.COUNT_SUBMITTED_EVAL", "제출된 평가가 있는지"),
            sql("MemoRepository.UPDATE_MEMO", "메모 수정"),
        ]),
        ("[삭제] → 확인 창", "MemoService.delete", [
            sql("MemoRepository.FIND_OWNED_MEMO", "본인 메모인지"),
            sql("MemoRepository.DELETE_MEMO", "메모 삭제"),
        ]),
    ]),
    ("평가메모 (메뉴)", "GET /memos", "김다니엘", [
        ("화면 열기", None, [
            sql("MemoRepository.FIND_MY_ACTIVE_PROJECTS", "새 메모 ① 프로젝트 목록"),
            sql("MemoRepository.FIND_MEMO_TARGETS", "새 메모 ② 팀원 목록", "내 진행/지연 프로젝트마다 한 번씩 실행"),
            sql("MemoRepository.FIND_MY_MEMOS", "내가 쓴 메모 전체"),
        ]),
        ("[저장] (새 메모)", "MemoService.create", [
            sql("MemoRepository.FIND_PARTICIPATION", "내 참여"),
            sql("MemoRepository.FIND_MEMO_TARGETS", "허용된 대상인지"),
            sql("MemoRepository.COUNT_SUBMITTED_EVAL", "제출된 평가가 있는지"),
            sql("MemoRepository.INSERT_MEMO", "메모 작성"),
        ]),
    ]),
    ("내 평가", "GET /evaluations", "김직수 (평가자, 25-0001)", [
        ("화면 열기", None, [sql("EvaluationRepository.FIND_MY_EVALUATIONS", "내가 평가자인 PM·동료 평가")]),
    ]),
    ("평가 화면", "GET /evaluations/{evalId}", "김직수 (예: 평가 1495)", [
        ("[작성] (화면 열기)", None, [
            sql("EvaluationRepository.FIND_MY_EVALUATION", "평가 한 건 (내가 평가자인지)"),
            sql("EvaluationRepository.FIND_QUESTIONS", "평가구분에 맞는 문항과 입력한 점수"),
            sql("EvaluationRepository.FIND_CONTENTS", "평가항목별 내용"),
            sql("MemoRepository.FIND_MEMOS_BY_TARGET", "오른쪽: 이 피평가자에 대해 내가 쓴 메모", "작성중인 평가만 실행 (제출된 평가는 메모가 이미 삭제됨)"),
        ]),
        ("[임시 저장]", "EvaluationService.saveDraft", [
            sql("EvaluationRepository.FIND_MY_EVALUATION", "평가 한 건 (작성중인지)"),
            sql("EvaluationRepository.FIND_QUESTIONS", "받을 수 있는 문항"),
            sql("EvaluationRepository.UPSERT_SCORE", "입력한 점수 저장", "문항마다: 입력한 칸"),
            sql("EvaluationRepository.DELETE_SCORE", "비운 점수 지우기", "문항마다: 비운 칸"),
            sql("EvaluationRepository.FIND_CONTENTS", "평가항목 목록"),
            sql("EvaluationRepository.UPSERT_CONTENT", "평가내용 저장", "항목마다 (WORK, COMM)"),
        ]),
        ("[제출] → 확인 창 → [제출]", "EvaluationService.submit", [
            sql("EvaluationRepository.FIND_MY_EVALUATION", "평가 한 건"),
            sql("EvaluationRepository.FIND_QUESTIONS", "① 화면 입력 저장: 문항"),
            sql("EvaluationRepository.UPSERT_SCORE", "① 점수 저장", "문항마다"),
            sql("EvaluationRepository.DELETE_SCORE", "① 비운 점수 지우기", "문항마다"),
            sql("EvaluationRepository.FIND_CONTENTS", "① 평가항목 목록"),
            sql("EvaluationRepository.UPSERT_CONTENT", "① 평가내용 저장", "항목마다"),
            sql("EvaluationRepository.FIND_COMPLETENESS", "② 완성 여부 확인 (빠지면 예외 → 전부 롤백)"),
            sql("EvaluationRepository.SUBMIT_EVALUATION", "③ 상태 '제출', 평가일자 오늘"),
            sql("EvaluationRepository.DELETE_MEMOS_OF_EVALUATION", "④ 그 평가의 메모 삭제"),
        ]),
    ]),
    ("평가 결과·보정 (보정회차)", "GET /adjustments", "강현준 (경영진, 10-0002)", [
        ("화면 열기", None, [sql("AdjustmentRepository.FIND_ROUNDS", "보정회차 목록")]),
        ("[확정] → 확인 창", "AdjustmentService.confirm", [
            sql("AdjustmentRepository.FIND_ROUNDS", "회차 확인 (있는지, 이미 확정인지, 실행일자)"),
            sql("AdjustmentRepository.CONFIRM_ROUND", "확정일자 = 오늘 (미확정일 때만)"),
        ]),
    ]),
    ("평가 결과 조회", "GET /adjustments/results?year=&div=", "강현준", [
        ("[조회] (연도, 평가구분)", None, [
            sql("AdjustmentRepository.FIND_EVAL_YEARS", "연도 선택 목록"),
            sql("AdjustmentRepository.FIND_ROUNDS", "그 연도 회차의 확정 여부"),
            sql("AdjustmentRepository.FIND_EVAL_RESULTS", "② 평가별 결과 ★ 성능 실험 대상"),
            sql("AdjustmentRepository.FIND_EVALUATEE_SUMMARY", "③ 피평가자별 요약 (평가 결과 SQL을 감싼다)"),
        ]),
    ]),
]

S2 = [
    ("교체 요청 목록", "GET /replacements?stat=", "이현규 (경영관리팀, 13-0001)", [
        ("화면 열기", None, [sql("ReplacementRepository.FIND_REQUESTS", "교체 요청 목록 (제외 등록 여부 포함)")]),
        ("[철회] → 확인 창", "ReplacementService.withdraw", [
            sql("ReplacementRepository.FIND_REQUEST_DETAIL", "요청 확인 (접수 상태인지)"),
            sql("ReplacementRepository.WITHDRAW_REQUEST", "'철회', 처리일자 오늘"),
        ]),
    ]),
    ("교체 요청 등록", "GET /replacements/new?prjId=", "이현규", [
        ("[요청 등록] (화면 열기)", None, [sql("ReplacementRepository.FIND_ACTIVE_PROJECTS", "진행/지연 프로젝트")]),
        ("① 프로젝트 선택", None, [
            sql("ReplacementRepository.FIND_ACTIVE_PROJECTS", "진행/지연 프로젝트 (고른 프로젝트 확인)"),
            sql("ReplacementRepository.FIND_REPLACEABLE_HISTS", "② 교체 대상 후보: 열린 투입 이력 (PM 제외)"),
            sql("ReplacementRepository.FIND_CLIENT_MANAGERS", "③ 그 발주처의 담당자"),
        ]),
        ("[접수 등록]", "ReplacementService.register", [
            sql("ReplacementRepository.FIND_TARGET_HIST", "대상 이력 (프로젝트 상태, 열림, PM 아님, 투입 시작일)"),
            sql("ReplacementRepository.COUNT_CLIENT_MANAGER", "담당자가 그 발주처 소속인지"),
            sql("ReplacementRepository.FIND_OPEN_REQUEST_ID", "같은 이력에 '접수' 요청이 이미 있는지"),
            sql("ReplacementRepository.INSERT_REQUEST", "교체 요청 등록 ('접수')"),
        ]),
    ]),
    ("교체 요청 수용", "GET /replacements/{id}/accept", "이현규 (예: 요청 10, 후임 김지원)", [
        ("[수용] (화면 열기)", None, [
            sql("ReplacementRepository.FIND_REQUEST_DETAIL", "요청 한 건"),
            sql("ReplacementRepository.FIND_SUCCESSOR_CANDIDATES", "후임 후보 (투입 중 아님, 발주처 지명·제외 표시)"),
        ]),
        ("[수용] → 확인 창 → [확인]", "ReplacementService.accept", [
            sql("ReplacementRepository.FIND_REQUEST_DETAIL", "요청 확인 (접수, 프로젝트 진행/지연, 대상 이력 열림)"),
            sql("ReplacementRepository.FIND_SUCCESSOR_CANDIDATES", "후임이 후보 안에 있는지", "후임을 골랐을 때만"),
            sql("ReplacementRepository.ACCEPT_REQUEST", "① 요청 '수용', 처리일자 오늘"),
            sql("ReplacementRepository.CLOSE_TARGET_HIST", "② 대상 이력 종료, 철수사유 CUST"),
            sql("ReplacementRepository.COUNT_PTCP", "③-1 후임 참여가 있는지", "후임을 골랐을 때만 (③~⑤)"),
            sql("ReplacementRepository.INSERT_PTCP", "③-2 없으면 대상과 같은 직무로 참여 생성", "참여가 없을 때만 (재투입이면 건너뜀)"),
            sql("ReplacementRepository.INSERT_SUCCESSOR_HIST", "④ 후임 이력 (처리일자 다음 날부터)"),
            sql("ReplacementRepository.LINK_SUCCESSOR_HIST", "⑤ 후임이력ID 연결"),
        ]),
    ]),
    ("제외 등록", "GET /designations/exclude?replReqId=", "이현규 (수용 직후 안내 버튼 또는 목록의 [제외 등록])", [
        ("[제외 등록하러 가기] (화면 열기)", None, [
            sql("DesignationRepository.FIND_REQUEST_FOR_EXCLUSION", "교체 요청 (발주처, 대상 직원)"),
            sql("DesignationRepository.FIND_DESIG_BY_REQUEST", "이 요청으로 이미 등록한 제외"),
        ]),
        ("[제외 등록]", "DesignationService.registerExclusion", [
            sql("DesignationRepository.FIND_REQUEST_FOR_EXCLUSION", "요청이 '수용'인지"),
            sql("DesignationRepository.FIND_DESIG_BY_REQUEST", "이 요청으로 이미 등록했는지"),
            sql("DesignationRepository.FIND_OVERLAPS", "같은 발주처·직원의 유효 기간이 겹치는 지정"),
            sql("DesignationRepository.INSERT_DESIGNATION", "'제외' 등록 (지정일자 오늘, 근거 요청ID)"),
        ]),
    ]),
    ("발주처 지정인력", "GET /designations?clientId=", "이현규", [
        ("화면 열기 (발주처 거르기)", None, [
            sql("DesignationRepository.FIND_CLIENTS", "발주처 목록"),
            sql("DesignationRepository.FIND_DESIGNATIONS", "지정인력 목록 (유효 여부 계산)"),
            sql("DesignationRepository.FIND_DEV_EMPLOYEES", "지명 대상: 재직 개발직원"),
        ]),
        ("[지명 등록]", "DesignationService.registerNomination", [
            sql("DesignationRepository.FIND_CLIENTS", "발주처 확인"),
            sql("DesignationRepository.FIND_DEV_EMPLOYEES", "재직 개발직원인지"),
            sql("DesignationRepository.FIND_OVERLAPS", "유효 기간이 겹치는 지정"),
            sql("DesignationRepository.INSERT_DESIGNATION", "'지명' 등록 (요청ID 없음)"),
        ]),
        ("[해제] → 확인 창", "DesignationService.release", [
            sql("DesignationRepository.RELEASE_DESIGNATION", "해제일자 = 오늘 (행은 지우지 않음)"),
        ]),
    ]),
    ("인력 배정", "GET /staffing?prjId=", "이현규 (예: P26-011, 조민경)", [
        ("화면 열기", None, [sql("DesignationRepository.FIND_STAFFING_PROJECTS", "진행/지연 프로젝트")]),
        ("① 프로젝트 선택", None, [
            sql("DesignationRepository.FIND_STAFFING_PROJECTS", "고른 프로젝트 확인"),
            sql("DesignationRepository.FIND_CANDIDATES", "② 배정 후보 목록 ★ 보고서용 (지명·제외와 근거 요청 포함)"),
            sql("DesignationRepository.FIND_ROLES", "직무 목록"),
            sql("DesignationRepository.COUNT_PM", "PM이 있는지 (있으면 PM 선택 막음)"),
        ]),
        ("[배정] (제외 인력이면 확인 창)", "DesignationService.assign", [
            sql("DesignationRepository.FIND_STAFFING_PROJECTS", "프로젝트가 진행/지연인지, 시작일"),
            sql("DesignationRepository.FIND_CANDIDATES", "직원이 후보 안에 있는지 (제외 여부)"),
            sql("DesignationRepository.FIND_LATEST_OVERLAP_END", "투입 기간이 겹치는 이력"),
            sql("DesignationRepository.FIND_PTCP_ROLE", "이 프로젝트 참여가 있는지 (재투입이면 기존 직무)"),
            sql("DesignationRepository.FIND_ROLES", "직무가 있는 값인지", "참여가 없을 때만"),
            sql("DesignationRepository.COUNT_PM", "PM 중복인지", "참여가 없고 PM으로 배정할 때만"),
            sql("DesignationRepository.INSERT_PTCP", "참여 생성", "참여가 없을 때만"),
            sql("DesignationRepository.INSERT_HIST", "투입 이력 생성"),
        ]),
    ]),
]


def render_screens(screens, start_no):
    out = []
    for i, (screen, url, who, buttons) in enumerate(screens, start=start_no):
        out.append(f"### {i}. {screen}\n")
        out.append(f"- 주소: `{url}`")
        out.append(f"- 시연 사용자: {who}\n")
        for button, tx, sqls in buttons:
            if tx:
                tx_text = (f"**한 트랜잭션** (`{tx}`, `@Transactional`): 아래 SQL 전부가 하나로 묶인다. "
                           "중간에 예외가 나면 앞에서 실행한 변경까지 모두 롤백한다.")
            else:
                tx_text = "트랜잭션 없음 (조회만, SQL마다 자동 커밋)"
            out.append(f"#### {button}\n")
            out.append(f"{tx_text}\n")
            for n, s in enumerate(sqls, start=1):
                ref = s["ref"]
                head = f"{n}. {s['label']} — `{ref}`"
                if s["note"]:
                    head += f" _({s['note']})_"
                out.append(head + "\n")
                out.append("```sql\n" + SQL[ref] + "\n```\n")
    return out


doc = []
doc.append("# 시나리오별 화면·버튼과 실행 SQL\n")
doc.append("시연 순서대로 화면마다 버튼을 누르면 실행되는 SQL을 실행 순서대로 정리했다. "
           "SQL은 각 Repository의 상수(`클래스.상수`)에서 그대로 옮겼으므로 실제 실행한 SQL과 같다. "
           "화면 아래쪽 \"이 화면이 실행한 SQL\"에 나오는 것도 같은 상수다.\n")
doc.append("> 이 문서는 `scripts/gen_scenario_sql.py`로 만든다. 직접 고치지 말고, 코드의 SQL을 바꾼 뒤 "
           "프로젝트 폴더에서 `python scripts/gen_scenario_sql.py`를 다시 실행한다.\n")
doc.append("- **트랜잭션 범위:** 데이터를 바꾸는 버튼은 Service 메서드 하나에 `@Transactional`이 붙어 있어, "
           "그 버튼이 실행하는 SQL 전부가 한 트랜잭션이다. 규칙 위반(`BusinessException`)이나 DB 오류가 나면 전부 롤백한다. "
           "조회 화면은 트랜잭션 없이 SQL마다 자동 커밋한다.")
doc.append("- **`?`:** JdbcTemplate에 넘기는 값이다. 직원ID, 프로젝트ID 같은 값은 현재 사용자와 화면에서 고른 값으로 채운다.")
doc.append("- 데이터를 처음 상태로 되돌리려면 `docker` 폴더에서 `docker compose down -v` 후 `docker compose up -d`.\n")

doc.append("## 0. 공통: 현재 사용자 (모든 화면)\n")
doc.append("로그인 대신 화면 오른쪽 위에서 현재 사용자를 고른다. 세션에는 직원ID만 저장한다.\n")
doc.append("#### 모든 화면을 열 때 (헤더)\n")
doc.append("트랜잭션 없음 (조회만)\n")
doc.append("1. 현재 사용자 드롭다운: 재직 직원 목록 — `CurrentUserRepository.FIND_ACTIVE_EMPLOYEES`\n")
doc.append("```sql\n" + SQL["CurrentUserRepository.FIND_ACTIVE_EMPLOYEES"] + "\n```\n")
doc.append("2. 세션의 직원ID로 현재 사용자 조회 (권한·직원구분 확인, 요청마다) — `CurrentUserRepository.FIND_ACTIVE_EMPLOYEE_BY_ID`\n")
doc.append("```sql\n" + SQL["CurrentUserRepository.FIND_ACTIVE_EMPLOYEE_BY_ID"] + "\n```\n")
doc.append("아래 화면별 목록에서는 이 두 SQL을 반복해 적지 않는다.\n")
used.update({"CurrentUserRepository.FIND_ACTIVE_EMPLOYEES", "CurrentUserRepository.FIND_ACTIVE_EMPLOYEE_BY_ID"})

doc.append("## 시나리오 1: 평가메모 → 평가 제출 → 보정점수 조회\n")
doc += render_screens(S1, 1)
doc.append("## 시나리오 2: 교체 요청 → 수용 → 제외 등록 → 배정 후보 목록\n")
doc += render_screens(S2, 1)

# 한 트랜잭션 요약표
doc.append("## 부록: 한 트랜잭션으로 묶인 버튼 요약\n")
doc.append("| 시나리오 | 화면 | 버튼 | Service 메서드 | 바뀌는 테이블 |")
doc.append("| --- | --- | --- | --- | --- |")
rows = [
    ("1", "대상별 메모 / 평가메모", "[저장] [수정] [삭제]", "MemoService.create / update / delete", "TB_EVAL_MEMO"),
    ("1", "평가 화면", "[임시 저장]", "EvaluationService.saveDraft", "TB_EVAL_QSTN_SCORE, TB_EVAL_CN"),
    ("1", "평가 화면", "[제출]", "EvaluationService.submit", "TB_EVAL_QSTN_SCORE, TB_EVAL_CN, TB_EVAL, TB_EVAL_MEMO"),
    ("1", "평가 결과·보정", "[확정]", "AdjustmentService.confirm", "TB_ADJ_RND"),
    ("2", "교체 요청 등록", "[접수 등록]", "ReplacementService.register", "TB_STAFF_REPL_REQ"),
    ("2", "교체 요청 목록", "[철회]", "ReplacementService.withdraw", "TB_STAFF_REPL_REQ"),
    ("2", "교체 요청 수용", "[수용]", "ReplacementService.accept", "TB_STAFF_REPL_REQ, TB_PRJ_HIST, TB_PRJ_PTCP"),
    ("2", "제외 등록", "[제외 등록]", "DesignationService.registerExclusion", "TB_CLIENT_DESIG_STAFF"),
    ("2", "발주처 지정인력", "[지명 등록] [해제]", "DesignationService.registerNomination / release", "TB_CLIENT_DESIG_STAFF"),
    ("2", "인력 배정", "[배정]", "DesignationService.assign", "TB_PRJ_PTCP, TB_PRJ_HIST"),
]
for r in rows:
    doc.append("| " + " | ".join(r) + " |")
doc.append("")
doc.append("- 여러 테이블을 함께 바꾸는 버튼은 **평가 [제출]**(4개 테이블), **교체 요청 [수용]**(3개 테이블), **인력 배정 [배정]**(2개 테이블)이다.")
doc.append("- 교체 요청 [수용]의 롤백은 `src/test/.../replacement/ReplacementRollbackTest`가 확인한다. "
           "④ 후임 이력 생성에서 일부러 예외를 내면 이미 실행한 ①(요청 상태)과 ②(대상 이력 종료)까지 되돌아간다.\n")

missing = sorted(set(SQL) - used)
if missing:
    doc.append("## 부록: 이 문서에 나오지 않은 SQL 상수\n")
    for m in missing:
        doc.append(f"- `{m}`")
    doc.append("")

out = ROOT / "docs/scenario_sql.md"
out.write_text("\n".join(doc), encoding="utf-8")
print("written", out, "constants", len(SQL), "used", len(used), "missing", missing)
