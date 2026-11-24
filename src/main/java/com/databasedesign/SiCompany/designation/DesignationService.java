package com.databasedesign.SiCompany.designation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.designation.DesignationDtos.AssignResult;
import com.databasedesign.SiCompany.designation.DesignationDtos.Candidate;
import com.databasedesign.SiCompany.designation.DesignationDtos.Client;
import com.databasedesign.SiCompany.designation.DesignationDtos.Designation;
import com.databasedesign.SiCompany.designation.DesignationDtos.DevEmployee;
import com.databasedesign.SiCompany.designation.DesignationDtos.Overlap;
import com.databasedesign.SiCompany.designation.DesignationDtos.ReplRequestForExclusion;
import com.databasedesign.SiCompany.designation.DesignationDtos.Role;
import com.databasedesign.SiCompany.designation.DesignationDtos.StaffingProject;

import lombok.RequiredArgsConstructor;

/**
 * 발주처 지정인력 규칙 (CLAUDE.md 시나리오 2, 경영관리팀)
 * - 제외 등록: 수용된 요청에서 DESIG_DIV='제외', DESIG_DT=오늘, REPL_REQ_ID=그 요청,
 *   CLIENT_ID와 EMP_ID는 요청의 발주처와 대상 직원.
 * - 지명: 교체 요청 없이 등록한다 (REPL_REQ_ID는 비움).
 * - 같은 발주처와 직원에 유효 기간이 겹치는 지정이 있으면 막는다.
 * - 해제는 행을 지우지 않고 RLS_DT를 넣는다 (오늘). 해제일자부터 효력이 없다.
 *
 * 배정 후보 목록과 배정
 * - 지금 다른 프로젝트에 투입 중이 아닌 개발직원과 그 발주처의 유효한 지정을 함께 보여 준다.
 * - 제외된 직원도 배정은 막지 않는다. 투입 이력을 만들고 참여가 없을 때만 참여도 만든다.
 * - 투입 시작일은 프로젝트 시작일 이후여야 한다.
 */
@Service
@RequiredArgsConstructor
public class DesignationService {

    private static final int MAX_REASON_LENGTH = 500;

    private final DesignationRepository designationRepository;

    public List<Client> findClients() {
        return designationRepository.findClients();
    }

    public List<Designation> findDesignations(Integer clientId) {
        return designationRepository.findDesignations(clientId);
    }

    public List<DevEmployee> findDevEmployees() {
        return designationRepository.findDevEmployees();
    }

    public ReplRequestForExclusion getRequest(int replReqId) {
        return designationRepository.findRequestForExclusion(replReqId)
                .orElseThrow(() -> new BusinessException("교체 요청을 찾을 수 없습니다. (" + replReqId + ")"));
    }

    public Integer findExclusionOf(int replReqId) {
        return designationRepository.findDesigIdByRequest(replReqId).orElse(null);
    }

    /** 제외 등록 @return 새 지정ID */
    @Transactional
    public int registerExclusion(int replReqId, String reason) {
        ReplRequestForExclusion request = getRequest(replReqId);
        if (!request.isAccepted()) {
            throw new BusinessException("수용된 교체 요청에서만 제외 등록할 수 있습니다. (요청 " + replReqId + ", 현재: " + request.procStat() + ")");
        }
        Integer existing = findExclusionOf(replReqId);
        if (existing != null) {
            throw new BusinessException("이 요청으로 이미 제외 등록했습니다. (지정 " + existing + ")");
        }
        String cleanReason = validReason(reason);
        throwIfOverlaps(request.clientId(), request.clientNm(), request.empId(), request.empNm());
        return designationRepository.insertDesignation("제외", cleanReason, request.clientId(), request.empId(), replReqId);
    }

    /** 지명 등록 @return 새 지정ID */
    @Transactional
    public int registerNomination(Integer clientId, String empId, String reason) {
        if (clientId == null) {
            throw new BusinessException("발주처를 고르세요.");
        }
        Client client = findClients().stream().filter(c -> c.clientId() == clientId).findFirst()
                .orElseThrow(() -> new BusinessException("발주처를 찾을 수 없습니다. (" + clientId + ")"));
        if (empId == null || empId.isBlank()) {
            throw new BusinessException("지명할 개발직원을 고르세요.");
        }
        DevEmployee employee = findDevEmployees().stream().filter(e -> e.empId().equals(empId)).findFirst()
                .orElseThrow(() -> new BusinessException("지명은 재직 중인 개발직원만 할 수 있습니다. (" + empId + ")"));
        String cleanReason = validReason(reason);
        throwIfOverlaps(client.clientId(), client.clientNm(), employee.empId(), employee.empNm());
        return designationRepository.insertDesignation("지명", cleanReason, client.clientId(), employee.empId(), null);
    }

