START TRANSACTION;

alter table moduleversion add column changelog text;

COMMIT;
