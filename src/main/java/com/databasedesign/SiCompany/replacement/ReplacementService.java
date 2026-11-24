package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ActiveProject;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ClientManager;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplRequest;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplaceableHist;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.TargetHist;

import lombok.RequiredArgsConstructor;

/**
 * 교체 요청 접수 규칙 (CLAUDE.md 시나리오 2, 경영관리팀이 대신 등록)
 * - 진행 또는 지연 중인 프로젝트의 열린 투입 이력(END_DT IS NULL)만 대상이 된다. PM의 이력은 안 된다.
 * - 요청 담당자는 그 프로젝트 발주처의 담당자 중에서 고른다 (프로젝트 담당자가 아니어도 된다).
 * - 요청일자는 대상 투입 기간 안이어야 한다 (열린 투입은 오늘까지로 본다). 등록하면 PROC_STAT = '접수'.
 * - 같은 이력에 '접수' 요청이 이미 있으면 막는다.
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

        LocalDate reqDt = parseDate(reqDtText);
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

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("요청일자를 입력하세요.");
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException("요청일자 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
    }
}
