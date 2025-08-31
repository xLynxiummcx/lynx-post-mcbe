#!/bin/bash
set -e

echo "[1/4] Updating package lists..."
if command -v apt >/dev/null 2>&1; then
    sudo apt update -y
elif command -v pkg >/dev/null 2>&1; then
    pkg update -y
fi

echo "[2/4] Installing Python 3 and pip..."
if command -v apt >/dev/null 2>&1; then
    sudo apt install -y python3 python3-pip curl
elif command -v pkg >/dev/null 2>&1; then
    pkg install -y python curl
fi

echo "[3/4] Installing lazurite..."
python3 -m pip install --upgrade pip
python3 -m pip install lazurite

echo "[4/4] Downloading shaderc-win-x64.exe..."
curl -L -o shaderc-win-x64.exe \
    https://github.com/devendrn/newb-shader/releases/download/dev/shaderc-win-x64.exe

echo "Setup complete!"