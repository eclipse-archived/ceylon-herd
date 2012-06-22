alter table moduleversion add column iscarpresent boolean;
alter table moduleversion add column isjspresent boolean;
update moduleversion set iscarpresent = true, isjspresent = false;
alter table moduleversion ALTER COLUMN isjspresent set not null;
alter table moduleversion ALTER COLUMN iscatpresent set not null;
alter table moduleversion ALTER COLUMN iscarpresent set not null;

alter table moduleversion add column jsdownloads bigint;
update moduleversion set jsdownloads = 0;
alter table moduleversion alter column jsdownloads set not null;
