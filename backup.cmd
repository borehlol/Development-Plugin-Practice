@echo off
rem Double-click this or run it from a terminal. Windows blocks .ps1 files by default, so this launches it.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\backup.ps1" %*
rem Keeps the window open when double-clicked, so the result can be read.
echo %CMDCMDLINE% | find /i "/c" >nul && pause
