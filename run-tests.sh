#!/bin/sh
CP=conf/:classes/:lib/*:testlib/*
SP=src/java/:test/java/

if [ $# -eq 0 ]; then
TESTS="metro.crypto.Curve25519Test metro.crypto.ReedSolomonTest metro.peer.HallmarkTest metro.TokenTest metro.FakeForgingTest
metro.FastForgingTest metro.ManualForgingTest"
else
TESTS=$@
fi

/bin/rm -f metro.jar
/bin/rm -rf classes
/bin/mkdir -p classes/

javac -encoding utf8 -sourcepath ${SP} -classpath ${CP} -d classes/ src/java/metro/*.java src/java/metro/*/*.java test/java/metro/*.java test/java/metro/*/*.java || exit 1

for TEST in ${TESTS} ; do
java -classpath ${CP} org.junit.runner.JUnitCore ${TEST} ;
done



