#!/usr/bin/env bash
# Genera los PDF de la entrega (Edge headless vía PowerShell; en Git Bash Edge no espera al imprimir).
DIR="$(cd "$(dirname "$0")" && pwd)"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$DIR/build_pdfs.ps1")"
