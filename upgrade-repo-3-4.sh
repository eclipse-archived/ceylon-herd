#!/bin/bash

# Run this shell script when upgrading from a Herd with version 3 API to version 4
# It can be safely run several times
# It performs the following steps:
#  - Finds all *.doc.zip files
#  - Deletes the "module-doc" folder
#  - Unzips the *.doc.zip file
#  - Renames the *.doc.zip file to module-doc.zip

find repo -name '*.doc.zip' -exec sh -c 'rm -rf `dirname {}`/module-doc ; unzip -d `dirname {}`/module-doc {} ; mv {} `dirname {}`/module-doc.zip' \;
