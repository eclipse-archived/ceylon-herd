alter table ModuleScript 
    drop constraint FK54AE36777BF02576;

drop table ModuleScript cascade;

create table ModuleScript (
    id int8 not null,
    description varchar(255),
    name varchar(255),
    unix bool not null,
    moduleVersion_id int8,
    primary key (id)
);

alter table ModuleScript 
    add constraint FK54AE36777BF02576 
    foreign key (moduleVersion_id) 
    references ModuleVersion;
