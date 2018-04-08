#!/bin/sh
CP="conf/;classes/;lib/*;testlib/*"
SP="src/java/;test/java/"
TESTS="metro.crypto.Curve25519Test metro.crypto.ReedSolomonTest"

/bin/rm -f metro.jar
/bin/rm -rf classes
/bin/mkdir -p classes/

javac -encoding utf8 -sourcepath $SP -classpath $CP -d classes/ src/java/metro/*.java src/java/metro/*/*.java test/java/metro/*/*.java || exit 1

java -classpath $CP org.junit.runner.JUnitCore $TESTS

