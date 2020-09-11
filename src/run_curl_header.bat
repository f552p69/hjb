::@echo off
if "%2"=="" echo usage: %~nx0 ^<file to send^> ^<header.conf^>&exit

set "curl=C:\Program Files (x86)\Git\mingw32\bin\curl.exe"
set "curl=curl.exe"
set "curl=.\git\curl.exe"

:run_curl
"%curl%" --request POST --header @%2 --data-binary @%1 http://localhost:9999
