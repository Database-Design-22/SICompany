package com.databasedesign.SiCompany.staff;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.staff.StaffDtos.Code;
import com.databasedesign.SiCompany.staff.StaffDtos.DraftEval;
import com.databasedesign.SiCompany.staff.StaffDtos.RetireImpact;
import com.databasedesign.SiCompany.staff.StaffDtos.RetireResult;
import com.databasedesign.SiCompany.staff.StaffDtos.StaffDetail;
import com.databasedesign.SiCompany.staff.StaffDtos.StaffRow;

import lombok.RequiredArgsConstructor;

/**
 * 직원 관리 (경영관리팀, 정의서 단위프로세스 11~13, 16, 17)
 * - 퇴사 처리(한 트랜잭션): 재직상태 '퇴사' → 열린 투입을 'RET'로 닫음 → 그 직원이 평가자인 작성중 평가를 점수·내용과 함께 삭제
 *   → 그 직원이 쓴 평가메모 삭제. 퇴사일자는 그 직원 모든 투입 날짜 이후여야 한다.
 * - 직원 정보 수정(이름, 영문이름, 최종학력), 기술등급 부여(개발), 부서 변경(비개발. 경영관리팀 권한자는 경영관리 부서 밖으로 못 옮김)
 * - 개인정보 파기(매일 배치를 버튼으로 실행): 퇴사 3년이 지난 직원의 개인정보와 그 경력증명서 기재 값을 비운다.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    public static final List<String> EDUCATIONS = List.of("고졸", "전문학사", "학사", "석사", "박사");

    private final StaffRepository staffRepository;

    public List<StaffRow> findStaff(String emplStat, String keyword) {
        return staffRepository.findStaff(emplStat, keyword);
    }

    public StaffDetail getStaff(String empId) {
        return staffRepository.findStaffDetail(empId)
                .orElseThrow(() -> new BusinessException("직원을 찾을 수 없습니다. (" + empId + ")"));
    }

    public RetireImpact findRetireImpact(String empId) {
        return staffRepository.findRetireImpact(empId);
    }

    public List<Code> findGrades() {
        return staffRepository.findGrades();
    }

    public List<Code> findDepts() {
        return staffRepository.findDepts();
    }

    public int countPiiTargets() {
        return staffRepository.countPiiTargets();
    }

    /** 퇴사 처리 (한 트랜잭션) */
    @Transactional
    public RetireResult retire(CurrentUser user, String empId, String rtrDtText) {
        StaffDetail staff = getStaff(empId);
        if (!staff.isActive()) {
            throw new BusinessException("이미 퇴사한 직원입니다. (퇴사일 " + staff.rtrDt() + ")");
        }
        if (empId.equals(user.empId())) {
            throw new BusinessException("본인은 퇴사 처리할 수 없습니다.");
        }
        LocalDate rtrDt = parseDate(rtrDtText, "퇴사일자");
        if (rtrDt.isBefore(staff.hireDt())) {
            throw new BusinessException("퇴사일자는 입사일자(" + staff.hireDt() + ") 이후여야 합니다.");
        }
        if (rtrDt.isAfter(LocalDate.now())) {
            throw new BusinessException("퇴사일자는 오늘 이후로 정할 수 없습니다.");
        }
        RetireImpact impact = findRetireImpact(empId);
        if (impact.lastHistDate() != null && rtrDt.isBefore(impact.lastHistDate())) {
            throw new BusinessException("퇴사일자(" + rtrDt + ")가 투입 이력의 날짜(" + impact.lastHistDate()
                    + ")보다 앞섭니다. 투입 기간을 먼저 정리하세요.");
        }
        if (impact.openReqCnt() > 0) {
            throw new BusinessException("이 직원의 투입에 접수된 교체 요청이 있습니다. 교체 요청 메뉴에서 먼저 수용하거나 철회하세요.");
        }

        // ① 퇴사 ② 열린 투입 닫기(RET) ③ 평가자로서 작성중인 평가 삭제 ④ 쓴 평가메모 삭제
        if (staffRepository.retireEmp(empId, rtrDt) != 1) {
            throw new BusinessException("이미 퇴사한 직원입니다. (" + empId + ")");
        }
        int closed = staffRepository.closeOpenHists(empId, rtrDt);
        List<DraftEval> drafts = staffRepository.findDraftEvals(empId);
        drafts.forEach(staffRepository::deleteEval);
        int memos = staffRepository.deleteMemosByWriter(empId);
        return new RetireResult(closed, drafts.size(), memos);
    }

    @Transactional
    public void updateBasic(String empId, String empNmText, String engNmText, String lastEdu) {
        StaffDetail staff = getStaff(empId);
        if (staff.dstrDt() != null) {
            throw new BusinessException("개인정보를 파기한 직원은 고칠 수 없습니다.");
        }
        if (empNmText == null || empNmText.isBlank() || empNmText.strip().length() > 50) {
            throw new BusinessException("이름을 50자 이내로 입력하세요.");
        }
        if (!EDUCATIONS.contains(lastEdu)) {
            throw new BusinessException("최종학력을 고르세요.");
        }
        String engNm = engNmText == null || engNmText.isBlank() ? null : engNmText.strip();
        staffRepository.updateBasic(empId, empNmText.strip(), engNm, lastEdu);
    }

    /** 기술등급 부여·변경 (빈 값이면 미부여) */
    @Transactional
    public void updateGrade(String empId, String grdIdText) {
        StaffDetail staff = getStaff(empId);
        if (!staff.isDev()) {
            throw new BusinessException("기술등급은 개발직원에게만 부여합니다.");
        }
        String grdId = grdIdText == null || grdIdText.isBlank() ? null : grdIdText;
        if (grdId != null && findGrades().stream().noneMatch(g -> g.id().equals(grdId))) {
            throw new BusinessException("없는 기술등급입니다. (" + grdId + ")");
        }
        staffRepository.updateGrade(empId, grdId);
    }

    /** 부서 변경: 경영관리팀 권한(MGT)인 직원은 경영관리 부서(MGT) 밖으로 옮길 수 없다 (제약 11) */
    @Transactional
    public void updateDept(String empId, String deptId) {
        StaffDetail staff = getStaff(empId);
        if (staff.isDev()) {
            throw new BusinessException("부서는 비개발직원만 가집니다.");
        }
        if (deptId == null || findDepts().stream().noneMatch(d -> d.id().equals(deptId))) {
            throw new BusinessException("옮길 부서를 고르세요.");
        }
        if ("MGT".equals(staff.authId()) && !"MGT".equals(deptId)) {
            throw new BusinessException("경영관리팀 권한이 있는 직원은 경영관리 부서 밖으로 옮길 수 없습니다. 권한을 먼저 바꾸세요.");
        }
        staffRepository.updateDept(empId, deptId);
    }

    /**
     * 개인정보 파기 (한 트랜잭션, 매일 배치를 버튼으로 실행): 경력증명서 기재 값 → 직원 개인정보.
     * @return 파기한 직원 수
     */
    @Transactional
    public int destroyPii() {
        staffRepository.clearCertStmts();
        return staffRepository.destroyPii();
    }

    private LocalDate parseDate(String text, String label) {
        try {
            return LocalDate.parse(text == null ? "" : text.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(label + " 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
    }
}
