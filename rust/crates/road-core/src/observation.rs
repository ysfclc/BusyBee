use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

/// Versioned, provider-neutral input captured by Android adapters or controlled
/// test sources. An observation is evidence, never a driver event by itself.
pub const OBSERVATION_SCHEMA_VERSION: u16 = 1;
pub const EVENT_SCHEMA_VERSION: u16 = 1;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum SourceKind {
    Notification,
    ProviderApi,
    AndroidSystem,
    OwnedAppInstrumentation,
    Manual,
    Import,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SourceIdentity {
    pub source_id: String,
    pub provider: String,
    pub kind: SourceKind,
    /// Adapter version, not the provider app version.
    pub adapter_version: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EvidenceRef {
    pub evidence_id: String,
    pub evidence_type: String,
    /// Optional opaque local reference. Do not put secrets or raw credentials here.
    pub local_ref: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ObservedFact {
    /// None means the source did not establish a value. Never fill it by guessing.
    pub value: Option<String>,
    pub evidence: Vec<EvidenceRef>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ObservationEnvelope {
    pub schema_version: u16,
    pub observation_id: String,
    pub source: SourceIdentity,
    pub observed_at_unix_ms: i64,
    pub received_at_unix_ms: i64,
    /// Stable identity supplied by the adapter when available.
    pub subject_id: Option<String>,
    pub facts: BTreeMap<String, ObservedFact>,
    pub evidence: Vec<EvidenceRef>,
}

impl ObservationEnvelope {
    pub fn validate(&self) -> Result<(), ContractError> {
        if self.schema_version != OBSERVATION_SCHEMA_VERSION {
            return Err(ContractError::UnsupportedSchemaVersion);
        }
        if self.observation_id.trim().is_empty()
            || self.source.source_id.trim().is_empty()
            || self.source.provider.trim().is_empty()
            || self.source.adapter_version.trim().is_empty()
        {
            return Err(ContractError::MissingIdentity);
        }
        if self.observed_at_unix_ms <= 0 || self.received_at_unix_ms <= 0 {
            return Err(ContractError::InvalidTimestamp);
        }
        if self.received_at_unix_ms < self.observed_at_unix_ms {
            return Err(ContractError::ReceivedBeforeObserved);
        }
        if self.facts.keys().any(|key| key.trim().is_empty()) {
            return Err(ContractError::EmptyFactKey);
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DriverEventKind {
    OfferObserved,
    OfferUpdated,
    OfferExpired,
    OfferDecisionObserved,
    TripStarted,
    TripUpdated,
    TripCompleted,
    TripCancelled,
    EarningObserved,
    AdjustmentObserved,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum EventStatus {
    Candidate,
    Confirmed,
    Rejected,
    Conflict,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum FieldCertainty {
    DirectlyObserved,
    Derived,
    Unknown,
    Conflicting,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FieldProvenance {
    pub observation_id: String,
    pub source_id: String,
    pub observed_at_unix_ms: i64,
    pub evidence_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CanonicalField {
    pub value: Option<String>,
    pub certainty: FieldCertainty,
    pub provenance: Vec<FieldProvenance>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DriverActivityEvent {
    pub schema_version: u16,
    /// Stable idempotency key chosen by the classifier/reconciler.
    pub event_id: String,
    /// Stable provider-neutral identity for the offer/trip/earning lifecycle.
    pub lifecycle_id: String,
    pub kind: DriverEventKind,
    pub status: EventStatus,
    pub occurred_at_unix_ms: Option<i64>,
    pub first_observed_at_unix_ms: i64,
    pub last_observed_at_unix_ms: i64,
    pub fields: BTreeMap<String, CanonicalField>,
    pub contributing_observation_ids: Vec<String>,
}

impl DriverActivityEvent {
    pub fn validate(&self) -> Result<(), ContractError> {
        if self.schema_version != EVENT_SCHEMA_VERSION {
            return Err(ContractError::UnsupportedSchemaVersion);
        }
        if self.event_id.trim().is_empty() || self.lifecycle_id.trim().is_empty() {
            return Err(ContractError::MissingIdentity);
        }
        if self.first_observed_at_unix_ms <= 0
            || self.last_observed_at_unix_ms < self.first_observed_at_unix_ms
            || self.occurred_at_unix_ms.is_some_and(|t| t <= 0)
        {
            return Err(ContractError::InvalidTimestamp);
        }
        if self.contributing_observation_ids.is_empty() {
            return Err(ContractError::MissingProvenance);
        }
        for field in self.fields.values() {
            if field.certainty == FieldCertainty::Unknown && field.value.is_some() {
                return Err(ContractError::UnknownFieldHasValue);
            }
            if field.certainty != FieldCertainty::Unknown && field.value.is_none() {
                return Err(ContractError::KnownFieldMissingValue);
            }
            if field.provenance.is_empty() {
                return Err(ContractError::MissingProvenance);
            }
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LedgerInsert {
    Inserted,
    Duplicate,
    Conflict,
}

/// Minimal deterministic ledger primitive. Durable storage is deliberately
/// owned by the BusyBee application layer; this type defines safe semantics.
#[derive(Debug, Default)]
pub struct EventLedger {
    events: BTreeMap<String, DriverActivityEvent>,
}

impl EventLedger {
    pub fn insert(&mut self, event: DriverActivityEvent) -> Result<LedgerInsert, ContractError> {
        event.validate()?;
        match self.events.get(&event.event_id) {
            None => {
                self.events.insert(event.event_id.clone(), event);
                Ok(LedgerInsert::Inserted)
            }
            Some(existing) if existing == &event => Ok(LedgerInsert::Duplicate),
            Some(_) => Ok(LedgerInsert::Conflict),
        }
    }

    pub fn get(&self, event_id: &str) -> Option<&DriverActivityEvent> {
        self.events.get(event_id)
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ContractError {
    UnsupportedSchemaVersion,
    MissingIdentity,
    InvalidTimestamp,
    ReceivedBeforeObserved,
    EmptyFactKey,
    MissingProvenance,
    UnknownFieldHasValue,
    KnownFieldMissingValue,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn provenance() -> FieldProvenance {
        FieldProvenance {
            observation_id: "obs-1".into(),
            source_id: "synthetic-provider".into(),
            observed_at_unix_ms: 1_000,
            evidence_ids: vec!["evidence-1".into()],
        }
    }

    fn event(amount: &str) -> DriverActivityEvent {
        let mut fields = BTreeMap::new();
        fields.insert("amount".into(), CanonicalField {
            value: Some(amount.into()),
            certainty: FieldCertainty::DirectlyObserved,
            provenance: vec![provenance()],
        });
        DriverActivityEvent {
            schema_version: EVENT_SCHEMA_VERSION,
            event_id: "offer:abc:observed".into(),
            lifecycle_id: "offer:abc".into(),
            kind: DriverEventKind::OfferObserved,
            status: EventStatus::Candidate,
            occurred_at_unix_ms: Some(1_000),
            first_observed_at_unix_ms: 1_000,
            last_observed_at_unix_ms: 1_000,
            fields,
            contributing_observation_ids: vec!["obs-1".into()],
        }
    }

    #[test]
    fn ledger_inserts_then_recognises_exact_duplicate() {
        let mut ledger = EventLedger::default();
        assert_eq!(ledger.insert(event("25.00")).unwrap(), LedgerInsert::Inserted);
        assert_eq!(ledger.insert(event("25.00")).unwrap(), LedgerInsert::Duplicate);
        assert_eq!(ledger.len(), 1);
    }

    #[test]
    fn ledger_surfaces_same_id_with_different_content_as_conflict() {
        let mut ledger = EventLedger::default();
        ledger.insert(event("25.00")).unwrap();
        assert_eq!(ledger.insert(event("30.00")).unwrap(), LedgerInsert::Conflict);
        assert_eq!(ledger.get("offer:abc:observed").unwrap().fields["amount"].value.as_deref(), Some("25.00"));
    }

    #[test]
    fn unknown_values_must_remain_empty() {
        let mut candidate = event("25.00");
        candidate.fields.insert("pickup".into(), CanonicalField {
            value: Some("guessed address".into()),
            certainty: FieldCertainty::Unknown,
            provenance: vec![provenance()],
        });
        assert_eq!(candidate.validate(), Err(ContractError::UnknownFieldHasValue));
    }

    #[test]
    fn candidate_requires_observation_provenance() {
        let mut candidate = event("25.00");
        candidate.contributing_observation_ids.clear();
        assert_eq!(candidate.validate(), Err(ContractError::MissingProvenance));
    }

    #[test]
    fn future_or_invalid_observation_times_are_rejected() {
        let mut envelope = ObservationEnvelope {
            schema_version: OBSERVATION_SCHEMA_VERSION,
            observation_id: "obs-1".into(),
            source: SourceIdentity {
                source_id: "source-1".into(),
                provider: "synthetic".into(),
                kind: SourceKind::OwnedAppInstrumentation,
                adapter_version: "0.1".into(),
            },
            observed_at_unix_ms: 2_000,
            received_at_unix_ms: 1_000,
            subject_id: None,
            facts: BTreeMap::new(),
            evidence: vec![],
        };
        assert_eq!(envelope.validate(), Err(ContractError::ReceivedBeforeObserved));
        envelope.received_at_unix_ms = 2_000;
        assert_eq!(envelope.validate(), Ok(()));
    }
}
