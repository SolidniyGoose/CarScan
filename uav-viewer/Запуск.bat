@echo off
chcp 65001 >nul
title UAV Viewer
cd /d "%~dp0"
set "HOST=127.0.0.1"
if not defined PORT set "PORT=3000"
if not defined UAV_OPEN set "UAV_OPEN=1"
"runtime\node.exe" --disable-warning=ExperimentalWarning "app\server.mjs"
if errorlevel 1 pause
