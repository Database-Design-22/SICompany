package com.databasedesign.SiCompany.employee;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.employee.EmployeeDtos.AsOfRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.AuthTarget;
import com.databasedesign.SiCompany.employee.EmployeeDtos.CareerRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Code;
import com.databasedesign.SiCompany.employee.EmployeeDtos.EmployeeDetail;
import com.databasedesign.SiCompany.employee.EmployeeDtos.EmployeeRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Grade;
import com.databasedesign.SiCompany.employee.EmployeeDtos.SearchCond;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Skill;

import lombok.RequiredArgsConstructor;

/**
 * 직원 검색과 내 정보 (정의서 단위프로세스 7~10, 15, 35, 36)
 * - 직원 검색·특정 시점 참여 조회는 경영진만. 주민등록번호는 어디에도 보여 주지 않는다.
 * - 내 정보: 본인 정보 조회, 전화번호·주소·비밀번호 수정, 보유기술 등록·삭제(개발직원만), 본인 경력 조회.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final java.util.regex.Pattern TELNO = java.util.regex.Pattern.compile("^0\\d{1,2}-\\d{3,4}-\\d{4}$");

    private final EmployeeRepository employeeRepository;

    public List<EmployeeRow> search(SearchCond cond) {
        return employeeRepository.search(cond);
    }

    public EmployeeDetail getEmployee(String empId) {
        return employeeRepository.findEmployee(empId)
                .orElseThrow(() -> new BusinessException("직원을 찾을 수 없습니다. (" + empId + ")"));
    }

    public List<Skill> findHoldSkills(String empId) {
        return employeeRepository.findHoldSkills(empId);
    }

    public List<CareerRow> findCareer(String empId) {
        return employeeRepository.findCareer(empId);
    }

    public List<AsOfRow> findAsOf(LocalDate date) {
        return employeeRepository.findAsOf(date);
    }

    public List<Skill> findAllSkills() {
        return employeeRepository.findAllSkills();
    }

    public List<Grade> findGrades() {
        return employeeRepository.findGrades();
    }

    /** 전화번호와 주소 (빈 값은 NULL로 저장) */
    @Transactional
    public void updateContact(CurrentUser user, String telnoText, String addrText) {
        String telno = blankToNull(telnoText);
        String addr = blankToNull(addrText);
        if (telno != null && !TELNO.matcher(telno).matches()) {
            throw new BusinessException("전화번호는 010-1234-5678 형식으로 입력하세요.");
        }
        if (addr != null && addr.length() > 200) {
            throw new BusinessException("주소는 200자까지 쓸 수 있습니다.");
        }
        employeeRepository.updateContact(user.empId(), telno, addr);
    }

    /** 비밀번호: 8자 이상, 확인 입력과 같아야 한다. DB에는 해시만 저장한다. */
    @Transactional
    public void updatePassword(CurrentUser user, String password, String confirm) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BusinessException("비밀번호는 8~64자로 입력하세요.");
        }
        if (!password.equals(confirm)) {
            throw new BusinessException("비밀번호 확인이 일치하지 않습니다.");
        }
        employeeRepository.updatePassword(user.empId(), password);
    }

    /** 보유기술 등록: 개발직원만, 같은 기술은 한 번만 */
    @Transactional
    public String addSkill(CurrentUser user, Integer skillId) {
        user.requireDev();
        Skill skill = findAllSkills().stream()
                .filter(s -> skillId != null && s.skillId() == skillId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("추가할 기술을 고르세요."));
        if (employeeRepository.existsHoldSkill(skill.skillId(), user.empId())) {
            throw new BusinessException("이미 보유기술에 있는 기술입니다. (" + skill.skillNm() + ")");
        }
        employeeRepository.insertHoldSkill(skill.skillId(), user.empId());
        return skill.skillNm();
    }

    @Transactional
    public void deleteSkill(CurrentUser user, int skillId) {
        user.requireDev();
        if (employeeRepository.deleteHoldSkill(skillId, user.empId()) != 1) {
            throw new BusinessException("보유기술에 없는 기술입니다. (" + skillId + ")");
        }
    }

    public List<Code> findAuths() {
        return employeeRepository.findAuths();
    }

    /**
     * 권한 변경 (경영진): 재직 중인 직원만. 본인 권한은 바꿀 수 없다 (경영진이 아무도 없게 되는 것을 막는다).
     * - 경영관리팀(MGT): 경영관리 부서(MGT)의 비개발직원에게만 (제약 11)
     * - 경영진(EXE): 비개발직원에게만 (개발직원은 프로젝트에 투입되는 인력이다)
     * @return 대상 직원 이름
     */
    @Transactional
    public String changeAuth(CurrentUser user, String empId, String authId) {
        user.require(CurrentUser.EXE);
        AuthTarget target = employeeRepository.findAuthTarget(empId)
                .orElseThrow(() -> new BusinessException("직원을 찾을 수 없습니다. (" + empId + ")"));
        if (!"재직".equals(target.emplStat())) {
            throw new BusinessException("퇴사한 직원의 권한은 바꿀 수 없습니다.");
        }
        if (empId.equals(user.empId())) {
            throw new BusinessException("본인 권한은 바꿀 수 없습니다.");
        }
        if (findAuths().stream().noneMatch(a -> a.id().equals(authId))) {
            throw new BusinessException("권한은 일반직원, 경영관리팀, 경영진 중에서 고르세요.");
        }
        if ("MGT".equals(authId) && !("비개발".equals(target.empDiv()) && "MGT".equals(target.deptId()))) {
            throw new BusinessException("경영관리팀 권한은 경영관리 부서의 비개발직원에게만 줄 수 있습니다.");
        }
        if ("EXE".equals(authId) && !"비개발".equals(target.empDiv())) {
            throw new BusinessException("경영진 권한은 비개발직원에게만 줄 수 있습니다.");
        }
        employeeRepository.updateAuth(empId, authId);
        return target.empNm();
    }

    public LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("날짜 형식이 올바르지 않습니다. (예: 2025-06-30)");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
