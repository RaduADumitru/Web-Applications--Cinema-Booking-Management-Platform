#!/bin/sh
set -e

# The compose setup bind-mounts ./client over /app and keeps node_modules in an
# anonymous volume. That volume persists across runs, so when dependencies change
# the image's fresh node_modules gets shadowed by a stale volume.
# Reconcile node_modules with the lockfile on startup to self-heal.
if [ ! -f /app/node_modules/.package-lock.json ] || \
   [ /app/package-lock.json -nt /app/node_modules/.package-lock.json ]; then
  echo "Dependencies out of date — running npm ci..."
  npm ci
fi

cat > /app/public/runtime-config.json << EOF
{
  "apiUrl": "${API_URL:-http://localhost:8080/api/v1}"
}
EOF
exec npm start -- --host 0.0.0.0 --poll 2000
