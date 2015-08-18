START TRANSACTION;

alter table ModuleVersion add column downloads int8;
alter table ModuleVersion add column sourceDownloads int8;

update ModuleVersion set downloads = 0, sourceDownloads = 0;

alter table ModuleVersion alter column downloads set not null;
alter table ModuleVersion alter column sourceDownloads set not null;

alter table ModuleVersion add unique (module_id, version);

COMMIT;

