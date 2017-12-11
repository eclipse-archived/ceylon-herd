    alter table Comment 
        drop constraint FK9BDE863F856AF776;

    alter table Comment 
        drop constraint FK9BDE863FB2FABF16;

    alter table Module 
        drop constraint FK89B0928CB2FABF16;

    alter table ModuleVersion 
        drop constraint FKE3396CAC53B2465E;

    alter table Project 
        drop constraint FK50C8E2F9B2FABF16;

    alter table Upload 
        drop constraint FK9768FA21B2FABF16;

    drop table Comment cascade;

    drop table Module cascade;

    drop table ModuleVersion cascade;

    drop table Project cascade;

    drop table Upload cascade;

    drop table user_table cascade;

    drop sequence hibernate_sequence;

    create table Comment (
        id int8 not null,
        date timestamp,
        status varchar(255),
        text text,
        owner_id int8,
        project_id int8,
        primary key (id)
    );

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

    create table ModuleVersion (
        id int8 not null,
        isAPIPresent bool not null,
        isSourcePresent bool not null,
        published timestamp not null,
        version varchar(255) not null,
        module_id int8 not null,
        primary key (id)
    );

    create table Project (
        id int8 not null,
        description varchar(255),
        license varchar(255),
        moduleName varchar(255) not null,
        motivation varchar(255),
        role varchar(255),
        status varchar(255),
        url varchar(255),
        owner_id int8 not null,
        primary key (id)
    );

    create table Upload (
        id int8 not null,
        created timestamp,
        owner_id int8,
        primary key (id)
    );

    create table user_table (
        id int8 not null,
        confirmationCode varchar(255) unique,
        email varchar(255) not null,
        firstName varchar(255),
        admin bool,
        lastName varchar(255),
        password varchar(255),
        status varchar(255) not null,
        userName varchar(255) unique,
        primary key (id)
    );

    alter table Comment 
        add constraint FK9BDE863F856AF776 
        foreign key (project_id) 
        references Project;

    alter table Comment 
        add constraint FK9BDE863FB2FABF16 
        foreign key (owner_id) 
        references user_table;

    alter table Module 
        add constraint FK89B0928CB2FABF16 
        foreign key (owner_id) 
        references user_table;

    alter table ModuleVersion 
        add constraint FKE3396CAC53B2465E 
        foreign key (module_id) 
        references Module;

    alter table Project 
        add constraint FK50C8E2F9B2FABF16 
        foreign key (owner_id) 
        references user_table;

    alter table Upload 
        add constraint FK9768FA21B2FABF16 
        foreign key (owner_id) 
        references user_table;

    create sequence hibernate_sequence;
