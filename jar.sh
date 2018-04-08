#!/bin/sh
APPLICATION="metro-clone"
java -cp classes metro.tools.ManifestGenerator
/bin/rm -f ${APPLICATION}.jar
jar cfm ${APPLICATION}.jar resource/metro.manifest.mf -C classes . || exit 1
/bin/rm -f ${APPLICATION}service.jar
jar cfm ${APPLICATION}service.jar resource/metroservice.manifest.mf -C classes . || exit 1

echo "jar files generated successfully"