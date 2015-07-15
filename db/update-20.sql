\set DB_SCHEMA_VERSION '\'20\''

create table HerdDependency (
    id int8 not null,
    name varchar(255),
    version varchar(255),
    upload_id int8,
    primary key (id)
);