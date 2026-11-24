
USE si_company;
SET NAMES utf8mb4;

START TRANSACTION;

-- TB_DEPT : 3건
INSERT INTO TB_DEPT (DEPT_ID, DEPT_NM) VALUES
('MKT', '마케팅'),
('MGT', '경영관리'),
('RND', '연구개발');

-- TB_AUTH : 3건
INSERT INTO TB_AUTH (AUTH_ID, AUTH_NM) VALUES
('EMP', '일반직원'),
('MGT', '경영관리팀'),
('EXE', '경영진');

-- TB_SKILL_GRD : 4건
INSERT INTO TB_SKILL_GRD (GRD_ID, GRD_NM) VALUES
('JR', '초급'),
('MD', '중급'),
('SR', '고급'),
('EX', '특급');

-- TB_SKILL : 24건
INSERT INTO TB_SKILL (SKILL_ID, SKILL_NM) VALUES
(1, 'Java'),
(2, 'Spring'),
(3, 'Spring Boot'),
(4, 'JavaScript'),
(5, 'TypeScript'),
(6, 'React'),
(7, 'Vue.js'),
(8, 'Node.js'),
(9, 'Python'),
(10, 'Django'),
(11, 'C#'),
(12, '.NET'),
(13, 'Oracle'),
(14, 'MySQL'),
(15, 'PostgreSQL'),
(16, 'MongoDB'),
(17, 'Redis'),
(18, 'Kafka'),
(19, 'Docker'),
(20, 'Kubernetes'),
(21, 'AWS'),
(22, 'Linux'),
(23, 'Git'),
(24, 'Jenkins');

-- TB_ROLE : 6건
INSERT INTO TB_ROLE (ROLE_ID, ROLE_NM) VALUES
('PM', 'PM'),
('PL', 'PL'),
('AN', '분석자'),
('DS', '설계자'),
('PG', '프로그래머'),
('TS', '테스터');

-- TB_WTHDR_RSN : 6건
INSERT INTO TB_WTHDR_RSN (WTHDR_RSN_ID, WTHDR_RSN_NM) VALUES
('PLAN', '계획된 종료'),
('CUST', '고객 교체 요청'),
('MOVE', '다른 프로젝트 차출'),
('SELF', '본인 사유'),
('RET', '퇴사'),
('STOP', '프로젝트 중단');

-- TB_EVAL_ITEM : 2건
INSERT INTO TB_EVAL_ITEM (EVAL_ITEM_ID, EVAL_ITEM_NM) VALUES
('WORK', '업무수행'),
('COMM', '커뮤니케이션');

-- TB_EVAL_QSTN : 10건
INSERT INTO TB_EVAL_QSTN (QSTN_ID, QSTN_CN, PM_USE_YN, PEER_USE_YN, CUST_USE_YN, EVAL_ITEM_ID) VALUES
(1, '맡은 업무를 일정 내에 완수하는가', 'Y', 'Y', 'Y', 'WORK'),
(2, '산출물의 품질이 우수한가', 'Y', 'Y', 'Y', 'WORK'),
(3, '요구사항을 정확히 이해하고 반영하는가', 'Y', 'N', 'Y', 'WORK'),
(4, '코드 리뷰에 성실히 참여하는가', 'N', 'Y', 'N', 'WORK'),
(5, '기술 문제를 스스로 해결하는가', 'Y', 'Y', 'N', 'WORK'),
(6, '일정 지연 위험을 사전에 공유하는가', 'Y', 'N', 'N', 'WORK'),
(7, '회의에서 의견을 명확히 전달하는가', 'Y', 'Y', 'Y', 'COMM'),
(8, '고객 및 팀원의 요청에 신속히 응답하는가', 'Y', 'Y', 'Y', 'COMM'),
(9, '갈등 상황을 협력적으로 조정하는가', 'Y', 'Y', 'N', 'COMM'),
(10, '진행 상황을 정확히 보고하는가', 'Y', 'N', 'Y', 'COMM');

COMMIT;
