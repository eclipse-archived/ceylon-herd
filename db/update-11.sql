create table modulerating (
    id int8 not null,
    module_id int8 not null,
    owner_id int8 not null,
    primary key (id),
    unique (module_id, owner_id)
);

alter table modulerating 
    add constraint FK_RATING_MODULE 
    foreign key (module_id) 
    references module;

alter table modulerating 
    add constraint FK_RATING_USER 
    foreign key (owner_id) 
    references user_table;