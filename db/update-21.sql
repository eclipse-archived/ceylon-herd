START TRANSACTION;

\set DB_SCHEMA_VERSION '\'21\''

alter table dependency add column nativeJvm boolean;
alter table dependency add column nativeJs boolean;
update dependency set nativeJvm = false, nativeJs = false;
alter table dependency alter column nativeJs set not null;
alter table dependency alter column nativeJvm set not null;

alter table moduleversion add column isNativeJvm boolean;
alter table moduleversion add column isNativeJs boolean;
update moduleversion set isNativeJvm = false, isNativeJs = false;
alter table moduleversion alter column isNativeJs set not null;
alter table moduleversion alter column isNativeJvm set not null;

update herd_metainf set value = :DB_SCHEMA_VERSION where key = 'db_schema_version';

COMMIT;

