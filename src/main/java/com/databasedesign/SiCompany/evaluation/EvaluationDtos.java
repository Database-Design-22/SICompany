package com.databasedesign.SiCompany.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.databasedesign.SiCompany.memo.MemoDtos.Memo;

/**
 * 평가 작성과 제출 화면에서 쓰는 DTO 모음.
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    /** ① 내 평가 목록 한 줄 */
    public record EvalSummary(int evalId, String evalDiv, String evalStat, LocalDate evalDt,
                              String prjId, String prjNm, String evlteEmpId, String evlteEmpNm,
                              int scoreCnt, int qstnCnt) {
        public boolean isSubmitted() {
            return "제출".equals(evalStat);
        }
    }

    /** 평가 한 건 (내가 평가자인 것) */
    public record Evaluation(int evalId, String evalDiv, String evalStat, LocalDate evalDt,
                             String prjId, String prjNm, String evlteEmpId, String evlteEmpNm) {
        public boolean isSubmitted() {
            return "제출".equals(evalStat);
        }

        public boolean isCustomer() {
            return "고객".equals(evalDiv);
        }
    }

    /** 평가구분에 맞는 문항과 입력한 점수 (score가 null이면 아직 입력하지 않음) */
    public record Question(int qstnId, String qstnCn, String evalItemId, Integer score) {
    }

    /** 평가항목(WORK, COMM)별 내용 */
    public record ItemContent(String evalItemId, String evalItemNm, String adv, String disadv, String expc) {
    }

    /** 제출 전 완성 여부 (DB에 저장된 값 기준) */
    public record Completeness(int qstnCnt, int scoreCnt, int itemCnt, int cnCnt) {
    }

    /** 고객평가의 의뢰·회수 정보 (회수 전이면 collDt가 null) */
    public record CustInfo(String clientNm, String mgrNm, LocalDate reqDt, LocalDate collDt) {
    }

    /** ② 평가 화면 전체. 고객평가면 memos는 비어 있고 custInfo가 있다 */
    public record EvalPage(Evaluation evaluation, List<Question> questions, List<ItemContent> contents,
                           List<Memo> memos, CustInfo custInfo) {
    }

    /** 화면에서 받은 입력. scores: 문항ID → 입력 문자열, contents: 평가항목ID → 내용 */
    public record EvalInput(Map<Integer, String> scores, Map<String, ContentInput> contents) {
    }

    public record ContentInput(String adv, String disadv, String expc) {
    }
}
