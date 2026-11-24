package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.AcceptResult;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ActiveProject;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ClientManager;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplRequest;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplaceableHist;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.RequestDetail;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.SuccessorCandidate;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.TargetHist;

import lombok.RequiredArgsConstructor;

/**
 * 교체 요청 접수 규칙 (CLAUDE.md 시나리오 2, 경영관리팀이 대신 등록)
 * - 진행 또는 지연 중인 프로젝트의 열린 투입 이력(END_DT IS NULL)만 대상이 된다. PM의 이력은 안 된다.
 * - 요청 담당자는 그 프로젝트 발주처의 담당자 중에서 고른다 (프로젝트 담당자가 아니어도 된다).
 * - 요청일자는 대상 투입 기간 안이어야 한다 (열린 투입은 오늘까지로 본다). 등록하면 PROC_STAT = '접수'.
 * - 같은 이력에 '접수' 요청이 이미 있으면 막는다.
 *
 * 처리 규칙
 * - 철회: PROC_STAT = '철회', PROC_DT = 처리일자.
 * - 수용은 한 트랜잭션 (accept 참고). 후임은 대상과 다른 개발직원이고 지금 다른 프로젝트에 투입 중이 아니어야 한다.
 */
@Service
@RequiredArgsConstructor
public class ReplacementService {

    public static final List<String> PROC_STATS = List.of("접수", "수용", "철회");
    private static final Set<String> ALLOWED_STATS = Set.copyOf(PROC_STATS);
    private static final int MAX_REASON_LENGTH = 500;

    private final ReplacementRepository replacementRepository;

    public List<ReplRequest> findRequests(String procStat) {
        return replacementRepository.findRequests(normalizeStat(procStat));
    }

    public List<ActiveProject> findActiveProjects() {
        return replacementRepository.findActiveProjects();
    }

