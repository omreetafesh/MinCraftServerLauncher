@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
title Minecraft Server Launcher - Build

echo.
echo  =============================================
echo   Minecraft Server Launcher - Build Tool
echo  =============================================
echo.

:: =============================================================
::  CONFIGURATION  (edit if auto-detection fails)
:: =============================================================
set "APP_NAME=Minecraft Server Launcher"
set "APP_VERSION=1.0.0"
set "APP_VENDOR=Omree"

:: Leave blank to auto-detect
set "JAVA_HOME_OVERRIDE="
set "JAVAFX_HOME_OVERRIDE="
:: =============================================================

:: -- Apply manual overrides ----------------------------------
if not "%JAVA_HOME_OVERRIDE%"=="" set "JAVA_HOME=%JAVA_HOME_OVERRIDE%"
if not "%JAVAFX_HOME_OVERRIDE%"=="" goto :use_fx_override

:: -- Auto-detect JAVA_HOME -----------------------------------
if defined JAVA_HOME goto :check_jpackage

for %%J in (
    "C:\Program Files\Java\jdk-26.0.1"
    "C:\Program Files\Java\jdk-21.0.2"
    "C:\Program Files\Java\jdk-21"
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot"
    "C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"
) do (
    if not defined JAVA_HOME (
        if exist "%%~J\bin\jpackage.exe" set "JAVA_HOME=%%~J"
    )
)

:check_jpackage
if not defined JAVA_HOME goto :java_err
if not exist "%JAVA_HOME%\bin\jpackage.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"
if not exist "%JAVA_HOME%\bin\jpackage.exe" goto :java_err
goto :java_ok

:java_err
echo [ERR] Full JDK with jpackage.exe not found.
echo  Set JAVA_HOME_OVERRIDE at the top of this script.
pause & exit /b 1

:java_ok
echo [OK] JDK: %JAVA_HOME%

:: -- Auto-detect JavaFX SDK ----------------------------------
set "FX="

for %%F in (
    "%ROOT%javafx-sdk-26.0.1\lib"
    "%ROOT%javafx-sdk-26\lib"
    "%USERPROFILE%\Desktop\3Assignment_7G\PROJECT\GoNatureClient\javafx-sdk-26.0.1\lib"
    "%USERPROFILE%\Desktop\GoNature\v1.0\GoNatureClient\javafx-sdk-26.0.1\lib"
    "C:\javafx-sdk-26.0.1\lib"
    "C:\javafx-sdk-26\lib"
    "C:\javafx-sdk-21\lib"
    "%USERPROFILE%\javafx-sdk-26\lib"
    "%USERPROFILE%\Downloads\javafx-sdk-26.0.1\lib"
    "C:\Program Files\javafx-sdk-26\lib"
) do (
    if not defined FX (
        if exist "%%~F\javafx.controls.jar" set "FX=%%~F"
    )
)

goto :check_fx

:use_fx_override
set "FX=%JAVAFX_HOME_OVERRIDE%\lib"

:check_fx
if not defined FX goto :fx_err
if not exist "%FX%\javafx.controls.jar" goto :fx_err
goto :fx_ok

:fx_err
echo [ERR] JavaFX SDK not found.
echo  Set JAVAFX_HOME_OVERRIDE at the top of this script.
echo  Example:  set JAVAFX_HOME_OVERRIDE=C:\javafx-sdk-26
pause & exit /b 1

:fx_ok
echo [OK] JavaFX: %FX%

:: -- Path setup ----------------------------------------------
set "SRC=%ROOT%src"
set "OUT=%ROOT%out"
set "DIST=%ROOT%dist"
set "TOOLS=%ROOT%tools"

:: -- Clean ---------------------------------------------------
echo.
echo Cleaning previous build...
if exist "%OUT%"  rmdir /s /q "%OUT%"
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%OUT%\MinCraftServerLauncher"
mkdir "%DIST%"
if not exist "%TOOLS%\bin" mkdir "%TOOLS%\bin"

:: -- Step 0: Generate icon -----------------------------------
echo.
echo [0/4] Generating icon.ico...
set "ICO_FLAG="

"%JAVA_HOME%\bin\javac" -d "%TOOLS%\bin" "%TOOLS%\MakeIcon.java" >nul 2>&1
if errorlevel 1 goto :skip_icon

"%JAVA_HOME%\bin\java" -cp "%TOOLS%\bin" MakeIcon "%DIST%\icon.ico" >nul 2>&1
if errorlevel 1 goto :skip_icon

