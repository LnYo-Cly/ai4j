@echo off
rem
rem ai4j -- launcher for the ai4j CLI fat jar (Windows).
rem
rem Responsibilities (see docs-site coding-agent/install-and-release section 8):
rem locate java, locate the fat jar, forward all arguments, keep the stable
rem command name `ai4j`. Does NOT hardcode provider/model/secrets or parse
rem configuration.
rem
rem Optional overrides:
rem   AI4J_JAR        explicit path to ai4j-cli-*-jar-with-dependencies.jar
rem   JAVA_HOME       JRE/JDK home (otherwise `java` from PATH)
rem   AI4J_JAVA_OPTS  extra JVM flags (heap, system properties, ...)
setlocal enabledelayedexpansion

set "BIN_DIR=%~dp0"
if "%BIN_DIR:~-1%"=="\" set "BIN_DIR=%BIN_DIR:~0,-1%"

rem Locate the fat jar: explicit override, then ..\lib\, then alongside the launcher.
set "JAR=%AI4J_JAR%"
if defined JAR goto run
for %%F in ("%BIN_DIR%\..\lib\ai4j-cli-*-jar-with-dependencies.jar") do if exist "%%F" ( set "JAR=%%F" & goto run )
for %%F in ("%BIN_DIR%\ai4j-cli-*-jar-with-dependencies.jar") do if exist "%%F" ( set "JAR=%%F" & goto run )
:run
if not defined JAR (
  echo ai4j: cannot find ai4j-cli fat jar. Set AI4J_JAR or place 1>&2
  echo        ai4j-cli-*-jar-with-dependencies.jar next to this launcher or in ..\lib\. 1>&2
  exit /b 1
)
if not exist "%JAR%" (
  echo ai4j: jar not found: %JAR% 1>&2
  exit /b 1
)

rem Locate java.
set "JAVACMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVACMD=%JAVA_HOME%\bin\java.exe"

"%JAVACMD%" %AI4J_JAVA_OPTS% %JAVA_OPTS% -jar "%JAR%" %*
endlocal
