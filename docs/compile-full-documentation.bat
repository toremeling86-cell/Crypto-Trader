@echo off
REM CryptoTrader Documentation Compilation Script
REM Compiles all documentation files into a single comprehensive PDF

cd /d "%~dp0"

echo ========================================
echo CryptoTrader Documentation Compiler
echo ========================================
echo.
echo This script will compile all documentation files into a comprehensive PDF.
echo.

REM Check if Pandoc is installed
where pandoc >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Pandoc is not installed or not in PATH!
    echo.
    echo Please install Pandoc from: https://pandoc.org/installing.html
    echo.
    echo After installation, restart this script.
    pause
    exit /b 1
)

echo [1/4] Pandoc detected: OK
echo.

REM Check if XeLaTeX is installed (for PDF generation)
where xelatex >nul 2>nul
if %errorlevel% neq 0 (
    echo WARNING: XeLaTeX not found! PDF generation may fail.
    echo.
    echo Please install MiKTeX from: https://miktex.org/download
    echo.
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

echo [2/4] XeLaTeX detected: OK
echo.

REM Create output directory if it doesn't exist
if not exist "output" mkdir output

echo [3/4] Compiling documentation files...
echo.
echo Including:
echo - CryptoTrader_Complete_System_Documentation.md
echo - ../CODE_AUDIT_REPORT.md
echo - ../SYSTEM_DOCUMENTATION.md
echo - ../BUILD_INSTRUCTIONS.md
echo - ../PROJECT_STRUCTURE.md
echo - phase2/PHASE2_IMPLEMENTATION_GUIDE.md
echo - phase2/INDICATOR_SYSTEM_ARCHITECTURE.md
echo - phase2/DEVELOPER_GUIDE_INDICATORS.md
echo - phase2/API_REFERENCE.md
echo - ../roadmap.md
echo.

REM Compile all documentation into single PDF
pandoc ^
  CryptoTrader_Complete_System_Documentation.md ^
  ..\CODE_AUDIT_REPORT.md ^
  ..\SYSTEM_DOCUMENTATION.md ^
  ..\BUILD_INSTRUCTIONS.md ^
  ..\PROJECT_STRUCTURE.md ^
  phase2\PHASE2_IMPLEMENTATION_GUIDE.md ^
  phase2\INDICATOR_SYSTEM_ARCHITECTURE.md ^
  phase2\DEVELOPER_GUIDE_INDICATORS.md ^
  phase2\API_REFERENCE.md ^
  ..\roadmap.md ^
  -o output\CryptoTrader_System_Documentation_FULL.pdf ^
  --pdf-engine=xelatex ^
  --toc ^
  --toc-depth=3 ^
  --number-sections ^
  --highlight-style=kate ^
  -V geometry:margin=1in ^
  -V fontsize=11pt ^
  -V documentclass=report ^
  -V colorlinks=true ^
  -V linkcolor=blue ^
  -V urlcolor=blue ^
  -V title="CryptoTrader - Complete System Documentation" ^
  -V subtitle="AI-Powered Cryptocurrency Trading Platform - Developer and User Manual" ^
  -V author="CryptoTrader Development Team" ^
  -V date="November 16, 2025" ^
  -V version="1.0"

if %errorlevel% equ 0 (
    echo.
    echo [4/4] SUCCESS! PDF generated successfully!
    echo.
    echo ========================================
    echo Output File: output\CryptoTrader_System_Documentation_FULL.pdf
    echo ========================================
    echo.
    echo Opening output folder...
    explorer output
) else (
    echo.
    echo [4/4] ERROR: PDF generation failed!
    echo.
    echo Check the error messages above for details.
    echo Common issues:
    echo - Missing LaTeX packages (install MiKTeX)
    echo - File paths incorrect
    echo - Unicode characters not supported (try using XeLaTeX)
    echo.
)

echo.
pause
