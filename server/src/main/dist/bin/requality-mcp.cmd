@echo off
rem Requality MCP server: Requality without a window, for AI agents (Model Context Protocol on standard input/output).
rem
rem Usage: requality-mcp [options] [<folder with Requality catalogs>...]   (default folder: the current one;
rem requality-mcp --help lists the options)
rem
rem Java: %REQUALITY_JAVA%, else the Java runtime of the package (jre\, or the one of the VS Code extension), else
rem %JAVA_HOME%, else java on the PATH (Java 17 or later). %REQUALITY_JAVA_OPTS% replaces the default -Xmx2g.
setlocal
rem The Java launcher of Windows reads its arguments in the code page of the system: a folder name in other letters
rem (for example Cyrillic letters on an English Windows) would not reach Java. The paths of the package go to Java
rem relative to its folder (the short 8.3 name of the folder when the volume has one); the current folder, where
rem relative arguments start, goes in REQUALITY_CWD.
for %%I in ("%~dp0..") do set "RQ_HOME=%%~fsI"
set "REQUALITY_CWD=%CD%"
set "RQ_JAVA=%REQUALITY_JAVA%"
if not "%RQ_JAVA%"=="" goto run
set "RQ_JAVA=%RQ_HOME%\jre\bin\java.exe"
if exist "%RQ_JAVA%" goto run
rem dist\server of the VS Code extension: the runtime is jre\ of the extension
set "RQ_JAVA=%RQ_HOME%\..\..\jre\bin\java.exe"
if exist "%RQ_JAVA%" goto run
set "RQ_JAVA=java"
if "%JAVA_HOME%"=="" goto run
if exist "%JAVA_HOME%\bin\java.exe" set "RQ_JAVA=%JAVA_HOME%\bin\java.exe"
:run
set "RQ_OPTS=%REQUALITY_JAVA_OPTS%"
if "%RQ_OPTS%"=="" set "RQ_OPTS=-Xmx2g"
pushd "%RQ_HOME%"
"%RQ_JAVA%" %RQ_OPTS% -Dfile.encoding=UTF-8 --add-opens java.base/java.lang=ALL-UNNAMED -cp "lib\*" com.unitesk.requality.vscode.mcp.McpMain --resources resources %*
set "RQ_EXIT=%ERRORLEVEL%"
popd
exit /b %RQ_EXIT%