set "ICO_FLAG=--icon "%DIST%\icon.ico""
echo [OK] icon.ico ready.
goto :compile

:skip_icon
echo [WARN] Icon generation failed - using default icon.

:: -- Step 1: Compile -----------------------------------------
:compile
echo.
echo [1/4] Compiling Java sources...

"%JAVA_HOME%\bin\javac" --module-path "%FX%" -d "%OUT%\MinCraftServerLauncher" ^
    "%SRC%\module-info.java" ^
    "%SRC%\main\AppStarter.java" ^
    "%SRC%\main\LauncherController.java" ^
    "%SRC%\main\Main.java" ^
    "%SRC%\main\RconClient.java"

if errorlevel 1 (
    echo [ERR] Compilation failed.
    pause & exit /b 1
)
echo [OK] Compiled.

:: -- Step 2: Build modular JAR -------------------------------
echo.
echo [2/4] Packaging JAR...

for %%f in ("%SRC%\main\*.fxml" "%SRC%\main\*.css") do (
    if exist "%%f" copy /y "%%f" "%OUT%\MinCraftServerLauncher\main\" >nul
)

"%JAVA_HOME%\bin\jar" --create --file="%DIST%\app.jar" -C "%OUT%\MinCraftServerLauncher" .

if errorlevel 1 (
    echo [ERR] JAR creation failed.
    pause & exit /b 1
)
echo [OK] app.jar created.

:: -- Step 3: jpackage ----------------------------------------
echo.
echo [3/4] Packaging app...

set "MODULE_PATH=%DIST%\app.jar;%FX%;%JAVA_HOME%\jmods"

"%JAVA_HOME%\bin\jpackage" ^
    --type exe ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --description "Minecraft Server Dashboard" ^
    --vendor "%APP_VENDOR%" ^
    --module-path "%MODULE_PATH%" ^
    --module "MinCraftServerLauncher/main.Main" ^
    --dest "%DIST%\output" ^
    %ICO_FLAG% ^
    --win-menu ^
    --win-menu-group "Minecraft Tools" ^
    --win-shortcut ^
    --win-dir-chooser ^
    --win-per-user-install

if not errorlevel 1 goto :success

:: -- Fallback: app-image (no WiX needed) ---------------------
echo.
echo [INFO] .exe installer needs WiX Toolset 3.x - falling back to app-image...
echo.

"%JAVA_HOME%\bin\jpackage" ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --module-path "%MODULE_PATH%" ^
    --module "MinCraftServerLauncher/main.Main" ^
    --dest "%DIST%\output" ^
    %ICO_FLAG%

if errorlevel 1 (
    echo [ERR] jpackage failed.
    pause & exit /b 1
)

:: -- Step 4: Copy JavaFX native DLLs -------------------------
echo.
echo [4/4] Copying JavaFX native libraries...
set "FX_BIN=%FX%\..\bin"
set "RT_BIN=%DIST%\output\%APP_NAME%\runtime\bin"
xcopy /q /y "%FX_BIN%\*.dll" "%RT_BIN%\" >nul
echo [OK] Native libraries copied.

:: -- Step 5: Create installer with Inno Setup (if installed) --
set "ISCC="
for %%I in (
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
    "C:\Program Files\Inno Setup 6\ISCC.exe"
    "C:\Program Files (x86)\Inno Setup 5\ISCC.exe"
) do (
    if not defined ISCC (
        if exist "%%~I" set "ISCC=%%~I"
    )
)

if defined ISCC (
    echo.
    echo [5/5] Creating installer...
    "%ISCC%" "%ROOT%installer.iss" /Q
    if not errorlevel 1 (
        echo [OK] Installer: %DIST%\MinecraftServerLauncher-Setup.exe
    ) else (
        echo [WARN] Inno Setup failed - portable folder is still available.
    )
) else (
    echo.
    echo [INFO] Inno Setup not found - skipping installer.
    echo  Install from: https://jrsoftware.org/isinfo.php
)

echo.
echo  =============================================
echo   BUILD COMPLETE
echo.
echo   Portable: %DIST%\output\%APP_NAME%\%APP_NAME%.exe
if defined ISCC echo   Installer: %DIST%\MinecraftServerLauncher-Setup.exe
echo  =============================================
pause
exit /b 0

:success
echo.
echo  =============================================
echo   BUILD COMPLETE
echo.
echo   Installer: %DIST%\output\
echo  =============================================
pause
exit /b 0
