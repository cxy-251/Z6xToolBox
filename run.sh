#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="/home/deck/Applications/dotnet:$PATH"
exec dotnet run --project "$SCRIPT_DIR/Z6xToolBox.Desktop/Z6xToolBox.Desktop.csproj" "$@"
