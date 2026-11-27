package com.databasedesign.SiCompany.certificate;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.certificate.CertificateDtos.Applicant;
import com.databasedesign.SiCompany.certificate.CertificateDtos.CertDetail;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;

import lombok.RequiredArgsConstructor;

/**
 * 경력증명서: 신청(모든 직원), 발급(경영관리팀), 증명서 보기(본인·경영관리팀)
 */
@Controller
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /** 신청과 내 신청·발급 이력 */
    @GetMapping("/certificates")
    public String applyPage(CurrentUser user, Model model) {
        Applicant applicant = certificateService.getApplicant(user.empId());
        model.addAttribute("applicant", applicant);
        model.addAttribute("canApply", certificateService.canApply(applicant));
        model.addAttribute("participations", applicant.isDev() ? certificateService.findParticipations(user.empId()) : List.of());
        model.addAttribute("certs", certificateService.findCerts(user.empId()));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("신청자 (재직기간 판단)", CertificateRepository.FIND_APPLICANT),
                new ExecutedSql("수록할 수 있는 참여 (현재 재직기간)", CertificateRepository.FIND_PARTICIPATIONS),
                new ExecutedSql("내 신청·발급 이력", CertificateRepository.FIND_CERTS),
                new ExecutedSql("신청 ①: 경력증명서", CertificateRepository.INSERT_CERT),
                new ExecutedSql("신청 ②: 수록할 참여", CertificateRepository.INSERT_INCL_HIST)));
        return "certificate/apply";
    }

    @PostMapping("/certificates")
    public String apply(@RequestParam(required = false) String emplPrdStmtYn,
                        @RequestParam(required = false) String blngStmtYn,
                        @RequestParam(required = false) String usg,
                        @RequestParam(required = false) List<String> prjIds,
                        CurrentUser user, RedirectAttributes redirect) {
        int certId = certificateService.apply(user, emplPrdStmtYn, blngStmtYn, usg, prjIds);
        redirect.addFlashAttribute("notice", "경력증명서 " + certId + "번을 신청했습니다. 경영관리팀이 확인한 뒤 발급합니다.");
        return "redirect:/certificates";
    }

    /** 발급 대기와 발급 이력 (경영관리팀) */
    @GetMapping("/certificates/issue")
    public String issuePage(CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("certs", certificateService.findCerts(null));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("경력증명서 목록 (발급 전이 먼저)", CertificateRepository.FIND_CERTS),
                new ExecutedSql("발급 ①: 진위확인번호와 기재 값 저장", CertificateRepository.ISSUE_CERT),
                new ExecutedSql("발급 ②: 수록 이력에 발급 당시 값 복사", CertificateRepository.FILL_INCL_HISTS)));
        return "certificate/issue";
    }

    @PostMapping("/certificates/{certId}/issue")
    public String issue(@PathVariable int certId, CurrentUser user, RedirectAttributes redirect) {
        CertDetail cert = certificateService.issue(user, certId);
        redirect.addFlashAttribute("notice", "경력증명서 " + certId + "번을 발급했습니다. 진위확인번호 " + cert.vrfcNo());
        return "redirect:/certificates/" + certId;
    }

    /** 증명서 한 장 */
    @GetMapping("/certificates/{certId}")
    public String detail(@PathVariable int certId, CurrentUser user, Model model) {
        model.addAttribute("cert", certificateService.getCert(user, certId));
        model.addAttribute("incls", certificateService.findInclHists(certId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("증명서 한 장 (발급 때 저장한 기재 값)", CertificateRepository.FIND_CERT),
                new ExecutedSql("수록 이력", CertificateRepository.FIND_INCL_HISTS)));
        return "certificate/detail";
    }
}
