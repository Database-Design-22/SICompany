package com.databasedesign.SiCompany.project;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.project.ProjectDtos.CloseResult;
import com.databasedesign.SiCompany.project.ProjectDtos.EvalCount;
import com.databasedesign.SiCompany.project.ProjectDtos.EvalPair;
import com.databasedesign.SiCompany.project.ProjectDtos.MemberHist;
import com.databasedesign.SiCompany.project.ProjectDtos.ProjectRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

    public static final List<String> STATS = List.of("진행", "지연", "종료", "중단");

    private final ProjectRepository projectRepository;

    public List<ProjectRow> findProjects(String prjStat) {
        return projectRepository.findProjects(prjStat);
    }

    public ProjectRow getProject(String prjId) {
        return projectRepository.findProject(prjId)
                .orElseThrow(() -> new BusinessException("프로젝트를 찾을 수 없습니다. (" + prjId + ")"));
    }

    public List<MemberHist> findMemberHists(String prjId) {
        return projectRepository.findMemberHists(prjId);
    }

    public List<EvalCount> findEvalCounts(String prjId) {
        return projectRepository.findEvalCounts(prjId);
    }

    /**
     * 종료 (한 트랜잭션). 종료일자는 오늘로 고정한다.
     * ① 프로젝트 '종료' → ② 열린 투입 이력을 'PLAN'(계획된 종료)으로 닫음
     * → ③ 평가 대상 쌍 조회 → ④ 쌍마다 평가(슈퍼타입)와 PM평가·동료평가(서브타입)를 '작성중'으로 생성.
     * 고객평가는 PM이 의뢰할 때 만든다. 평가메모는 평가를 제출할 때 지워지므로 남겨 둔다.
     */
    @Transactional
    public CloseResult end(String prjId) {
        ProjectRow project = getProject(prjId);
        LocalDate endDt = LocalDate.now();
        checkClosable(project, endDt, "종료");
        if (project.pmEmpId() == null) {
            throw new BusinessException("PM이 없는 프로젝트는 종료할 수 없습니다. PM 평가를 할 사람이 없기 때문입니다. (" + prjId + ")");
        }

        // ① 프로젝트 종료
        if (projectRepository.endProject(prjId, endDt) != 1) {
            throw new BusinessException("이미 종료되었거나 중단된 프로젝트입니다. (" + prjId + ")");
        }
        // ② 열린 투입 이력 닫기
        int closedHistCnt = projectRepository.closeOpenHists(prjId, endDt, "PLAN");
        // ③ 평가 대상 쌍 → ④ 평가 생성
        int pmEvalCnt = 0;
        int peerEvalCnt = 0;
        for (EvalPair pair : projectRepository.findEvalPairs(prjId)) {
            int evalId = projectRepository.insertEval(pair.evalDiv(), pair.evlteEmpId(), prjId);
            if ("PM".equals(pair.evalDiv())) {
                projectRepository.insertPmEval(evalId, pair.evltrEmpId(), prjId);
                pmEvalCnt++;
            } else {
                projectRepository.insertPeerEval(evalId, pair.evltrEmpId(), prjId);
                peerEvalCnt++;
            }
        }
        return new CloseResult(endDt, closedHistCnt, pmEvalCnt, peerEvalCnt, 0);
    }

    /**
     * 중단 (한 트랜잭션). 종료일자는 오늘로 고정한다.
     * ① 프로젝트 '중단' → ② 열린 투입 이력을 'STOP'(프로젝트 중단)으로 닫음 → ③ 그 프로젝트의 평가메모 삭제.
     * 중단 프로젝트는 평가하지 않으므로 평가 행은 만들지 않는다.
     */
    @Transactional
    public CloseResult stop(String prjId) {
        ProjectRow project = getProject(prjId);
        LocalDate endDt = LocalDate.now();
        checkClosable(project, endDt, "중단");

        // ① 프로젝트 중단
        if (projectRepository.stopProject(prjId, endDt) != 1) {
            throw new BusinessException("이미 종료되었거나 중단된 프로젝트입니다. (" + prjId + ")");
        }
        // ② 열린 투입 이력 닫기
        int closedHistCnt = projectRepository.closeOpenHists(prjId, endDt, "STOP");
        // ③ 평가메모 삭제
        int deletedMemoCnt = projectRepository.deleteProjectMemos(prjId);
        return new CloseResult(endDt, closedHistCnt, 0, 0, deletedMemoCnt);
    }

    /** 종료·중단 공통 검사 */
    private void checkClosable(ProjectRow project, LocalDate endDt, String action) {
        if (!project.isActive()) {
            throw new BusinessException("진행 또는 지연 중인 프로젝트만 " + action + "할 수 있습니다. (현재: " + project.prjStat() + ")");
        }
        if (endDt.isBefore(project.strtDt())) {
            throw new BusinessException("프로젝트 시작일(" + project.strtDt() + ") 전에는 " + action + "할 수 없습니다.");
        }
        LocalDate lastHistDate = projectRepository.findLastHistDate(project.prjId());
        if (lastHistDate != null && endDt.isBefore(lastHistDate)) {
            throw new BusinessException(action + "일자(" + endDt + ")가 투입 이력의 날짜(" + lastHistDate
                    + ")보다 앞섭니다. 앞으로 시작할 투입이 있으면 먼저 정리하세요.");
        }
        int openReqCnt = projectRepository.countOpenReplRequests(project.prjId());
        if (openReqCnt > 0) {
            throw new BusinessException("아직 처리하지 않은 교체 요청이 " + openReqCnt + "건 있습니다. 교체 요청 메뉴에서 먼저 수용하거나 철회하세요.");
        }
    }

    /** 빈 값이면 전체(null), 그 밖에는 진행·지연·종료·중단만 받는다. */
    public String normalizeStat(String prjStat) {
        if (prjStat == null || prjStat.isBlank()) {
            return null;
        }
        if (!STATS.contains(prjStat)) {
            throw new BusinessException("프로젝트 상태는 진행, 지연, 종료, 중단 중에서 고르세요. (" + prjStat + ")");
        }
        return prjStat;
    }
}