    public ActiveProject getActiveProject(String prjId) {
        return findActiveProjects().stream()
                .filter(p -> p.prjId().equals(prjId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("진행 또는 지연 중인 프로젝트를 고르세요. (" + prjId + ")"));
    }

    public List<ReplaceableHist> findReplaceableHists(String prjId) {
        return replacementRepository.findReplaceableHists(prjId);
    }

    public List<ClientManager> findClientManagers(String prjId) {
        return replacementRepository.findClientManagers(prjId);
    }

    /** @return 새 교체요청ID */
    @Transactional
    public int register(Integer prjHistId, Integer mgrId, String reqDtText, String reqRsn) {
        if (prjHistId == null) {
            throw new BusinessException("교체 대상을 고르세요.");
        }
        if (mgrId == null) {
            throw new BusinessException("요청 담당자를 고르세요.");
        }
        TargetHist hist = replacementRepository.findTargetHist(prjHistId)
                .orElseThrow(() -> new BusinessException("투입 이력을 찾을 수 없습니다. (" + prjHistId + ")"));

        if (!"진행".equals(hist.prjStat()) && !"지연".equals(hist.prjStat())) {
            throw new BusinessException("진행 또는 지연 중인 프로젝트의 투입만 교체를 요청할 수 있습니다. (현재: " + hist.prjStat() + ")");
        }
        if (hist.endDt() != null) {
            throw new BusinessException("이미 투입이 끝난 이력입니다. (종료일 " + hist.endDt() + ")");
        }
        if ("PM".equals(hist.roleId())) {
            throw new BusinessException("PM은 교체 요청 대상이 될 수 없습니다.");
        }
        if (!replacementRepository.existsClientManager(hist.clientId(), mgrId)) {
            throw new BusinessException("요청 담당자는 이 프로젝트 발주처의 담당자 중에서 고르세요.");
        }

        LocalDate reqDt = parseDate(reqDtText, "요청일자");
        LocalDate today = LocalDate.now();
        if (reqDt.isBefore(hist.strtDt()) || reqDt.isAfter(today)) {
            throw new BusinessException("요청일자는 대상 투입 기간 안이어야 합니다. 투입 시작일(" + hist.strtDt()
                    + ")부터 오늘(" + today + ") 사이로 입력하세요.");
        }

        String reason = reqRsn == null ? "" : reqRsn.strip();
        if (reason.isEmpty()) {
            throw new BusinessException("요청 사유를 입력하세요.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("요청 사유는 " + MAX_REASON_LENGTH + "자까지 쓸 수 있습니다.");
        }

        replacementRepository.findOpenRequestId(prjHistId).ifPresent(id -> {
            throw new BusinessException("이 투입에는 이미 접수된 교체 요청이 있습니다. (요청 " + id + ")");
        });

        return replacementRepository.insertRequest(reqDt, reason, prjHistId, hist.clientId(), mgrId);
    }

    public RequestDetail getRequest(int replReqId) {
        return replacementRepository.findRequestDetail(replReqId)
                .orElseThrow(() -> new BusinessException("교체 요청을 찾을 수 없습니다. (" + replReqId + ")"));
    }

    public List<SuccessorCandidate> findSuccessorCandidates(RequestDetail request) {
        return replacementRepository.findSuccessorCandidates(request.prjId(), request.clientId(), request.trgtEmpId());
    }

    /**
     * 수용 (한 트랜잭션). 중간에 하나라도 실패하면 앞 단계까지 모두 롤백된다.
     * 처리일자는 오늘로 고정한다 (과거로 소급하면 대상 투입 기간이 줄어 다른 규칙과 엇갈릴 수 있다).
     * ① 요청 '수용', 처리일자 → ② 대상 이력 종료(CUST) → ③ 후임 참여(없을 때만, 대상과 같은 직무)
     * → ④ 후임 이력(처리일자 다음 날부터) → ⑤ 후임이력ID 연결
     */
    @Transactional
    public AcceptResult accept(int replReqId, String succEmpId) {
        RequestDetail request = getRequest(replReqId);
        throwIfNotReceived(request);
        if (!"진행".equals(request.prjStat()) && !"지연".equals(request.prjStat())) {
            throw new BusinessException("진행 또는 지연 중인 프로젝트의 요청만 수용할 수 있습니다. (현재: " + request.prjStat() + ")");
        }
        if (request.trgtEndDt() != null) {
            throw new BusinessException("대상 투입이 이미 끝났습니다. (종료일 " + request.trgtEndDt() + ")");
        }

        LocalDate procDt = LocalDate.now();
        if (procDt.isBefore(request.reqDt())) {
            throw new BusinessException("요청일자(" + request.reqDt() + ") 전에는 처리할 수 없습니다.");
        }

        String successor = (succEmpId == null || succEmpId.isBlank()) ? null : succEmpId.strip();
        SuccessorCandidate succCandidate = null;
        if (successor != null) {
            if (successor.equals(request.trgtEmpId())) {
                throw new BusinessException("교체 대상과 다른 직원을 후임으로 고르세요.");
            }
            succCandidate = findSuccessorCandidates(request).stream()
                    .filter(c -> c.empId().equals(successor))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "후임은 지금 다른 프로젝트에 투입 중이 아닌 재직 개발직원이어야 합니다. (" + successor + ")"));
            // 그 발주처의 제외 인력이어도 막지는 않는다 (화면에서 확인 창만 띄운다).
        }

        // ① 요청 상태 변경
        if (replacementRepository.acceptRequest(replReqId, procDt) != 1) {
            throw new BusinessException("이미 처리된 요청입니다. (요청 " + replReqId + ")");
        }
        // ② 대상 이력 종료
        if (replacementRepository.closeTargetHist(request.trgtHistId(), procDt) != 1) {
            throw new BusinessException("대상 투입이 이미 끝났습니다. (이력 " + request.trgtHistId() + ")");
        }
        if (successor == null) {
            return new AcceptResult(null, null, false, false);
        }
        // ③ 후임 참여 (재투입이면 이미 있으므로 만들지 않는다. 직무는 바꿀 수 없다)
        boolean ptcpCreated = false;
        if (!replacementRepository.existsPtcp(successor, request.prjId())) {
            replacementRepository.insertPtcp(successor, request.prjId(), request.trgtRoleId());
            ptcpCreated = true;
        }
        // ④ 후임 이력 (처리일자 다음 날부터)
        int succHistId = replacementRepository.insertSuccessorHist(successor, request.prjId(), procDt.plusDays(1));
        // ⑤ 후임이력ID 연결
        replacementRepository.linkSuccessorHist(replReqId, succHistId);
        return new AcceptResult(succHistId, successor, ptcpCreated, succCandidate.isExcluded());
    }

    /** 철회: 처리일자는 오늘 */
    @Transactional
    public void withdraw(int replReqId) {
        RequestDetail request = getRequest(replReqId);
        throwIfNotReceived(request);
        if (replacementRepository.withdrawRequest(replReqId, LocalDate.now()) != 1) {
            throw new BusinessException("이미 처리된 요청입니다. (요청 " + replReqId + ")");
        }
    }

    private void throwIfNotReceived(RequestDetail request) {
        if (!request.isReceived()) {
            throw new BusinessException("이미 처리된 요청입니다. (요청 " + request.replReqId() + ", 현재: " + request.procStat() + ")");
        }
    }

    /** 빈 값이면 전체(null), 그 밖에는 접수·수용·철회만 받는다. */
    public String normalizeStat(String procStat) {
        if (procStat == null || procStat.isBlank()) {
            return null;
        }
        if (!ALLOWED_STATS.contains(procStat)) {
            throw new BusinessException("처리상태는 접수, 수용, 철회 중에서 고르세요. (" + procStat + ")");
        }
        return procStat;
    }

    private LocalDate parseDate(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(label + "를 입력하세요.");
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + " 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
    }
}
