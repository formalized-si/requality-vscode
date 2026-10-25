@echo off
rem Requality MCP server: Requality without a window, for AI agents (Model Context Protocol on standard input/output).
rem
rem Usage: requality-mcp [options] [<folder with Requality catalogs>...]   (default folder: the current one;
rem requality-mcp --help lists the options)
rem
rem Java: %REQUALITY_JAVA%, else the Java runtime of the package (jre\, or the one of the VS Code extension), else
rem %JAVA_HOME%, else java on the PATH (Java 17 or later). %REQUALITY_JAVA_OPTS% replaces the default -Xmx2g.
setlocal
set "RQ_HOME=%~dp0.."
set "RQ_JAVA=%REQUALITY_JAVA%"
if not "%RQ_JAVA%"=="" goto run
set "RQ_JAVA=%RQ_HOME%\jre\bin\java.exe"
if exist "%RQ_JAVA%" goto run
rem dist\server of the VS Code extension: the runtime is jre\ of the extension
set "RQ_JAVA=%RQ_HOME%\..\..\jre\bin\java.exe"
if exist "%RQ_JAVA%" goto run
set "RQ_JAVA=java"
if "%JAVA_HOME%"=="" goto run
set "RQ_JAVA=%JAVA_HOME%\bin\java.exe"
:run
set "RQ_OPTS=%REQUALITY_JAVA_OPTS%"
if "%RQ_OPTS%"=="" set "RQ_OPTS=-Xmx2g"
"%RQ_JAVA%" %RQ_OPTS% -Dfile.encoding=UTF-8 --add-opens java.base/java.lang=ALL-UNNAMED -cp "%RQ_HOME%\lib\*" com.unitesk.requality.vscode.mcp.McpMain --resources "%RQ_HOME%\resources" %*
exit /b %ERRORLEVEL%