    @Transactional
    public void release(int desigId) {
        if (designationRepository.release(desigId) != 1) {
            throw new BusinessException("이미 해제되었거나 없는 지정입니다. (지정 " + desigId + ")");
        }
    }

    // ===== 인력 배정 =====

    public List<StaffingProject> findStaffingProjects() {
        return designationRepository.findStaffingProjects();
    }

    public StaffingProject getStaffingProject(String prjId) {
        return findStaffingProjects().stream().filter(p -> p.prjId().equals(prjId)).findFirst()
                .orElseThrow(() -> new BusinessException("진행 또는 지연 중인 프로젝트에만 배정할 수 있습니다. (" + prjId + ")"));
    }

    public List<Candidate> findCandidates(String prjId) {
        return designationRepository.findCandidates(prjId);
    }

    public List<Role> findRoles() {
        return designationRepository.findRoles();
    }

    public boolean hasPm(String prjId) {
        return designationRepository.hasPm(prjId);
    }

    /**
     * 배정 (한 트랜잭션): 참여가 없으면 참여를 만들고, 투입 이력을 만든다.
     * 제외된 직원도 막지 않는다 (화면에서 확인 창만 띄운다).
     */
    @Transactional
    public AssignResult assign(String prjId, String empId, String roleId, String strtDtText) {
        StaffingProject project = getStaffingProject(prjId);
        if (empId == null || empId.isBlank()) {
            throw new BusinessException("배정할 직원을 고르세요.");
        }
        Candidate candidate = findCandidates(prjId).stream().filter(c -> c.empId().equals(empId)).findFirst()
                .orElseThrow(() -> new BusinessException("지금 다른 프로젝트에 투입 중이 아닌 재직 개발직원만 배정할 수 있습니다. (" + empId + ")"));

        LocalDate strtDt = parseDate(strtDtText, "투입 시작일");
        if (strtDt.isBefore(project.strtDt())) {
            throw new BusinessException("투입 시작일은 프로젝트 시작일(" + project.strtDt() + ") 이후여야 합니다.");
        }
        designationRepository.findLatestOverlapEnd(empId, strtDt).ifPresent(end -> {
            throw new BusinessException(candidate.empNm() + "의 이전 투입(~" + end + ")과 기간이 겹칩니다. 투입 시작일을 그 다음 날 이후로 하세요.");
        });

        // 재투입이면 기존 직무 그대로 (직무는 바꿀 수 없다), 처음이면 고른 직무로 참여를 만든다.
        String existingRole = designationRepository.findPtcpRole(empId, prjId).orElse(null);
        String finalRole;
        boolean ptcpCreated = false;
        if (existingRole != null) {
            if (roleId != null && !roleId.isBlank() && !roleId.equals(existingRole)) {
                throw new BusinessException("이 프로젝트에서 " + candidate.empNm() + "의 직무는 " + existingRole + "로 정해져 있어 바꿀 수 없습니다.");
            }
            finalRole = existingRole;
        } else {
            if (roleId == null || roleId.isBlank()) {
                throw new BusinessException("직무를 고르세요.");
            }
            if (findRoles().stream().noneMatch(r -> r.roleId().equals(roleId))) {
                throw new BusinessException("없는 직무입니다. (" + roleId + ")");
            }
            if ("PM".equals(roleId) && hasPm(prjId)) {
                throw new BusinessException("이 프로젝트에는 이미 PM이 있습니다. 프로젝트당 PM은 1명입니다.");
            }
            designationRepository.insertPtcp(empId, prjId, roleId);
            ptcpCreated = true;
            finalRole = roleId;
        }
        int histId = designationRepository.insertHist(empId, prjId, strtDt);
        return new AssignResult(histId, finalRole, ptcpCreated, candidate.isExcluded());
    }

    private LocalDate parseDate(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(label + "을 입력하세요.");
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(label + " 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
    }

    /** 새 지정의 기간은 오늘부터 해제 전까지이므로, 그 기간과 겹치는 지정이 있으면 막는다. */
    private void throwIfOverlaps(int clientId, String clientNm, String empId, String empNm) {
        List<Overlap> overlaps = designationRepository.findOverlaps(clientId, empId, LocalDate.now());
        if (!overlaps.isEmpty()) {
            Overlap o = overlaps.get(0);
            throw new BusinessException(clientNm + "에 " + empNm + "의 유효한 " + o.desigDiv()
                    + "(지정 " + o.desigId() + ", " + o.desigDt() + "~" + (o.rlsDt() == null ? "" : o.rlsDt())
                    + ")이 있어 새로 지정할 수 없습니다. 먼저 해제하세요.");
        }
    }

    private String validReason(String reason) {
        String clean = reason == null ? "" : reason.strip();
        if (clean.isEmpty()) {
            throw new BusinessException("지정 사유를 입력하세요.");
        }
        if (clean.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("지정 사유는 " + MAX_REASON_LENGTH + "자까지 쓸 수 있습니다.");
        }
        return clean;
    }
}
