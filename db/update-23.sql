START TRANSACTION;

\set DB_SCHEMA_VERSION '\'23\''


alter table moduleversion add column artifactId varchar(255);
alter table moduleversion add column groupId varchar(255);
alter table moduleversion add column isPackageJsonPresent bool;
update moduleversion set isPackageJsonPresent = false;
alter table moduleversion alter column isPackageJsonPresent set not null;

update herd_metainf set value = :DB_SCHEMA_VERSION where key = 'db_schema_version';

COMMIT;

