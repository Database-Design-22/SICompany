package com.databasedesign.SiCompany.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.CustEval;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.CustTarget;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.Manager;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.PmProject;

import lombok.RequiredArgsConstructor;

/**
 * 고객평가 의뢰·회수 (정의서 단위프로세스 49, 50)
 * - 종료된 프로젝트의 PM이 피평가자(참여자)별로 그 발주처 담당자에게 평가서를 의뢰한다.
 *   의뢰할 때 평가(구분 '고객', '작성중')와 고객평가(의뢰일자 오늘) 행을 한 트랜잭션으로 만든다. 피평가자당 1건.
 * - 평가서를 회수한 날을 기록한다 (의뢰일자 이후, 오늘 이전). 점수와 내용 입력·제출은 평가 화면에서 한다.
 */
@Service
@RequiredArgsConstructor
public class CustomerEvalService {

    private final CustomerEvalRepository customerEvalRepository;

    public List<PmProject> findMyPmProjects(CurrentUser user) {
        return customerEvalRepository.findMyPmProjects(user.empId());
    }

    /** 내가 PM인 종료 프로젝트만 연다. */
    public PmProject getMyPmProject(CurrentUser user, String prjId) {
        return findMyPmProjects(user).stream()
                .filter(p -> p.prjId().equals(prjId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("내가 PM인 종료 프로젝트의 고객평가만 다룰 수 있습니다. (" + prjId + ")"));
    }

    public List<CustTarget> findTargets(String prjId) {
        return customerEvalRepository.findTargets(prjId);
    }

    public List<Manager> findManagers(int clientId) {
        return customerEvalRepository.findManagers(clientId);
    }

    /**
     * 의뢰 (한 트랜잭션): 고른 피평가자마다 평가와 고객평가 행을 만든다.
     * @return 의뢰한 수
     */
    @Transactional
    public int request(CurrentUser user, String prjId, List<String> empIds, Integer mgrId) {
        PmProject project = getMyPmProject(user, prjId);
        if (empIds == null || empIds.isEmpty()) {
            throw new BusinessException("의뢰할 피평가자를 한 명 이상 고르세요.");
        }
        if (mgrId == null || findManagers(project.clientId()).stream().noneMatch(m -> m.mgrId() == mgrId)) {
            throw new BusinessException("평가담당자는 이 프로젝트 발주처(" + project.clientNm() + ")의 담당자 중에서 고르세요.");
        }
        Set<String> members = findTargets(prjId).stream().map(CustTarget::empId).collect(Collectors.toSet());
        int count = 0;
        for (String empId : empIds) {
            if (!members.contains(empId)) {
                throw new BusinessException("이 프로젝트 참여자가 아닙니다. (" + empId + ")");
            }
            if (customerEvalRepository.existsCustEval(prjId, empId)) {
                throw new BusinessException("이미 고객평가를 의뢰한 직원입니다. 피평가자당 1건만 의뢰합니다. (" + empId + ")");
            }
            int evalId = customerEvalRepository.insertEval(empId, prjId);
            customerEvalRepository.insertCustEval(evalId, project.clientId(), mgrId);
            count++;
        }
        return count;
    }

    /**
     * 회수: 평가서를 받은 날을 기록한다. 제출 전에는 고칠 수 있다.
     * @return 프로젝트ID
     */
    @Transactional
    public String collect(CurrentUser user, int evalId, String collDtText) {
        CustEval custEval = customerEvalRepository.findCustEval(evalId)
                .orElseThrow(() -> new BusinessException("고객평가를 찾을 수 없습니다. (평가 " + evalId + ")"));
        getMyPmProject(user, custEval.prjId());
        if ("제출".equals(custEval.evalStat())) {
            throw new BusinessException("제출된 고객평가는 회수일자를 고칠 수 없습니다.");
        }
        LocalDate collDt;
        try {
            collDt = LocalDate.parse(collDtText == null ? "" : collDtText.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("회수일자 형식이 올바르지 않습니다. (예: 2026-11-24)");
        }
        if (collDt.isBefore(custEval.reqDt())) {
            throw new BusinessException("회수일자는 의뢰일자(" + custEval.reqDt() + ") 이후여야 합니다.");
        }
        if (collDt.isAfter(LocalDate.now())) {
            throw new BusinessException("회수일자는 오늘 이후로 정할 수 없습니다.");
        }
        customerEvalRepository.updateCollDt(evalId, collDt);
        return custEval.prjId();
    }
}
