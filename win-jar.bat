java -cp classes metro.tools.ManifestGenerator
del metro.jar
jar cfm metro.jar resource\metro.manifest.mf -C classes .
del metroservice.jar
jar cfm metroservice.jar resource\metroservice.manifest.mf -C classes .

echo "jar files generated successfully"