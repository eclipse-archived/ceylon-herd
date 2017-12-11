START TRANSACTION;

create table Dependency (
    id int8 not null,
    export bool not null,
    name varchar(255),
    optional bool not null,
    version varchar(255),
    moduleVersion_id int8,
    primary key (id)
);


alter table Dependency 
    add constraint FK7540AF6B7BF02576 
    foreign key (moduleVersion_id) 
    references ModuleVersion;

COMMIT;

