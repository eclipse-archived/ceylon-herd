START TRANSACTION;

\set DB_SCHEMA_VERSION '\'19\''

create table herd_metainf (
    id int8 not null,
    key varchar(255) not null unique,
    value varchar(255),
    primary key (id)
);  

insert into herd_metainf (id, key, value)
values (1, 'db_schema_version', :DB_SCHEMA_VERSION);

COMMIT;

