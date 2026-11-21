# App Observer — Intelligence Goal and Engineering Reset

**Date:** 2026-11-21  
**Applies to:** research/app-observer-capture and PR #22  
**Status:** corrective engineering baseline; implementation still required

## 1. Product goal

App Observer is meant to learn how arbitrary user-selected Android applications are structured and behave. It must combine every permitted observation source—screenshots/vision, OCR, Accessibility structure, interactions, notifications and network metadata—into a progressively improving model of the observed application.

The user should not have to draw and label every region. The machine should discover screens, elements, controls, content objects, relationships, recurring states and interaction-linked transitions by itself. User feedback is optional supervision and correction, not the core operating mechanism.

The output is an evidence-backed application model, not a screen classifier. It should eventually describe what exists, how elements relate, what changes after interactions, which transitions recur, what outcomes are verified, and what remains unknown. The observation system remains read-only; action execution is a separate future capability with its own safety and verification gates.

## 2. Honest current-state assessment

The PR implementation is a useful first foundation:

- It captures selected-app screenshots and structural Accessibility snapshots.
- It gathers interaction events, notification metadata/explicitly authorised content and minimised packet-header metadata.
- It runs on-device OCR and whole-screen image labelling.
- It combines these sources in a timestamp window and emits concept hypotheses with provenance.
- It localises some hypotheses using OCR boxes or Accessibility bounds.
- It clusters privacy-safe feature vectors and counts interaction-linked transitions.
- It accepts optional user corrections and trains a per-app Naive Bayes classifier.

But it does not yet meet the product goal. The current classifier is driven by a fixed phrase vocabulary, currency patterns and hand-written Accessibility/image-label mappings. The feature vector is largely a coded version of that same vocabulary. The learner clusters these features with Jaccard similarity and learns label/feature co-occurrence from corrections. This is not open-ended semantic learning, element discovery, relational learning or a validated model of app behaviour.

There is also no complete scene graph connecting visual regions, UI elements, text, controls, interactions and later outcomes. Time-window proximity is useful correlation, but it cannot by itself establish that a network burst caused a UI change or that an operation succeeded.

## 3. Correct system model

1. **Observation ledger** — immutable, timestamped evidence records with source identity, capture method, time uncertainty, screenshot/evidence references, privacy classification and source availability.
2. **Perception** — generic visual regions, text shapes, controls, containers, lists, images and other UI elements. Do not force every observation into a fixed business category.
3. **Scene graph** — elements as nodes and explicit relations such as parent/child, contains, overlaps, aligned, adjacent, grouped-with, label-of and control-for. Each relation has evidence and uncertainty.
4. **Temporal model** — element identity across frames; content changes; insertions/removals; layout movement; screen-state hypotheses; transition traces and counterexamples.
5. **Hypothesis engine** — competing interpretations with supporting and contradicting evidence, including an explicit unknown state.
6. **Learning and verification** — repeated traces, user corrections, predicted postconditions, observed outcomes, versioned model updates and held-out evaluation.
7. **Reusable knowledge** — app-independent patterns separated from app-specific mappings. A pattern learned in one app should be tested—not assumed—to generalise to another.

The sources are complementary sensors, not independent classifiers whose scores should simply be added. They must be synchronised into one observation model while retaining each source's own reliability and limitations.

## 4. Immediate implementation order

### M1 — Generic scene graph

Build a privacy-safe, replayable graph from current evidence. Include OCR line geometry and ephemeral text-shape categories, Accessibility hierarchy/geometry/control flags, screenshot regions, interaction source nodes and notification/network evidence references. Persist graph nodes and relations with provenance; never persist raw OCR strings unless separately authorised.

**Exit:** tests prove containment, overlap, alignment, adjacency and hierarchy relations; exported observations can be replayed without the original device session.

### M2 — Temporal element identity and change

Match graph elements across observations using structure, geometry, visual appearance and permitted stable identifiers. Separate stable identity from mutable values. Preserve competing matches and unknowns.

**Exit:** repeated screens with changing text retain element identity; visually similar but semantically different states are not silently merged.

### M3 — Relational and behavioural memory

Learn recurring graph motifs, element relationships and transitions. Keep failed/cancelled/ambiguous examples. Avoid a single union-of-features cluster that blurs different states.

**Exit:** repeated traces produce reproducible transition models with counterexamples and trace references.

### M4 — Open-world semantic learning

Use the scene graph, multimodal evidence and feedback to infer element roles and new concepts without adding a code constant for each concept. Keep app-independent patterns separate from app-specific mappings. Evaluate on held-out apps and traces.

**Exit:** previously unseen concepts can be proposed with evidence and uncertainty; cross-app reuse is demonstrated rather than assumed.

### M5 — Verified behaviour model

Predict observable postconditions and compare them with subsequent UI, notification and network evidence. Correlation is not causation; ambiguous outcomes remain unknown. Do not execute actions in this milestone.

**Exit:** success/failure/timeout/cancelled outcomes are measured against labelled traces with published false-success and false-failure rates.

## 5. Non-negotiable evaluation

A feature is not complete because it produces a label or stores more observations. Each milestone needs a repeatable corpus containing multiple unrelated apps, repeated workflows, layout variations, failures, cancellations, delayed updates and app-version changes.

Report at minimum element detection/localisation precision and recall, relation extraction precision and recall, state identity and transition accuracy, outcome verification false-success and false-failure rates, score calibration semantics, and performance/battery/memory/storage/capture gaps on the target device.

## 6. Decision on PR #22

Preserve its capture/privacy work and stable package/signing identity. Do not treat the current heuristic classifier and Jaccard/Naive Bayes memory as the finished learning engine. The next engineering checkpoint is M1, the generic scene graph, followed by temporal and relational memory. Do not add more fixed screen categories as a substitute for those capabilities.

The PR must remain unmerged until the branch is reconciled with current main, CI passes on the reconciled tree, and product claims accurately distinguish implemented foundations from unverified learning behaviour.
