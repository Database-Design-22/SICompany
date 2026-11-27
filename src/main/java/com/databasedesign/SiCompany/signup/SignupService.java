package com.databasedesign.SiCompany.signup;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.signup.SignupDtos.Dept;
import com.databasedesign.SiCompany.signup.SignupDtos.MatchedPasser;
import com.databasedesign.SiCompany.signup.SignupDtos.Passer;
import com.databasedesign.SiCompany.signup.SignupDtos.SignupInput;
import com.databasedesign.SiCompany.signup.SignupDtos.Skill;

import lombok.RequiredArgsConstructor;

/**
 * 합격자 명부와 회원가입 (정의서 단위프로세스 1~4)
 * - 합격자 등록(경영관리팀): 합격자명, 주민등록번호, 직군, 배정 부서(비개발). 직원에 같은 주민등록번호가 있으면 넣지 않는다.
 * - 회원가입(합격자 본인) 한 트랜잭션: 합격자 대조 → 직원ID 부여 → 직원과 서브타입 생성 → 보유기술(개발) → 합격자 행 삭제.
 *   권한은 일반직원(EMP), 비개발이면 합격자의 배정 부서로 들어간다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    public static final List<String> EDUCATIONS = List.of("고졸", "전문학사", "학사", "석사", "박사");
    private static final Pattern RRN = Pattern.compile("^(\\d{6})-?([1-4]\\d{6})$");
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z][a-z0-9._]{3,29}$");
    private static final Pattern TELNO = Pattern.compile("^0\\d{1,2}-\\d{3,4}-\\d{4}$");

    private final SignupRepository signupRepository;

    public List<Passer> findPassers() {
        return signupRepository.findPassers();
    }

    public List<Dept> findDepts() {
        return signupRepository.findDepts();
    }

    public List<Skill> findSkills() {
        return signupRepository.findSkills();
    }

    /** 합격자 등록 */
    @Transactional
    public void registerPasser(String nameText, String rrnText, String jobgrp, String deptIdText) {
        String name = requireName(nameText);
        String rrn = normalizeRrn(rrnText);
        String deptId = checkJobgrp(jobgrp, deptIdText);
        if (signupRepository.existsEmpByRrn(rrn)) {
            throw new BusinessException("이미 직원으로 등록된 주민등록번호입니다. 퇴사자라면 재입사 처리 대상입니다.");
        }
        if (signupRepository.existsPasserByRrn(rrn)) {
            throw new BusinessException("이미 합격자 명부에 있는 주민등록번호입니다.");
        }
        signupRepository.insertPasser(name, rrn, jobgrp, deptId);
    }

    /** 미가입 합격자 삭제 */
    @Transactional
    public void deletePasser(int passerId) {
        if (signupRepository.deletePasser(passerId) != 1) {
            throw new BusinessException("합격자 명부에 없는 번호입니다. (" + passerId + ")");
        }
    }

    /** 로그인ID를 쓸 수 있으면 null, 아니면 이유 */
    public String checkLoginId(String loginIdText) {
        String loginId = loginIdText == null ? "" : loginIdText.strip();
        if (!LOGIN_ID.matcher(loginId).matches()) {
            return "로그인ID는 영문 소문자로 시작하고 영문 소문자·숫자·마침표·밑줄로 4~30자여야 합니다.";
        }
        if (signupRepository.existsLoginId(loginId)) {
            return "이미 사용 중인 로그인ID입니다. 다른 ID를 입력하세요.";
        }
        return null;
    }

    /**
     * 회원가입 (한 트랜잭션).
     * @return 새 직원ID
     */
    @Transactional
    public String signup(SignupInput in) {
        String empNm = requireName(in.empNm());
        String rrn = normalizeRrn(in.rrn());
        String empDiv = in.empDiv();
        if (!"개발".equals(empDiv) && !"비개발".equals(empDiv)) {
            throw new BusinessException("직군은 개발 또는 비개발 중에서 고르세요.");
        }
        if (!EDUCATIONS.contains(in.lastEdu())) {
            throw new BusinessException("최종학력을 고르세요.");
        }
        String loginProblem = checkLoginId(in.loginId());
        if (loginProblem != null) {
            throw new BusinessException(loginProblem);
        }
        if (in.password() == null || in.password().length() < 8 || in.password().length() > 64) {
            throw new BusinessException("비밀번호는 8~64자로 입력하세요.");
        }
        String telno = blankToNull(in.telno());
        if (telno != null && !TELNO.matcher(telno).matches()) {
            throw new BusinessException("전화번호는 010-1234-5678 형식으로 입력하세요.");
        }
        String addr = blankToNull(in.addr());
        if (addr != null && addr.length() > 200) {
            throw new BusinessException("주소는 200자까지 쓸 수 있습니다.");
        }
        String engNm = blankToNull(in.engNm());
        LocalDate hireDt = parseHireDt(in.hireDt());
        List<Integer> skillIds = in.skillIds() == null ? List.of() : in.skillIds().stream().distinct().toList();
        if ("비개발".equals(empDiv) && !skillIds.isEmpty()) {
            throw new BusinessException("보유기술은 개발직원만 입력합니다.");
        }
        Set<Integer> validSkills = findSkills().stream().map(Skill::skillId).collect(Collectors.toSet());
        if (!validSkills.containsAll(skillIds)) {
            throw new BusinessException("없는 기술이 들어 있습니다.");
        }

        // ① 이미 가입한 사람인지 ② 합격자 대조
        if (signupRepository.existsEmpByRrn(rrn)) {
            throw new BusinessException("이미 가입한 주민등록번호입니다.");
        }
        MatchedPasser passer = signupRepository.findMatchingPasser(empNm, rrn, empDiv)
                .orElseThrow(() -> new BusinessException("합격자 명부에서 찾을 수 없습니다. 이름, 주민등록번호, 직군을 다시 확인하세요."));

        // ③ 직원ID 'YY-NNNN' (입사연도)
        String prefix = String.format("%02d-", hireDt.getYear() % 100);
        int seq = signupRepository.nextEmpSeq(prefix);
        if (seq > 9999) {
            throw new BusinessException(hireDt.getYear() + "년 직원ID를 모두 썼습니다.");
        }
        String empId = prefix + String.format("%04d", seq);

        // ④ 직원 ⑤ 서브타입 ⑥ 보유기술 ⑦ 합격자 삭제
        signupRepository.insertEmp(empId, rrn, empNm, in.loginId().strip(), in.password(), hireDt,
                empDiv, engNm, in.lastEdu(), telno, addr);
        if ("개발".equals(empDiv)) {
            signupRepository.insertDevEmp(empId);
            for (Integer skillId : skillIds) {
                signupRepository.insertHoldSkill(skillId, empId);
            }
        } else {
            signupRepository.insertNdevEmp(empId, passer.deptId());
        }
        signupRepository.deletePasser(passer.passerId());
        return empId;
    }

    /** 주민등록번호를 'YYMMDD-NNNNNNN' 형태로 맞춘다 (해시를 만들기 전에 형태를 통일해야 대조가 된다). */
    private String normalizeRrn(String text) {
        var m = RRN.matcher(text == null ? "" : text.strip());
        if (!m.matches()) {
            throw new BusinessException("주민등록번호는 900101-1234567 형식으로 입력하세요.");
        }
        return m.group(1) + "-" + m.group(2);
    }

    private String checkJobgrp(String jobgrp, String deptIdText) {
        String deptId = blankToNull(deptIdText);
        if ("개발".equals(jobgrp)) {
            if (deptId != null) {
                throw new BusinessException("개발 합격자는 배정 부서가 없습니다.");
            }
            return null;
        }
        if ("비개발".equals(jobgrp)) {
            if (deptId == null || findDepts().stream().noneMatch(d -> d.deptId().equals(deptId))) {
                throw new BusinessException("비개발 합격자는 배정 부서를 고르세요.");
            }
            return deptId;
        }
        throw new BusinessException("직군은 개발 또는 비개발 중에서 고르세요.");
    }

    private String requireName(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("이름을 입력하세요.");
        }
        String name = text.strip();
        if (name.length() > 50) {
            throw new BusinessException("이름은 50자까지 쓸 수 있습니다.");
        }
        return name;
    }

    private LocalDate parseHireDt(String text) {
        try {
            LocalDate hireDt = LocalDate.parse(text == null ? "" : text.strip());
            if (hireDt.isAfter(LocalDate.now().plusYears(1))) {
                throw new BusinessException("입사일자가 너무 먼 미래입니다.");
            }
            return hireDt;
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("입사일자 형식이 올바르지 않습니다. (예: 2026-12-01)");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
