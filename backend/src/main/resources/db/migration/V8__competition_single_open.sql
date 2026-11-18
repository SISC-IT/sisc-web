-- 진행 중(ACTIVE)뿐 아니라 시작 전(READY) 대회까지 통틀어 하나만 허용한다.
-- ACTIVE만 막으면 READY 대회가 여러 개 생겨 참가자·계좌가 중복 생성된 뒤, 시작할 때에야 막힌다.
drop index uk_competition_single_active;
create unique index uk_competition_single_open on competition ((true)) where status in ('READY', 'ACTIVE');
