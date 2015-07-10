
# How to make changes to the database

Each time you make a change to the database you'll need to create both a new
`db-XX.sql` file as well as a new `update-XX.sql` file.

The `db-XX.sql` file will contain the complete DB schema and is useful for
creating a new database from scratch.

The `update-XX.sql` file is meant for updating existing databases to a newer
version.

You should choose the `XX` number in the filename to be one higher than the
highest number that already exists right now.

Also, from version 19 onwards you MUST not forget to update the same version
number in the first line of the `db-XX.sql`file while all `update-XX.sql`
files MUST include the following SQL command:

    UPDATE herd_metainf SET value = 'XX' WHERE key = 'db_schema_version';

Where `XX` should of course be replaced by the new version number (the same
as the `XX` in the name of the `update-XX.sql` file itself).

The same number must also be updated in the `app/models/HerdMetainf.java` file.

