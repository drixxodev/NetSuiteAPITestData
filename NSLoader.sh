#!/bin/bash
libDir="$(dirname $0)/lib"
if [ ! -d $libDir ] || [ $(ls $libDir | wc -l) -lt 12 ]; then
    echo "Project is incomplete. Please run 'gradle deploy' for rebuilding it."
    exit 1
else
    java -jar "$libDir/NSLoader.jar" "$@"
fi
