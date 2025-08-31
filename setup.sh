#!/bin/bash
set -e

echo "[1/3] Updating package lists..."
sudo apt update -y

echo "[2/3] Installing Python 3 and pip..."
sudo apt install -y python3 python3-pip curl

echo "[3/3] Installing lazurite 0.4.2..."
python3 -m pip install --upgrade pip
python3 -m pip install lazurite==0.4.2

echo "[4/4] Downloading shaderc for Linux..."
curl -L -o shaderc \
    https://github.com/devendrn/newb-shader/releases/download/dev/shaderc-linux-x64

chmod +x shaderc

echo " Setup complete!"