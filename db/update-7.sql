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
