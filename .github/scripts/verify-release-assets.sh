#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <release-assets-directory> <version>" >&2
  exit 2
fi

assets_directory="$(cd "$1" && pwd)"
version="$2"
python_command=python3
[[ -z "${MSYSTEM:-}" ]] || python_command=python
"$python_command" .github/scripts/verify-release-assets.py "$assets_directory" "$version"

(
  cd "$assets_directory"
  sha256sum --check --strict SHA256SUMS
  sha512sum --check --strict SHA512SUMS
)

mapfile -t signed_assets < <(
  find "$assets_directory" -maxdepth 1 -type f \
    \( -name '*.jar' -o -name '*.cdx.json' -o -name 'SHA256SUMS' -o -name 'SHA512SUMS' \) \
    -printf '%f\n' | sort
)
if [[ ${#signed_assets[@]} -ne 7 ]]; then
  echo "Expected seven signed assets, found ${#signed_assets[@]}." >&2
  exit 1
fi
for asset in "${signed_assets[@]}"; do
  signature="$assets_directory/${asset}.asc"
  if [[ ! -f "$signature" ]]; then
    echo "Missing detached signature: ${asset}.asc" >&2
    exit 1
  fi
  status="$(gpg --batch --status-fd 1 --verify "$signature" "$assets_directory/$asset")"
  if [[ -n "${RELEASE_SIGNING_FINGERPRINT:-}" ]] && \
      ! awk -v expected="$RELEASE_SIGNING_FINGERPRINT" \
        '$2 == "VALIDSIG" && ($3 == expected || $12 == expected) { valid = 1 } END { exit !valid }' <<< "$status"; then
    echo "Signature for $asset does not belong to the expected release identity." >&2
    exit 1
  fi
done
