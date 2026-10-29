@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=C:\Program Files\Java\jdk-17\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
cd /d "C:\Users\ARJUN\OneDrive\Desktop\project\pce_project"
call gradlew.bat assembleDebug --no-daemon
pause

