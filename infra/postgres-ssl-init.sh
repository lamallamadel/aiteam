#!/bin/bash
set -e

echo "Configuring PostgreSQL SSL certificates..."

if [ -f /var/lib/postgresql/certs/server.key ]; then
    chown postgres:postgres /var/lib/postgresql/certs/server.key
    chmod 600 /var/lib/postgresql/certs/server.key
    echo "SSL certificates configured successfully"
else
    echo "WARNING: SSL certificates not found at /var/lib/postgresql/certs/"
    echo "Run infra/gen-ssl-cert.sh to generate certificates"
fi
