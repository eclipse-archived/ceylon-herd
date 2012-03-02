create table module_admin_user (
    module int8 not null,
    admin int8 not null
);

alter table module_admin_user 
    add constraint FK8CE50E6E555F243E 
    foreign key (admin) 
    references user_table;

alter table module_admin_user 
    add constraint FK8CE50E6E7080C8FC 
    foreign key (module) 
    references Module;

