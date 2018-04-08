#!/bin/sh
java -cp "classes:lib/*:conf" metro.tools.SignTransactionJSON $@
exit $?
