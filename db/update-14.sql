START TRANSACTION;

alter TABLE moduleversion add column isDocPresent boolean;
UPDATE moduleversion set isDocPresent = false;
alter TABLE moduleversion alter column isDocPresent set not null;

create table ModuleMember (
    id int8 not null,
    name varchar(255),
    packageName varchar(255),
    type varchar(255),
    moduleVersion_id int8,
    primary key (id)
);

alter table ModuleMember 
    add constraint FK4A8AF0467BF02576 
    foreign key (moduleVersion_id) 
    references ModuleVersion;

COMMIT;
