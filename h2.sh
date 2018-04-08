#!/bin/sh
java -cp lib/h2*.jar org.h2.tools.Shell -url jdbc:h2:./metro_db/metro -user sa -password sa
