
@echo off

Rem IMPORTANT: Assumes the working directory is the project root.

SET PROJ_ROOT=%~dp0

Rem Generate
cd "%PROJ_ROOT%src\gov\sandia\n2a\language\parse"
java -classpath "%PROJ_ROOT%lib\javacc\javacc-7.0.5.jar" jjtree grammar.jjt
java -classpath "%PROJ_ROOT%lib\javacc\javacc-7.0.5.jar" javacc grammar.jj

cd "%PROJ_ROOT%"
