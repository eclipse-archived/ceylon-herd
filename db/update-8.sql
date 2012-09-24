-- #64

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

-- #75

alter table ModuleVersion add column isRunnable bool;
update ModuleVersion set isRunnable = false;
alter table ModuleVersion alter column isRunnable set not null;

-- #47
 
create table Category (
    id int8 not null,
    description TEXT,
    name varchar(255) not null unique,
    primary key (id)
);

alter table Module add column category_id int8;
 
alter table Module 
    add constraint FK89B0928CFA266C1E 
    foreign key (category_id) 
    references Category;
