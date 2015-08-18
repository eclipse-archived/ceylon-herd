#!/bin/bash
CV=$(psql -qt -h localhost -U ceylon-herd -c "SELECT value AS db_schema_version FROM herd_metainf WHERE key = 'db_schema_version';" | head -1)
echo "Current version: $CV"
NV=$(($CV + 1))

VA=$(ls db/update-*.sql | sed -re 's/db\/update-([0-9]+)\.sql/\1/g' | sort -nr | head -1)
echo "Version available: $VA"

if [ $VA -gt $CV ]
then
    echo ""
    echo "Database should be updated, run the following command(s):"
    for i in `seq $NV $VA`;
    do
        echo "   psql -h localhost -U ceylon-herd -f db/update-${i}.sql"
    done
else
    echo "Database is up-to-date"
fi
