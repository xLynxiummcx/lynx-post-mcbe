#!/bin/sh

# Exit immediately if a command exits with a non-zero status
set -e

# Run Lazurite build on ./src
lazurite build ./src -p windows

echo " Build finished successfully."