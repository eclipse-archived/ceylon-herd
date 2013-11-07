alter TABLE moduleversion add column isdocpresent boolean;
UPDATE moduleversion set isdocpresent = false;
alter TABLE moduleversion alter column isdocpresent set not null;

