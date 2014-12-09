@echo off
setlocal enabledelayedexpansion
set jars=.
for %%f in (lib/*.*) do (
set jars=!jars!;lib\%%f
)
echo %jars%
javac -cp %jars% main/QosBatch.java
echo compiler finished
pause