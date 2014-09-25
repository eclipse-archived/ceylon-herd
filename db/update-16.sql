alter TABLE moduleversion add column jsBinMajor int4;
alter TABLE moduleversion add column jsBinMinor int4;
UPDATE moduleversion set jsBinMajor = ceylonMajor;
UPDATE moduleversion set jsBinMinor = ceylonMinor;
alter TABLE moduleversion alter column jsBinMajor set not null;
alter TABLE moduleversion alter column jsBinMinor set not null;

alter TABLE moduleversion rename column ceylonMajor to jvmBinMajor;
alter TABLE moduleversion rename column ceylonMinor to jvmBinMinor;

alter TABLE moduleversion add column isResourcesPresent bool;
UPDATE moduleversion set isResourcesPresent = false;
alter TABLE moduleversion alter column isResourcesPresent set not null;
