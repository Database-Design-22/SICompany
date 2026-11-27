package com.databasedesign.SiCompany.project;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.project.ProjectDtos.ClientManager;
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

    public List<ClientManager> findClientManagers() {
        return projectRepository.findClientManagers();
    }

    /** 그 발주처의 담당자만 (수정 화면: 발주처는 바꿀 수 없다) */
    public List<ClientManager> findClientManagers(int clientId) {
        return projectRepository.findClientManagers().stream()
                .filter(m -> m.clientId() == clientId)
                .toList();
    }

    /**
     * 등록 (한 트랜잭션). 프로젝트ID는 'P' + 시작일자 연도 두 자리 + '-' + 그 연도 일련번호 세 자리.
     * 일련번호를 구할 때 같은 연도 행을 잠가(FOR UPDATE) 동시에 등록해도 번호가 겹치지 않게 한다.
     * @param mgrKey 화면에서 고른 담당자 '발주처ID-담당자ID'
     * @return 새 프로젝트ID
     */
    @Transactional
    public String register(String prjNmText, String strtDtText, String schdEndDtText, String mgrKey) {
        String prjNm = requireName(prjNmText);
        LocalDate strtDt = parseDate(strtDtText, "시작일자");
        LocalDate schdEndDt = parseDate(schdEndDtText, "예정종료일자");
        checkSchedule(strtDt, schdEndDt);
        if (schdEndDt.isBefore(LocalDate.now())) {
            throw new BusinessException("예정종료일자(" + schdEndDt + ")가 이미 지났습니다. 새 프로젝트는 오늘 이후로 정하세요.");
        }
        ClientManager mgr = findManager(mgrKey);

        String prefix = String.format("P%02d-", strtDt.getYear() % 100);
        int seq = projectRepository.nextPrjSeq(prefix);
        if (seq > 999) {
            throw new BusinessException(strtDt.getYear() + "년 프로젝트 번호를 모두 썼습니다. (최대 999개)");
        }
        String prjId = prefix + String.format("%03d", seq);
        projectRepository.insertProject(prjId, prjNm, strtDt, schdEndDt, mgr.clientId(), mgr.mgrId());
        return prjId;
    }

    /**
     * 수정: 프로젝트명, 시작일자, 예정종료일자, 담당자. 발주처와 프로젝트ID는 바꾸지 않는다
     * (발주처가 바뀌면 이미 있는 교체 요청·지정인력의 발주처와 어긋난다).
     * 예정종료일자에 따라 상태를 '진행' 또는 '지연'으로 맞춘다.
     */
    @Transactional
    public void update(String prjId, String prjNmText, String strtDtText, String schdEndDtText, Integer mgrId) {
        ProjectRow project = getProject(prjId);
        if (!project.isActive()) {
            throw new BusinessException("진행 또는 지연 중인 프로젝트만 고칠 수 있습니다. (현재: " + project.prjStat() + ")");
        }
        String prjNm = requireName(prjNmText);
        LocalDate strtDt = parseDate(strtDtText, "시작일자");
        LocalDate schdEndDt = parseDate(schdEndDtText, "예정종료일자");
        checkSchedule(strtDt, schdEndDt);
        if (mgrId == null) {
            throw new BusinessException("발주처 담당자를 고르세요.");
        }
        if (findClientManagers(project.clientId()).stream().noneMatch(m -> m.mgrId() == mgrId)) {
            throw new BusinessException("담당자는 이 프로젝트의 발주처(" + project.clientNm() + ") 담당자 중에서 고르세요.");
        }
        LocalDate firstHistStrt = projectRepository.findFirstHistStrt(prjId);
        if (firstHistStrt != null && strtDt.isAfter(firstHistStrt)) {
            throw new BusinessException("시작일자는 첫 투입 시작일(" + firstHistStrt + ")보다 늦을 수 없습니다.");
        }
        if (projectRepository.updateProject(prjId, prjNm, strtDt, schdEndDt, mgrId) != 1) {
            throw new BusinessException("이미 종료되었거나 중단된 프로젝트입니다. (" + prjId + ")");
        }
    }

    private String requireName(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("프로젝트명을 입력하세요.");
        }
        String name = text.strip();
        if (name.length() > 100) {
            throw new BusinessException("프로젝트명은 100자까지 쓸 수 있습니다. (현재 " + name.length() + "자)");
        }
        return name;
    }

    private void checkSchedule(LocalDate strtDt, LocalDate schdEndDt) {
        if (schdEndDt.isBefore(strtDt)) {
            throw new BusinessException("예정종료일자(" + schdEndDt + ")는 시작일자(" + strtDt + ") 이후여야 합니다.");
        }
    }

    private ClientManager findManager(String mgrKey) {
        if (mgrKey == null || mgrKey.isBlank()) {
            throw new BusinessException("발주처 담당자를 고르세요.");
        }
        return projectRepository.findClientManagers().stream()
                .filter(m -> (m.clientId() + "-" + m.mgrId()).equals(mgrKey.strip()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("없는 발주처 담당자입니다. (" + mgrKey + ")"));
    }

    private LocalDate parseDate(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(label + "를 입력하세요.");
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(label + " 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
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
