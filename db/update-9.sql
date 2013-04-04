alter table user_table add column passwordresetconfirmationcode varchar(255) unique;
alter table user_table add column passwordresetconfirmationdate timestamp;