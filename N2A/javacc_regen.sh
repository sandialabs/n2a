#!/bin/bash

export PROJ_ROOT=`pwd`

cd "$PROJ_ROOT/src/gov/sandia/n2a/language/parse"
java -classpath "$PROJ_ROOT/lib/javacc/javacc-7.0.5.jar" jjtree grammar.jjt
java -classpath "$PROJ_ROOT/lib/javacc/javacc-7.0.5.jar" javacc grammar.jj
