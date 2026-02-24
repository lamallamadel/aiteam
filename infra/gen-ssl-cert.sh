#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}/certs"
KEYSTORE_FILE="${CERT_DIR}/keystore.p12"
SERVER_CERT="${CERT_DIR}/server.crt"
SERVER_KEY="${CERT_DIR}/server.key"
CA_CERT="${CERT_DIR}/ca.crt"
CA_KEY="${CERT_DIR}/ca.key"

KEYSTORE_PASSWORD="${SSL_KEYSTORE_PASSWORD:-changeit}"
VALIDITY_DAYS=365

echo "🔐 Generating SSL certificates for local development..."

mkdir -p "${CERT_DIR}"

echo "📝 Generating Certificate Authority (CA)..."
openssl genrsa -out "${CA_KEY}" 4096
openssl req -x509 -new -nodes -key "${CA_KEY}" -sha256 -days "${VALIDITY_DAYS}" \
  -out "${CA_CERT}" \
  -subj "/C=US/ST=Dev/L=Local/O=Atlasia/OU=Development/CN=Atlasia Dev CA"

echo "📝 Generating server private key..."
openssl genrsa -out "${SERVER_KEY}" 2048

echo "📝 Creating certificate signing request..."
openssl req -new -key "${SERVER_KEY}" -out "${CERT_DIR}/server.csr" \
  -subj "/C=US/ST=Dev/L=Local/O=Atlasia/OU=Development/CN=localhost"

cat > "${CERT_DIR}/server.ext" << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = ai-orchestrator
DNS.3 = ai-db
IP.1 = 127.0.0.1
IP.2 = ::1
EOF

echo "📝 Signing server certificate with CA..."
openssl x509 -req -in "${CERT_DIR}/server.csr" -CA "${CA_CERT}" -CAkey "${CA_KEY}" \
  -CAcreateserial -out "${SERVER_CERT}" -days "${VALIDITY_DAYS}" -sha256 \
  -extfile "${CERT_DIR}/server.ext"

echo "📦 Creating PKCS12 keystore for Spring Boot..."
openssl pkcs12 -export -in "${SERVER_CERT}" -inkey "${SERVER_KEY}" \
  -out "${KEYSTORE_FILE}" -name "atlasia-server" \
  -password "pass:${KEYSTORE_PASSWORD}"

echo "📦 Creating PostgreSQL server certificate bundle..."
cat "${SERVER_CERT}" "${CA_CERT}" > "${CERT_DIR}/server-bundle.crt"

chmod 600 "${SERVER_KEY}" "${CA_KEY}" "${KEYSTORE_FILE}"
chmod 644 "${SERVER_CERT}" "${CA_CERT}" "${CERT_DIR}/server-bundle.crt"

echo ""
echo "✅ SSL certificates generated successfully!"
echo ""
echo "📁 Certificate files:"
echo "  - CA Certificate:       ${CA_CERT}"
echo "  - Server Certificate:   ${SERVER_CERT}"
echo "  - Server Key:           ${SERVER_KEY}"
echo "  - PKCS12 Keystore:      ${KEYSTORE_FILE}"
echo ""
echo "🔧 Configuration:"
echo "  - Keystore Password:    ${KEYSTORE_PASSWORD}"
echo "  - Validity:             ${VALIDITY_DAYS} days"
echo ""
echo "⚙️  To use with Spring Boot, set:"
echo "  export SSL_ENABLED=true"
echo "  export SSL_KEYSTORE_PATH=file:${KEYSTORE_FILE}"
echo "  export SSL_KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD}"
echo ""
echo "⚙️  To use with PostgreSQL, update docker-compose with:"
echo "  - Mount ${CERT_DIR} to /var/lib/postgresql/certs"
echo "  - Set SSL certificate paths in postgresql.conf"
echo ""
echo "⚠️  Note: These are self-signed certificates for development only."
echo "    Do NOT use in production!"

rm -f "${CERT_DIR}/server.csr" "${CERT_DIR}/server.ext" "${CERT_DIR}/ca.srl"
