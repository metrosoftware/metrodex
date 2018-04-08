#!/bin/sh
if [ -x jre/bin/java ]; then
    JAVA=./jre/bin/java
else
    JAVA=java
fi
${JAVA} -cp classes:lib/*:conf:addons/classes:addons/lib/* -Dmetro.runtime.mode=desktop -Dmetro.runtime.dirProvider=metro.env.DefaultDirProvider metro.Metro
