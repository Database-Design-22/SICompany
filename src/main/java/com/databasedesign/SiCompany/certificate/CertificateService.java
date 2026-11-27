package com.databasedesign.SiCompany.certificate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.certificate.CertificateDtos.Applicant;
import com.databasedesign.SiCompany.certificate.CertificateDtos.CertDetail;
import com.databasedesign.SiCompany.certificate.CertificateDtos.CertRow;
import com.databasedesign.SiCompany.certificate.CertificateDtos.InclRow;
import com.databasedesign.SiCompany.certificate.CertificateDtos.Participation;
import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;

import lombok.RequiredArgsConstructor;

/**
 * 경력증명서 (고도화 ③, 정의서 단위프로세스 61~63)
 * - 신청: 계속 30일 이상 근무한 직원이 기재 항목, 용도, 수록할 참여를 골라 신청한다. 수록할 참여는 현재 재직기간의 본인 참여만.
 * - 발급(경영관리팀): 한 트랜잭션으로 진위확인번호와 기재 값(성명·재직기간·소속)을 저장하고, 수록 이력에 프로젝트명·발주처명·
 *   직무명·투입기간을 복사한다. 원본이 나중에 바뀌어도 증명서 내용은 그대로다. 발급 후에는 고칠 수 없다.
 * - 회사 안에서만 쓰는 정보(평가, 메모, 철수 사유, 교체 요청)는 기재하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final int MIN_WORK_DAYS = 30;
    private static final int MAX_USAGE_LENGTH = 100;

    private final CertificateRepository certificateRepository;

    public Applicant getApplicant(String empId) {
        return certificateRepository.findApplicant(empId)
                .orElseThrow(() -> new BusinessException("직원을 찾을 수 없습니다. (" + empId + ")"));
    }

    public List<Participation> findParticipations(String empId) {
        return certificateRepository.findParticipations(empId);
    }

    public List<CertRow> findCerts(String trgtEmpId) {
        return certificateRepository.findCerts(trgtEmpId);
    }

    /** 본인 증명서이거나 경영관리팀일 때만 연다. */
    public CertDetail getCert(CurrentUser user, int certId) {
        CertDetail cert = certificateRepository.findCert(certId)
                .orElseThrow(() -> new BusinessException("경력증명서를 찾을 수 없습니다. (" + certId + ")"));
        if (!cert.trgtEmpId().equals(user.empId()) && !user.isMgt()) {
            throw new BusinessException("본인 경력증명서만 볼 수 있습니다. (경영관리팀은 모두 볼 수 있습니다)");
        }
        return cert;
    }

    public List<InclRow> findInclHists(int certId) {
        return certificateRepository.findInclHists(certId);
    }

    /** 계속 30일 이상 근무했는지 (입사일부터 오늘까지) */
    public boolean canApply(Applicant applicant) {
        return ChronoUnit.DAYS.between(applicant.hireDt(), LocalDate.now()) + 1 >= MIN_WORK_DAYS;
    }

    /**
     * 신청 (한 트랜잭션): 경력증명서 → 수록할 참여.
     * @return 새 증명서ID
     */
    @Transactional
    public int apply(CurrentUser user, String emplPrdStmtYn, String blngStmtYn, String usgText, List<String> prjIds) {
        Applicant applicant = getApplicant(user.empId());
        if (!canApply(applicant)) {
            throw new BusinessException("경력증명서는 계속 " + MIN_WORK_DAYS + "일 이상 근무한 직원만 신청할 수 있습니다. (입사 "
                    + applicant.hireDt() + ")");
        }
        String emplPrd = yn(emplPrdStmtYn);
        String blng = yn(blngStmtYn);
        String usg = usgText == null || usgText.isBlank() ? null : usgText.strip();
        if (usg != null && usg.length() > MAX_USAGE_LENGTH) {
            throw new BusinessException("용도는 " + MAX_USAGE_LENGTH + "자까지 쓸 수 있습니다.");
        }
        List<String> selected = prjIds == null ? List.of() : prjIds.stream().distinct().toList();
        if (!applicant.isDev() && !selected.isEmpty()) {
            throw new BusinessException("비개발직원은 수록할 프로젝트 없이 재직기간과 소속만 기재합니다.");
        }
        Set<String> allowed = findParticipations(user.empId()).stream()
                .map(Participation::prjId).collect(Collectors.toSet());
        for (String prjId : selected) {
            if (!allowed.contains(prjId)) {
                throw new BusinessException("현재 재직기간에 투입한 본인 참여만 수록할 수 있습니다. (" + prjId + ")");
            }
        }

        int certId = certificateRepository.insertCert(emplPrd, blng, usg, user.empId());
        for (String prjId : selected) {
            certificateRepository.insertInclHist(certId, prjId, user.empId());
        }
        return certId;
    }

    /** 발급 (한 트랜잭션): 진위확인번호·기재 값 저장 → 수록 이력에 발급 당시 값 복사. 경영관리팀만. */
    @Transactional
    public CertDetail issue(CurrentUser user, int certId) {
        user.require(CurrentUser.MGT);
        CertDetail cert = getCert(user, certId);
        if (cert.isIssued()) {
            throw new BusinessException("이미 발급한 증명서입니다. 발급한 증명서는 고칠 수 없습니다. (" + cert.vrfcNo() + ")");
        }
        Applicant applicant = getApplicant(cert.trgtEmpId());
        if (applicant.rtrDt() != null && applicant.rtrDt().plusYears(3).isBefore(LocalDate.now())) {
            throw new BusinessException("퇴사 후 3년이 지난 직원의 증명서는 발급할 수 없습니다. (퇴사 " + applicant.rtrDt() + ")");
        }
        if (certificateRepository.issueCert(certId, user.empId()) != 1) {
            throw new BusinessException("이미 발급한 증명서입니다. (" + certId + ")");
        }
        certificateRepository.fillInclHists(certId);
        return getCert(user, certId);
    }

    private static String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }
}
