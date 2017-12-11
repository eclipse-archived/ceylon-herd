START TRANSACTION;

alter table dependency add column resolvedFromMaven boolean;
update dependency set resolvedFromMaven = false;
alter table dependency alter column resolvedFromMaven set not null;
 
create table MavenDependency (
    id int8 not null,
    name varchar(255),
    version varchar(255),
    upload_id int8,
    primary key (id)
);

alter table MavenDependency 
    add constraint FK50917167C8A23E 
    foreign key (upload_id) 
    references Upload;

COMMIT;

