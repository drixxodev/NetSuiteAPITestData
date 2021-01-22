@echo off
setlocal

set count=0
for %%f in (%~dp0\lib\*) do set /a count+=1

IF %count% LSS 12 (
    echo Project is incomplete. Please run 'gradle deploy' for rebuilding it.
) ELSE (
    java -jar %~dp0\lib\NSLoader.jar %*
)
