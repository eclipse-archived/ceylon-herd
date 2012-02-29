alter table ModuleVersion 
   drop constraint FKE3396CACB2FABF16;

alter table Comment add column status varchar(255);
 
create table Module (
    id int8 not null,
    codeURL varchar(255),
    friendlyName varchar(255),
    homeURL varchar(255),
    issueTrackerURL varchar(255),
    name varchar(255) not null unique,
    owner_id int8 not null,
    primary key (id)
);

alter table ModuleVersion drop column issueTrackerURL;
alter table ModuleVersion drop column name;
alter table ModuleVersion drop column owner_id;
alter table ModuleVersion add column module_id int8 not null;
alter table ModuleVersion alter column published set not null;
alter table ModuleVersion alter column version set not null;

alter table Module 
    add constraint FK89B0928CB2FABF16 
    foreign key (owner_id) 
    references user_table;

alter table ModuleVersion 
    add constraint FKE3396CAC53B2465E 
    foreign key (module_id) 
    references Module;
