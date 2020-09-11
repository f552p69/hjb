@echo off
set "curl=C:\Program Files (x86)\Git\mingw32\bin\curl.exe"
set "curl=curl.exe"
set "curl=.\git\curl.exe"

"%curl%" --request GET --header "Terminate: Mission impossible" http://localhost:9999