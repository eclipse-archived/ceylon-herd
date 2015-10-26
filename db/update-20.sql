START TRANSACTION;

\set DB_SCHEMA_VERSION '\'20\''

create table HerdDependency (
    id int8 not null,
    name varchar(255),
    version varchar(255),
    upload_id int8,
    primary key (id)
);

alter table HerdDependency 
    add constraint FKABC46CBA7C8A23E 
    foreign key (upload_id) 
    references Upload;

alter table Dependency add column resolvedFromHerd bool;
update Dependency set resolvedFromHerd = false;
alter table Dependency alter column resolvedFromHerd set not null;

alter table dependency add column nativeJvm boolean;
alter table dependency add column nativeJs boolean;
update dependency set nativeJvm = false, nativeJs = false;
alter table dependency alter column nativeJs set not null;
alter table dependency alter column nativeJvm set not null;

update herd_metainf set value = :DB_SCHEMA_VERSION where key = 'db_schema_version';

COMMIT;

