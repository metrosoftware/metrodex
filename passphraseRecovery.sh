#!/bin/sh
echo "***********************************************************************"
echo "* Use this shell script to search for a lost passphrase.              *"
echo "*                                                                     *"
echo "* When using desktop mode, invoke this script as:                     *"
echo "* ./passphraseRecovery.sh -Dmetro.runtime.mode=desktop                  *"
echo "***********************************************************************"

java -Xmx1024m -cp "classes:lib/*:conf" $@ metro.tools.PassphraseRecovery
exit $?
