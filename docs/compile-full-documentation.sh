#!/bin/bash
# CryptoTrader Documentation Compilation Script
# Compiles all documentation files into a single comprehensive PDF

# Change to script directory
cd "$(dirname "$0")"

echo "========================================"
echo "CryptoTrader Documentation Compiler"
echo "========================================"
echo ""
echo "This script will compile all documentation files into a comprehensive PDF."
echo ""

# Check if Pandoc is installed
if ! command -v pandoc &> /dev/null; then
    echo "ERROR: Pandoc is not installed or not in PATH!"
    echo ""
    echo "Please install Pandoc:"
    echo "  Mac: brew install pandoc"
    echo "  Linux: sudo apt-get install pandoc"
    echo ""
    exit 1
fi

echo "[1/4] Pandoc detected: OK"
echo ""

# Check if XeLaTeX is installed
if ! command -v xelatex &> /dev/null; then
    echo "WARNING: XeLaTeX not found! PDF generation may fail."
    echo ""
    echo "Please install LaTeX:"
    echo "  Mac: brew install --cask mactex"
    echo "  Linux: sudo apt-get install texlive-full"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "[2/4] XeLaTeX detected: OK"
echo ""

# Create output directory if it doesn't exist
mkdir -p output

echo "[3/4] Compiling documentation files..."
echo ""
echo "Including:"
echo "- CryptoTrader_Complete_System_Documentation.md"
echo "- ../CODE_AUDIT_REPORT.md"
echo "- ../SYSTEM_DOCUMENTATION.md"
echo "- ../BUILD_INSTRUCTIONS.md"
echo "- ../PROJECT_STRUCTURE.md"
echo "- phase2/PHASE2_IMPLEMENTATION_GUIDE.md"
echo "- phase2/INDICATOR_SYSTEM_ARCHITECTURE.md"
echo "- phase2/DEVELOPER_GUIDE_INDICATORS.md"
echo "- phase2/API_REFERENCE.md"
echo "- ../roadmap.md"
echo ""

# Compile all documentation into single PDF
pandoc \
  CryptoTrader_Complete_System_Documentation.md \
  ../CODE_AUDIT_REPORT.md \
  ../SYSTEM_DOCUMENTATION.md \
  ../BUILD_INSTRUCTIONS.md \
  ../PROJECT_STRUCTURE.md \
  phase2/PHASE2_IMPLEMENTATION_GUIDE.md \
  phase2/INDICATOR_SYSTEM_ARCHITECTURE.md \
  phase2/DEVELOPER_GUIDE_INDICATORS.md \
  phase2/API_REFERENCE.md \
  ../roadmap.md \
  -o output/CryptoTrader_System_Documentation_FULL.pdf \
  --pdf-engine=xelatex \
  --toc \
  --toc-depth=3 \
  --number-sections \
  --highlight-style=kate \
  -V geometry:margin=1in \
  -V fontsize=11pt \
  -V documentclass=report \
  -V colorlinks=true \
  -V linkcolor=blue \
  -V urlcolor=blue \
  -V title="CryptoTrader - Complete System Documentation" \
  -V subtitle="AI-Powered Cryptocurrency Trading Platform - Developer and User Manual" \
  -V author="CryptoTrader Development Team" \
  -V date="November 16, 2025" \
  -V version="1.0"

if [ $? -eq 0 ]; then
    echo ""
    echo "[4/4] SUCCESS! PDF generated successfully!"
    echo ""
    echo "========================================"
    echo "Output File: output/CryptoTrader_System_Documentation_FULL.pdf"
    echo "========================================"
    echo ""

    # Get file size
    if [ -f "output/CryptoTrader_System_Documentation_FULL.pdf" ]; then
        filesize=$(du -h "output/CryptoTrader_System_Documentation_FULL.pdf" | cut -f1)
        pagecount=$(pdfinfo "output/CryptoTrader_System_Documentation_FULL.pdf" 2>/dev/null | grep "Pages:" | awk '{print $2}')

        echo "File size: $filesize"
        if [ ! -z "$pagecount" ]; then
            echo "Page count: $pagecount pages"
        fi
    fi

    # Open output folder
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open output
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        xdg-open output 2>/dev/null || echo "Output folder: $(pwd)/output"
    fi
else
    echo ""
    echo "[4/4] ERROR: PDF generation failed!"
    echo ""
    echo "Check the error messages above for details."
    echo "Common issues:"
    echo "- Missing LaTeX packages (install texlive-full)"
    echo "- File paths incorrect"
    echo "- Unicode characters not supported (XeLaTeX should handle this)"
    echo ""
    exit 1
fi

echo ""
