package com.databasedesign.SiCompany.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 프로젝트 현황과 인센티브 자료 화면에서 쓰는 DTO 모음.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** 위쪽 숫자: 상태별 프로젝트 수, 재직 개발직원 수와 그중 투입 중인 수 */
    public record Summary(int activeCnt, int delayedCnt, int endedCnt, int stoppedCnt, int devCnt, int deployedCnt) {
        public int benchCnt() {
            return devCnt - deployedCnt;
        }
    }

    /** 수행 중인 프로젝트 한 줄 */
    public record ActiveProject(String prjId, String prjNm, String prjStat, String clientNm,
                                LocalDate strtDt, LocalDate schdEndDt, String pmNm,
                                int curCnt, int totalCnt, int elapsedDays, int planDays) {
        /** 일정 경과율 (0~100, 예정일을 넘기면 100) */
        public int progress() {
            if (planDays <= 0) {
                return 100;
            }
            return Math.max(0, Math.min(100, elapsedDays * 100 / planDays));
        }
    }

    /** 지금 투입 중인 직원 한 줄 */
    public record CurrentMember(String prjId, String empId, String empNm, String roleId, String roleNm,
                                String grdNm, LocalDate strtDt, int days) {
    }

    /** 인센티브 기초자료 한 줄 (개발직원별, 그 연도 기준) */
    public record IncentiveRow(String empId, String empNm, String grdNm, int prjCnt, int deployDays, String roles,
                               int evalCnt, BigDecimal rawAvg, BigDecimal adjAvg, BigDecimal custAvg, int replCnt) {
    }
}
