@echo off
setlocal
set "BIN_DIR=%~dp0"
for %%I in ("%BIN_DIR%..") do set "APP_HOME=%%~fI"
if not defined AI4J_JAVA set "AI4J_JAVA=java"
if not defined AI4J_CLI_JAR set "AI4J_CLI_JAR=%APP_HOME%\lib\ai4j-cli-@project.version@-jar-with-dependencies.jar"

if not exist "%AI4J_CLI_JAR%" (
  echo ai4j launcher: missing "%AI4J_CLI_JAR%" 1>&2
  exit /b 1
)

if defined AI4J_JAVA_OPTS (
  %AI4J_JAVA% %AI4J_JAVA_OPTS% -jar "%AI4J_CLI_JAR%" %*
  set "EXIT_CODE=%ERRORLEVEL%"
) else (
  %AI4J_JAVA% -jar "%AI4J_CLI_JAR%" %*
  set "EXIT_CODE=%ERRORLEVEL%"
)
endlocal & exit /b %EXIT_CODE%
