@echo off
if "%2"=="" echo usage: %~nx0 ^<file to send^> ^<url.conf^>&exit

set "curl=C:\Program Files (x86)\Git\mingw32\bin\curl.exe"
set "curl=curl.exe"
set "curl=.\git\curl.exe"

for /f %%i in (%2) do set "url=%%i"&&goto :run_curl

:run_curl
"%curl%" --request POST --data-binary @%1 "%url%"

:: Workaround fix:
::"%curl%" --request POST --data-binary @%1 "%url%" --header "Expect:"
