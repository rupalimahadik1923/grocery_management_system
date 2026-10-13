@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if exist "C:\Users\LENOVO\AppData\Local\Programs\apache-maven-3.9.6\bin\mvn.cmd" (
    call "C:\Users\LENOVO\AppData\Local\Programs\apache-maven-3.9.6\bin\mvn.cmd" %*
) else (
    call mvn %*
)
