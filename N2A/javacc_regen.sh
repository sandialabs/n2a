#!/bin/bash

export PROJ_ROOT=`pwd`

# Purge generated files that might be replaced
cd "$PROJ_ROOT/src/gov/sandia/n2a/parsing/gen"
rm TokenMgrError.java
rm ParseException.java
rm Token.java
rm JavaCharStream.java

# Generate
cd "$PROJ_ROOT/src/gov/sandia/n2a/parsing/grammar"
java -classpath "$PROJ_ROOT/lib/javacc-5.0/javacc.jar" jjtree n2a.jjt
java -classpath "$PROJ_ROOT/lib/javacc-5.0/javacc.jar" javacc ../gen/n2a.jj
