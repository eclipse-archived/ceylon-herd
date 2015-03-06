alter TABLE ModuleScript add column plugin bool;
UPDATE ModuleScript set plugin = false;
alter TABLE ModuleScript alter column plugin set not null;

alter TABLE ModuleScript add column module varchar(255);
