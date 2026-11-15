// Frida script for the BusyBee-owned POC app only.
// Hooks only the two explicit boundary methods in our own package.
// It never enumerates processes, hooks TLS globally, or inspects unrelated traffic.
Java.perform(function () {
  const Boundary = Java.use('com.ysfclc.busybee.plaintextpoc.PlaintextBoundary');
  const outbound = Boundary.outbound.overload('java.lang.String');
  const inbound = Boundary.inbound.overload('java.lang.String');

  function safeSummary(raw) {
    try {
      const value = JSON.parse(String(raw));
      const allowed = [
        'event_type', 'correlation_id', 'sequence', 'synthetic_offer_id',
        'offer_id', 'currency', 'gross_amount', 'pickup_distance_km',
        'estimated_trip_distance_km', 'expires_in_seconds'
      ];
      const summary = {};
      allowed.forEach((key) => {
        if (Object.prototype.hasOwnProperty.call(value, key)) summary[key] = value[key];
      });
      summary.secret_fields_redacted =
        Object.keys(value).some((key) => /secret|token|cookie|authorization|password/i.test(key));
      return summary;
    } catch (_) {
      return { parse_error: true, payload_omitted: true };
    }
  }

  outbound.implementation = function (json) {
    send({ observer: 'busybee-owned-plaintext-poc', direction: 'outbound-pre-transport',
      boundary: 'PlaintextBoundary.outbound', summary: safeSummary(json) });
    return outbound.call(this, json);
  };

  inbound.implementation = function (json) {
    send({ observer: 'busybee-owned-plaintext-poc', direction: 'inbound-post-tls-decrypt',
      boundary: 'PlaintextBoundary.inbound', summary: safeSummary(json) });
    return inbound.call(this, json);
  };

  send({ observer: 'busybee-owned-plaintext-poc', status: 'attached',
    hooks: ['outbound-pre-transport', 'inbound-post-tls-decrypt'] });
});
