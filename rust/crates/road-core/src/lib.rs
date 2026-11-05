use serde::{Deserialize, Serialize};

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

pub const ENGINE_VERSION: &str = "busybee-road-core/0.1.0";

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
