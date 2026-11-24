package com.databasedesign.SiCompany.designation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.designation.DesignationDtos.Client;
import com.databasedesign.SiCompany.designation.DesignationDtos.Designation;
import com.databasedesign.SiCompany.designation.DesignationDtos.DevEmployee;
import com.databasedesign.SiCompany.designation.DesignationDtos.Overlap;
import com.databasedesign.SiCompany.designation.DesignationDtos.ReplRequestForExclusion;

import lombok.RequiredArgsConstructor;

/**
 * 발주처 지정인력 규칙 (CLAUDE.md 시나리오 2, 경영관리팀)
 * - 제외 등록: 수용된 요청에서 DESIG_DIV='제외', DESIG_DT=오늘, REPL_REQ_ID=그 요청,
 *   CLIENT_ID와 EMP_ID는 요청의 발주처와 대상 직원.
 * - 지명: 교체 요청 없이 등록한다 (REPL_REQ_ID는 비움).
 * - 같은 발주처와 직원에 유효 기간이 겹치는 지정이 있으면 막는다.
 * - 해제는 행을 지우지 않고 RLS_DT를 넣는다 (오늘). 해제일자부터 효력이 없다.
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
