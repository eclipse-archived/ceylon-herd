START TRANSACTION;

\set DB_SCHEMA_VERSION '\'22\''

alter table modulemember add column shared boolean;
update modulemember set shared = true;
alter table modulemember alter column shared set not null;

update herd_metainf set value = :DB_SCHEMA_VERSION where key = 'db_schema_version';

COMMIT;

