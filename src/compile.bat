@echo off

if exist "%JAVA_HOME%" goto :SetTibcoVars
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
if exist "%JAVA_HOME%" goto :SetTibcoVars
set JAVA_HOME=C:\BUILD_ENV\JDK\1.8
if exist "%JAVA_HOME%" goto :SetTibcoVars
echo Incorrect %%JAVA_HOME%%
exit

:SetTibcoVars
set TIBEMS_JAVA=.\tibco_jms
call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\jms-2.0.jar
call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\tibjms.jar
::call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\tibjmsufo.jar
::call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\tibjmsadmin.jar
::call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\tibcrypt.jar
::call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\slf4j-api-1.4.2.jar
::call :if_exist_add_to_CLASSPATH %TIBEMS_JAVA%\slf4j-simple-1.4.2.jar


set JAVA_PROJECT=hjbServer
:: Java1.7 compatible
"%JAVA_HOME%\bin\javac.exe" -source 7 -target 7 -Xlint:-deprecation -cp "%CLASSPATH%" *.java
:: Current Java1.8 compatible
::"%JAVA_HOME%\bin\javac.exe" -Xlint:-deprecation -cp "%CLASSPATH%" *.java

copy nul %TEMP%\MANIFEST.MF > nul
::echo Manifest-Version: 1.0>>%TEMP%\MANIFEST.MF
::echo Created-By: Me>>%TEMP%\MANIFEST.MF
echo Main-Class: %JAVA_PROJECT%>>%TEMP%\MANIFEST.MF
echo Class-Path: %JAR_CLASSPATH%>>%TEMP%\MANIFEST.MF
"%JAVA_HOME%\bin\jar.exe" cvfm %JAVA_PROJECT%.jar %TEMP%\MANIFEST.MF *.class > nul
del %TEMP%\MANIFEST.MF
::"%JAVA_HOME%\bin\jar.exe" cvfe %JAVA_PROJECT%.jar %JAVA_PROJECT% *.class > nul

del .\*.class
::start /MIN "" "%JAVA_HOME%\bin\java.exe" -jar %JAVA_PROJECT%.jar

goto :eof
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:if_exist_add_to_CLASSPATH
if exist "%~1" goto :update_CLASSPATH
echo File '%~1' not exists
goto :eof
:update_CLASSPATH
set CLASSPATH=%~1;%CLASSPATH%
set JAR_CLASSPATH=%~1 %JAR_CLASSPATH%
goto :eof
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
