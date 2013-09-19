alter TABLE user_table add column isbcrypt boolean;
UPDATE user_table set isbcrypt = false;
alter TABLE user_table alter column isbcrypt set not null;

