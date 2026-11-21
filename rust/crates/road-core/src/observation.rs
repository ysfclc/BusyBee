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
            if matches!(&field.certainty, FieldCertainty::Unknown | FieldCertainty::Conflicting) && field.value.is_some() {
                return Err(ContractError::UnknownFieldHasValue);
            }
            if matches!(&field.certainty, FieldCertainty::DirectlyObserved | FieldCertainty::Derived) && field.value.is_none() {
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
    events: BTreeMap<String, Vec<DriverActivityEvent>>,
}

impl EventLedger {
    pub fn insert(&mut self, event: DriverActivityEvent) -> Result<LedgerInsert, ContractError> {
        event.validate()?;
        match self.events.entry(event.event_id.clone()) {
            std::collections::btree_map::Entry::Vacant(entry) => {
                entry.insert(vec![event]);
                Ok(LedgerInsert::Inserted)
            }
            std::collections::btree_map::Entry::Occupied(mut entry) => {
                let versions = entry.get_mut();
                if versions.iter().any(|existing| existing == &event) {
                    return Ok(LedgerInsert::Duplicate);
                }
                // Preserve both sides of the contradiction and make the
                // conflict visible to downstream consumers.
                for existing in versions.iter_mut() {
                    existing.status = EventStatus::Conflict;
                }
                let mut conflicting = event;
                conflicting.status = EventStatus::Conflict;
                versions.push(conflicting);
                Ok(LedgerInsert::Conflict)
            }
        }
    }

    pub fn get(&self, event_id: &str) -> Option<&[DriverActivityEvent]> {
        self.events.get(event_id).map(Vec::as_slice)
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }
}

/// Interpret a normalised offer observation into a canonical candidate.
/// This deliberately requires an explicit offer state: a notification or
/// screen merely disappearing is not evidence that an offer expired.
pub fn classify_offer_observation(
    envelope: &ObservationEnvelope,
) -> Result<Option<DriverActivityEvent>, ContractError> {
    envelope.validate()?;
    let state = envelope
        .facts
        .get("offer_state")
        .and_then(|fact| fact.value.as_deref())
        .unwrap_or("")
        .trim()
        .to_ascii_lowercase();

    let (kind, kind_key) = match state.as_str() {
        "observed" | "new" | "offered" => (DriverEventKind::OfferObserved, "offer_observed"),
        "updated" | "changed" => (DriverEventKind::OfferUpdated, "offer_updated"),
        "expired" => (DriverEventKind::OfferExpired, "offer_expired"),
        // In particular, "disappeared", "hidden" and unknown states do not
        // prove expiry. Keep the observation in the ledger without an event.
        _ => return Ok(None),
    };
    let lifecycle_id = envelope
        .subject_id
        .as_deref()
        .filter(|value| !value.trim().is_empty())
        .or_else(|| envelope.facts.get("offer_id").and_then(|fact| fact.value.as_deref()))
        .ok_or(ContractError::MissingIdentity)?;

    let fields = envelope.facts.iter().map(|(name, fact)| {
        let evidence_ids = if fact.evidence.is_empty() {
            envelope.evidence.iter().map(|item| item.evidence_id.clone()).collect()
        } else {
            fact.evidence.iter().map(|item| item.evidence_id.clone()).collect()
        };
        let provenance = FieldProvenance {
            observation_id: envelope.observation_id.clone(),
            source_id: envelope.source.source_id.clone(),
            observed_at_unix_ms: envelope.observed_at_unix_ms,
            evidence_ids,
        };
        let certainty = if fact.value.is_some() {
            FieldCertainty::DirectlyObserved
        } else {
            FieldCertainty::Unknown
        };
        (name.clone(), CanonicalField {
            value: fact.value.clone(),
            certainty,
            provenance: vec![provenance],
        })
    }).collect();

    let event = DriverActivityEvent {
        schema_version: EVENT_SCHEMA_VERSION,
        event_id: format!("{}:{}:{}", lifecycle_id, kind_key, envelope.observation_id),
        lifecycle_id: lifecycle_id.to_string(),
        kind,
        status: EventStatus::Candidate,
        occurred_at_unix_ms: if envelope.source.kind == SourceKind::Import { None } else { Some(envelope.observed_at_unix_ms) },
        first_observed_at_unix_ms: envelope.observed_at_unix_ms,
        last_observed_at_unix_ms: envelope.observed_at_unix_ms,
        fields,
        contributing_observation_ids: vec![envelope.observation_id.clone()],
    };
    event.validate()?;
    Ok(Some(event))
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
        let versions = ledger.get("offer:abc:observed").unwrap();
        assert_eq!(versions.len(), 2);
        assert_eq!(versions[0].fields["amount"].value.as_deref(), Some("25.00"));
        assert_eq!(versions[1].fields["amount"].value.as_deref(), Some("30.00"));
        assert!(versions.iter().all(|event| event.status == EventStatus::Conflict));
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

    fn offer_observation(state: &str, observation_id: &str) -> ObservationEnvelope {
        let mut facts = BTreeMap::new();
        facts.insert("offer_state".into(), ObservedFact {
            value: Some(state.into()),
            evidence: vec![EvidenceRef {
                evidence_id: format!("evidence-{observation_id}"),
                evidence_type: "synthetic_offer_state".into(),
                local_ref: None,
            }],
        });
        facts.insert("fare".into(), ObservedFact {
            value: Some("25.00".into()),
            evidence: vec![],
        });
        ObservationEnvelope {
            schema_version: OBSERVATION_SCHEMA_VERSION,
            observation_id: observation_id.into(),
            source: SourceIdentity {
                source_id: "synthetic-app".into(),
                provider: "synthetic-provider".into(),
                kind: SourceKind::OwnedAppInstrumentation,
                adapter_version: "test/1".into(),
            },
            observed_at_unix_ms: 1_000,
            received_at_unix_ms: 1_000,
            subject_id: Some("offer-123".into()),
            facts,
            evidence: vec![],
        }
    }

    #[test]
    fn classifies_explicit_offer_lifecycle_states() {
        assert_eq!(
            classify_offer_observation(&offer_observation("observed", "obs-a")).unwrap().unwrap().kind,
            DriverEventKind::OfferObserved
        );
        assert_eq!(
            classify_offer_observation(&offer_observation("updated", "obs-b")).unwrap().unwrap().kind,
            DriverEventKind::OfferUpdated
        );
        assert_eq!(
            classify_offer_observation(&offer_observation("expired", "obs-c")).unwrap().unwrap().kind,
            DriverEventKind::OfferExpired
        );
    }

    #[test]
    fn disappearance_does_not_become_offer_expiry() {
        assert!(classify_offer_observation(&offer_observation("disappeared", "obs-d")).unwrap().is_none());
    }

    #[test]
    fn offer_candidate_keeps_unknown_fields_unknown() {
        let mut envelope = offer_observation("observed", "obs-e");
        envelope.facts.insert("pickup".into(), ObservedFact { value: None, evidence: vec![] });
        let candidate = classify_offer_observation(&envelope).unwrap().unwrap();
        assert_eq!(candidate.fields["pickup"].certainty, FieldCertainty::Unknown);
        assert_eq!(candidate.fields["pickup"].value, None);
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
