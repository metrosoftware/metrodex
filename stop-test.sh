#!/bin/sh
APPLICATION="metro-test"
if [ -e ~/.${APPLICATION}/metro.pid ]; then
    PID=`cat ~/.${APPLICATION}/metro.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    echo "stopping"
    while [ $STATUS -eq 0 ]; do
        kill `cat ~/.${APPLICATION}/metro.pid` > /dev/null
        sleep 5
        ps -p $PID > /dev/null
        STATUS=$?
    done
    rm -f ~/.${APPLICATION}/metro.pid
    echo "Metro server stopped"
fi

