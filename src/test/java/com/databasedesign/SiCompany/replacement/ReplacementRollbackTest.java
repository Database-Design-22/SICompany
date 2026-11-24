package com.databasedesign.SiCompany.replacement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.databasedesign.SiCompany.replacement.ReplacementDtos.SuccessorCandidate;

/**
 * 교체 요청 수용이 한 트랜잭션으로 묶여 있는지 확인한다.
 * 수용 ④(후임 이력 생성)에서 일부러 예외를 내고, 이미 실행된 ①(요청 상태)과 ②(대상 이력 종료)까지
 * 모두 되돌아가는지 본다. 실제 DB(docker, 포트 3307)가 처음 상태(요청 10이 '접수')일 때 돌린다.
 */
@SpringBootTest
class ReplacementRollbackTest {

    private static final int REQUEST_ID = 10;      // P26-007 조민경(17-0001) 교체 요청
    private static final int TARGET_HIST_ID = 334; // 조민경의 P26-007 투입 이력

    @Autowired
    private ReplacementService replacementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ReplacementRepository replacementRepository;

    @Test
    void 후임_이력_생성에서_실패하면_요청_상태와_대상_이력_종료까지_모두_롤백된다() {
        assumeTrue("접수".equals(requestRow().get("PROC_STAT")), "요청 10이 '접수' 상태일 때만 돌린다 (DB를 처음 상태로 되돌린 뒤 실행)");
        // 후임은 지금 후보 중 아무나 (특정 직원에 묶으면 다른 시연으로 그 직원이 배정됐을 때 테스트가 깨진다)
        List<SuccessorCandidate> candidates =
                replacementService.findSuccessorCandidates(replacementService.getRequest(REQUEST_ID));
        assumeTrue(!candidates.isEmpty(), "후임 후보가 있어야 돌린다");
        String successor = candidates.get(0).empId();

        doThrow(new IllegalStateException("테스트용 실패: 후임 이력 생성"))
                .when(replacementRepository).insertSuccessorHist(anyString(), anyString(), any(LocalDate.class));

        assertThatThrownBy(() -> replacementService.accept(REQUEST_ID, successor))
                .hasMessageContaining("테스트용 실패");

        Map<String, Object> request = requestRow();
        assertThat(request.get("PROC_STAT")).isEqualTo("접수");
        assertThat(request.get("PROC_DT")).isNull();
        assertThat(request.get("SUCC_HIST_ID")).isNull();

        Map<String, Object> hist = jdbcTemplate.queryForMap(
                "SELECT END_DT, WTHDR_RSN_ID FROM TB_PRJ_HIST WHERE PRJ_HIST_ID = ?", TARGET_HIST_ID);
        assertThat(hist.get("END_DT")).isNull();
        assertThat(hist.get("WTHDR_RSN_ID")).isNull();
    }

    private Map<String, Object> requestRow() {
        return jdbcTemplate.queryForMap(
                "SELECT PROC_STAT, PROC_DT, SUCC_HIST_ID FROM TB_STAFF_REPL_REQ WHERE REPL_REQ_ID = ?", REQUEST_ID);
    }
}
