#!/bin/bash
psql -h localhost -U ceylon-herd -c "SELECT value AS db_schema_version FROM herd_metainf WHERE key = 'db_schema_version';"

