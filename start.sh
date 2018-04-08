#!/bin/sh
APPLICATION="metro-clone"
if [ -e ~/.${APPLICATION}/metro.pid ]; then
    PID=`cat ~/.${APPLICATION}/metro.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    if [ $STATUS -eq 0 ]; then
        echo "Metro server already running"
        exit 1
    fi
fi
mkdir -p ~/.${APPLICATION}/
DIR=`dirname "$0"`
cd "${DIR}"
if [ -x jre/bin/java ]; then
    JAVA=./jre/bin/java
else
    JAVA=java
fi
nohup ${JAVA} -cp classes:lib/*:conf:addons/classes:addons/lib/* -Dmetro.runtime.mode=desktop metro.Metro > /dev/null 2>&1 &
echo $! > ~/.${APPLICATION}/metro.pid
cd - > /dev/null
