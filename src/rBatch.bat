@echo off
setlocal enabledelayedexpansion
set jars=.
for %%f in (lib/*.*) do (
set jars=!jars!;lib\%%f
)
echo %jars%

java -cp %jars% main.QosBatch
pause