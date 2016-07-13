#!/bin/bash

export PROJ_ROOT=`pwd`

cd "$PROJ_ROOT/src/gov/sandia/n2a/language/parse"
java -classpath "$PROJ_ROOT/lib/javacc-6.0/javacc.jar" jjtree grammar.jjt
java -classpath "$PROJ_ROOT/lib/javacc-6.0/javacc.jar" javacc grammar.jj
