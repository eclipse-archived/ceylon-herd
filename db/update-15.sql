START TRANSACTION;

alter TABLE moduleversion add column isScriptsPresent boolean;
UPDATE moduleversion set isScriptsPresent = false;
alter TABLE moduleversion alter column isScriptsPresent set not null;

COMMIT;
