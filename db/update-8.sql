create table ModuleComment (
    id int8 not null,
    date timestamp,
    text TEXT,
    module_id int8,
    owner_id int8,
    primary key (id)
);


alter table ModuleComment 
    add constraint FK6EA3E3353B2465E 
    foreign key (module_id) 
    references Module;

alter table ModuleComment 
    add constraint FK6EA3E33B2FABF16 
    foreign key (owner_id) 
    references user_table;

