#!/usr/bin/env bash
set -euo pipefail

# Script para incrementar el último número (patch) de la versión en build.gradle.kts
# Ubicación esperada del script: scripts/update_version.sh
# Uso: ./scripts/update_version.sh

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_FILE="$ROOT_DIR/build.gradle.kts"

if [[ ! -f "$TARGET_FILE" ]]; then
  echo "Error: No se encontró el archivo $TARGET_FILE" >&2
  exit 1
fi

# Extraer versión actual: busca una línea con el patrón version = "X.Y.Z" usando sed (compatible macOS/Linux)
current_version="$(LC_ALL=C sed -n -E 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([0-9]+)\.([0-9]+)\.([0-9]+)".*/\1.\2.\3/p' "$TARGET_FILE" | head -n1)"

if [[ -z "${current_version:-}" ]]; then
  echo 'Error: No se encontró una línea con el patrón: version = "X.Y.Z" en '"$TARGET_FILE" >&2
  exit 2
fi

IFS='.' read -r major minor patch <<< "$current_version"
# Sumar 1 al patch de forma segura (evita octales si hay ceros a la izquierda)
patch=$((10#$patch + 1))
new_version="${major}.${minor}.${patch}"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

# Reemplazar únicamente la línea de versión, preservando indentación (awk POSIX)
awk -v newv="$new_version" '
  {
    if ($0 ~ /^[[:space:]]*version[[:space:]]*=[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"[[:space:]]*$/) {
      # Reemplaza solo la parte numérica dentro de las comillas
      sub(/"[0-9]+\.[0-9]+\.[0-9]+"/, "\"" newv "\"")
      print
    } else {
      print
    }
  }
' "$TARGET_FILE" > "$tmp_file"

# Sustituir el archivo original por el actualizado
mv "$tmp_file" "$TARGET_FILE"
trap - EXIT

echo "Versión actualizada: ${current_version} -> ${new_version}"
