#!/usr/bin/env python3
"""Local-only synthetic HTTPS fixture. Never contacts a ride-hailing provider."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import ssl
import argparse

RESPONSES = {
    "offer_lookup": ("offer_observed", {
        "offer_id": "synthetic-ride-001", "currency": "PLN",
        "gross_amount": 24.50, "pickup_distance_km": 1.8,
        "estimated_trip_distance_km": 6.2, "expires_in_seconds": 20
    }),
    "offer_update": ("offer_updated", {
        "offer_id": "synthetic-ride-001", "currency": "PLN",
        "gross_amount": 26.00, "pickup_distance_km": 1.5,
        "estimated_trip_distance_km": 6.2
    }),
    "offer_expired": ("offer_expired", {
        "offer_id": "synthetic-ride-001"
    }),
}

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/event":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size < 1 or size > 16_384:
                self.send_error(413)
                return
            request = json.loads(self.rfile.read(size))
            event_type = request.get("event_type")
            response_type, fields = RESPONSES[event_type]
            body = {
                "event_type": response_type,
                "correlation_id": request.get("correlation_id"),
                "sequence": request.get("sequence"),
                "event_timestamp": "2026-11-15T12:00:00Z",
                **fields,
                # Deliberately synthetic secret: observer must redact it.
                "synthetic_secret": "SERVER_SIDE_SECRET_DO_NOT_LOG",
            }
            encoded = json.dumps(body, separators=(",", ":")).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
        except (ValueError, KeyError, json.JSONDecodeError):
            self.send_error(400, "invalid synthetic event")

    def log_message(self, *_):
        # Do not log request bodies or headers.
        return

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8443)
    args = parser.parse_args()
    server = HTTPServer((args.host, args.port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(args.cert, args.key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print(f"Owned synthetic HTTPS fixture listening on {args.host}:{args.port}")
    server.serve_forever()

if __name__ == "__main__":
    main()
