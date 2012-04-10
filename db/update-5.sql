alter table user_table add column salt varchar(255);

alter table project alter column description type text;
alter table project alter column motivation type text;

update comment set text = (select data from pg_catalog.pg_largeobject where loid = text::int);
update comment set text = replace(text, '\\015\\012', '\n');