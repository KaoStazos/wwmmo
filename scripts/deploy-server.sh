#!/bin/bash

set -e

SCRIPT=`realpath $0`
SCRIPTPATH=`dirname $SCRIPT`
ROOTPATH=`dirname $SCRIPTPATH`
DISTPATH=$ROOTPATH/server/build/distributions/server.zip

pushd $ROOTPATH > /dev/null
./gradlew :server:distZip
popd > /dev/null

# copy the file from the build location to the server
scp $DISTPATH warworld@game2.war-worlds.com:/home/warworld

