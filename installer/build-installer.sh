#!/bin/sh

PATHSEP=":"
if [ "$OSTYPE" = "cygwin" ] ; then
PATHSEP=";"
fi

CLASSDIR=../installer/panels/classes
CONFDIR=$CLASSDIR/metro/installer/resources
JAR=$CLASSDIR/metro-panels.jar

# build custom panels
rm -rf $CLASSDIR
mkdir -p $CLASSDIR
javac -cp "../installer/lib/*" -d $CLASSDIR -sourcepath ../installer/panels/src ../installer/panels/src/metro/installer/*.java

mkdir -p $CONFDIR
cp ../conf/examples/*.properties $CONFDIR
(cd $CONFDIR; ls *.properties > settings.txt)

jar cf0 $JAR -C $CLASSDIR metro

# package the installer
java -Xmx512m -cp "../installer/lib/*${PATHSEP}${JAR}" com.izforge.izpack.compiler.bootstrap.CompilerLauncher ../installer/setup.xml -o $1.jar > ../installer/build-installer.log 2>&1

# cleanup
rm -rf $CLASSDIR
