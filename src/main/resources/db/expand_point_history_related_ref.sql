-- 오늘의 퀴즈·뉴스 상세 퀴즈 보상 시 claimKey 저장용 (수동 적용 시)
ALTER TABLE point_history ALTER COLUMN related_ref TYPE varchar(220);
