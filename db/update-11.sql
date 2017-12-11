START TRANSACTION;

create table ModuleRating (
    id int8 not null,
    mark int4 not null,
    module_id int8,
    owner_id int8,
    primary key (id)
);

alter table ModuleRating 
    add constraint FK52DE174953B2465E 
    foreign key (module_id) 
    references Module;

alter table ModuleRating 
    add constraint FK52DE1749B2FABF16 
    foreign key (owner_id) 
    references user_table;

COMMIT;
