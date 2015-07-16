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

alter table module_version add column resolvedFromHerd bool;
update module_version set resolvedFromHerd = false;
alter table module_version alter column resolvedFromHerd set not null;
 
update herd_metainf set value = :DB_SCHEMA_VERSION where key = 'db_schema_version';
