alter table moduleversion add column iscarpresent boolean;
alter table moduleversion add column isjspresent boolean;
alter table moduleversion add column isjarpresent boolean;
update moduleversion set iscarpresent = true, isjspresent = false, isjarpresent = false;
alter table moduleversion ALTER COLUMN isjspresent set not null;
alter table moduleversion ALTER COLUMN iscarpresent set not null;
alter table moduleversion ALTER COLUMN isjarpresent set not null;

alter table moduleversion add column jsdownloads bigint;
update moduleversion set jsdownloads = 0;
alter table moduleversion alter column jsdownloads set not null;

alter table moduleversion add column ceylonminor int;
alter table moduleversion add column ceylonmajor int;
update moduleversion set ceylonminor = 0, ceylonmajor = 0;
alter table moduleversion ALTER COLUMN ceylonminor set not null;
alter table moduleversion ALTER COLUMN ceylonmajor set not null;

create table Author (
    id int8 not null,
    name TEXT,
    primary key (id)
);
create table ModuleVersion_Author (
    ModuleVersion_id int8 not null,
    authors_id int8 not null
);

alter table ModuleVersion_Author 
    add constraint FK9BE1449E7BF02576 
    foreign key (ModuleVersion_id) 
    references ModuleVersion;

alter table ModuleVersion_Author 
    add constraint FK9BE1449E42A7BA21 
    foreign key (authors_id) 
    references Author;

alter table moduleversion add column doc text;
alter table moduleversion add column license text;
