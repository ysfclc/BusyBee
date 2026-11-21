use serde::{Deserialize, Serialize};

pub mod observation;
pub use observation::{classify_offer_observation, DriverActivityEvent, DriverEventKind, EventLedger, ObservationEnvelope};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RoadEvent {
    pub id: String,
    pub category: String,
    pub latitude: f64,
    pub longitude: f64,
    pub observed_at_unix: i64,
    pub ttl_seconds: i64,
    pub source_id: String,
}

impl RoadEvent {
    pub fn is_current_at(&self, now_unix: i64) -> bool {
        if self.ttl_seconds <= 0 || self.observed_at_unix > now_unix { return false; }
        now_unix.saturating_sub(self.observed_at_unix) <= self.ttl_seconds
    }
}

pub const ENGINE_VERSION: &str = "busybee-road-core/0.2.0";

#[cfg(test)]
mod tests {
    use super::*;
    fn event(observed: i64, ttl: i64) -> RoadEvent {
        RoadEvent { id: "evt-1".into(), category: "hazard".into(), latitude: 50.0647,
            longitude: 19.9450, observed_at_unix: observed, ttl_seconds: ttl,
            source_id: "driver-report".into() }
    }
    #[test] fn fresh_event_is_current() { assert!(event(100, 60).is_current_at(150)); }
    #[test] fn expired_event_is_not_current() { assert!(!event(100, 60).is_current_at(161)); }
    #[test] fn future_observation_is_rejected() { assert!(!event(101, 60).is_current_at(100)); }
    #[test] fn invalid_ttl_is_rejected() { assert!(!event(100, 0).is_current_at(100)); }
}

#[no_mangle]
pub extern "system" fn Java_com_ysfclc_busybee_drivercommandcentre_NativeBridge_engineVersion(
    env: jni::JNIEnv,
    _this: jni::objects::JObject,
) -> jni::sys::jstring {
    env.new_string(ENGINE_VERSION).expect("JNI string allocation failed").into_raw()
}

fn classify_observation_json(input: &str) -> String {
    let envelope: observation::ObservationEnvelope = match serde_json::from_str(input) {
        Ok(value) => value,
        Err(_) => return serde_json::json!({"error": "invalid_observation_envelope"}).to_string(),
    };
    match classify_offer_observation(&envelope) {
        Ok(Some(event)) => serde_json::to_string(&event)
            .unwrap_or_else(|_| serde_json::json!({"error": "event_serialization_failed"}).to_string()),
        Ok(None) => serde_json::json!({"result": "no_event"}).to_string(),
        Err(error) => serde_json::json!({"error": format!("{error:?}")}).to_string(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ysfclc_busybee_drivercommandcentre_NativeBridge_classifyOfferObservation(
    mut env: jni::JNIEnv,
    _this: jni::objects::JObject,
    input: jni::objects::JString,
) -> jni::sys::jstring {
    let raw: String = env.get_string(&input).map(|value| value.into()).unwrap_or_default();
    let output = classify_observation_json(&raw);
    env.new_string(output).expect("JNI string allocation failed").into_raw()
}
