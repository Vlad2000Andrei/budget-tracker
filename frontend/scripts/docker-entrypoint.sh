#!/bin/sh
set -e

# Provide defaults if not specified via environment
export FRONTEND_PORT=${FRONTEND_PORT:-80}
export BACKEND_URL=${BACKEND_URL:-http://127.0.0.1:19092}

# Print configuration info for diagnostic purposes
echo "Starting Nginx frontend..."
echo "Listening on port: ${FRONTEND_PORT}"
echo "Proxying /v1 to backend: ${BACKEND_URL}"

# Perform environment substitution on the template file
envsubst '${FRONTEND_PORT} ${BACKEND_URL}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

# Execute CMD (which is Nginx)
exec "$@"
