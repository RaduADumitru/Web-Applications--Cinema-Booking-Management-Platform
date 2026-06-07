#!/bin/sh
cat > /app/public/runtime-config.json << EOF
{
  "apiUrl": "${API_URL:-http://localhost:8080/api/v1}"
}
EOF
exec npm start -- --host 0.0.0.0 --poll 2000
