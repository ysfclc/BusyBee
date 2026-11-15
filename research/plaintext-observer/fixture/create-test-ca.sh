#!/usr/bin/env sh
set -eu
OUT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/certs"
mkdir -p "$OUT_DIR"
umask 077
openssl genrsa -out "$OUT_DIR/test-ca.key" 3072
openssl req -x509 -new -nodes -key "$OUT_DIR/test-ca.key" -sha256 -days 30 \
  -subj "/CN=BusyBee Owned POC Test CA" -out "$OUT_DIR/test-ca.crt"
openssl genrsa -out "$OUT_DIR/localhost.key" 2048
openssl req -new -key "$OUT_DIR/localhost.key" -subj "/CN=localhost" \
  -out "$OUT_DIR/localhost.csr"
cat > "$OUT_DIR/localhost.ext" <<'EOF'
subjectAltName=DNS:localhost
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF
openssl x509 -req -in "$OUT_DIR/localhost.csr" -CA "$OUT_DIR/test-ca.crt" \
  -CAkey "$OUT_DIR/test-ca.key" -CAcreateserial -out "$OUT_DIR/localhost.crt" \
  -days 30 -sha256 -extfile "$OUT_DIR/localhost.ext"
rm -f "$OUT_DIR/localhost.csr" "$OUT_DIR/localhost.ext" "$OUT_DIR/test-ca.srl"
chmod 600 "$OUT_DIR/"*.key
printf 'Created local-only CA and localhost server certificate in %s\n' "$OUT_DIR"
printf 'Do not commit or share private keys. Install only test-ca.crt on the test emulator/device.\n'
