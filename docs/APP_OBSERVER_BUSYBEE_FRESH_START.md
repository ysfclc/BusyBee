# Fresh Start — App Observer and BusyBee

**Checkpoint date:** 2026-11-22  
**Repository:** `ysfclc/BusyBee`  
**Baseline:** `main` at `05d335794498516b7b7f2c5c3f32ecc9c6b89094`  
**Checkpoint type:** audit and product-boundary reset; documentation only

## 1. Product boundary — locked

- **BusyBee is the end-user product:** the Driver Command Centre and operating platform.
- **App Observer is a research instrument:** it observes selected Android apps, runs controlled experiments, validates capture/perception methods and produces reusable evidence, fixtures and technical findings.
- App Observer is not a driver dashboard, a BusyBee screen, or a second product that drivers should need to operate.
- Shared code is allowed only through deliberate, reviewed integration. Research UI, research storage and experimental capture mechanisms do not automatically become BusyBee production features.
- The two projects currently live in one GitHub repository. Do not create a second repository or duplicate the code merely to make the product boundary clearer.

## 2. What exists on current `main`

### App Observer Lab — preserved survey baseline

Path: `research/app-observer/android`  
Package: `com.ysfclc.appobserver.debug`  
Version: `0.1.0-survey-debug`

This is a limited Android capability survey, not the later multimodal recorder. It reports installed-app metadata, Usage Access summaries, notification metadata, read-only Accessibility event/tree-shape counts and device-level VPN transport state. It does not capture screenshots, packet contents, per-app network flows or HTTPS plaintext. Counters stay local; JSON export is user initiated; the documented build has no INTERNET permission, account login or telemetry.

The recovery record documents a successful CI build/test/signature-verification run (#44655016958) and a fresh debug APK checksum:
`4717cbf851ff3f0ba3c9adb03147b33809dff2f342c9948a28ad058dbf6f29bd`.

This is the known-good survey baseline. Preserve it. Do not silently replace it with a different research experiment.

## 3. Richer App Observer Capture — separate development line

The more capable generic recorder exists under `research/app-observer-capture` on the development lines associated with PR #22 and the reset branch. Its documented capabilities include:

- Dynamic discovery of launchable apps and one manually selected target per session.
- Read-only Accessibility structure/event capture and user-visible screenshot capture where Android permits it.
- On-device OCR/image labelling and provisional screen-concept hypotheses.
- Local evidence/session storage, optional explicit semantic rules and local learning/correction.
- Optional PCAPdroid packet-header metadata only; no raw packet/payload storage or TLS decryption.
- User-initiated evidence export/import and an explicit self-test target.

The documented stable identity is `com.ysfclc.appobserver.capture`, version `0.5.0-multimodal` / versionCode 8. PR #22 records CI run #44806697182 as successful on commit `1f725eac2492c3137ac0d0940869740fccbbe0be`: unit tests, debug/release compilation, package/version checks and ZIP alignment passed. The CI artifact uploaded by that run was an **unsigned** release candidate because persistent signing secrets were absent; the PR separately records an offline-signed APK and certificate/hash, but that APK was not retrieved or independently re-verified in this audit. The current PR head (`811f516e2e1cca5537c1454d02e8a9f4e74237ec`) is five commits later; the comparison shows those intervening commits changed documentation/README files only, not Android source. Physical Redmi acceptance, representative app coverage, classifier accuracy, battery/latency behaviour and business-outcome verification remain incomplete.

**Do not confuse a successful CI build with a verified on-device research instrument.** Do not change the stable package ID or signing identity without an explicit migration plan.

## 4. Branch and PR situation

- PR [#22](https://github.com/ysfclc/BusyBee/pull/22), the older 0.5.0 capture draft, was closed without merge on 2026-11-22 as superseded by PR #28. Its source branch `fix/app-observer-capture-0.2.1` remains preserved. Android CI run #265 passed on the code commit `1f725eac2492c3137ac0d0940869740fccbbe0be`; later PR commits were documentation-only. The CI artifact was unsigned; offline signing is separately documented but was not independently verified in this audit.
- Branch `app-observer-intelligence-reset` contains additional work beyond the older capture PR: a generic scene-graph slice, a Rust observation/event contract, a Kotlin observation contract, an observation ledger path in BusyBee, JNI classification and canonical offer-event candidates in the Offer Desk.
- At this checkpoint, the reset branch compares as 103 commits ahead and 3 behind current `main`. It is not the same history as PR #22's head; comparing the two development lines reports divergence (104 commits unique to reset and 225 unique to the PR head, with a common ancestor at the restoration checkpoint).
- Therefore neither “merge PR #22” nor “merge the reset branch” is a safe cleanup operation. The branch histories and their code must be reconciled deliberately.
- PR [#28](https://github.com/ysfclc/BusyBee/pull/28) is the clean-mainline Capture 0.6.0 import: 41 files, research-only, no BusyBee product/event code, stable package retained and versionCode incremented to 9. Latest PR head CI run #335 passed on commit `b61d93d456b59cf87535fd11993111c5792fa4e9` and produced debug artifact `14736547217` plus unsigned release candidate `14736427945`. The app source and build workflow were unchanged by the documentation-only commits. Physical device acceptance remains pending.
- Preserve all existing branches and PR source histories during this audit. No branch deletion, force-push, blind merge or history rewrite was performed.

## 5. BusyBee production baseline

Current `main` describes Phase 3 — Driver Data Mirror — as the active product phase. The existing app includes local-first imports, evidence/ledger foundations, notification-based offer capture and driver-facing surfaces. The repository explicitly does not claim verified direct Uber/Bolt API access.

The reset branch's observation-to-event work is directionally consistent with BusyBee's architecture, but its presence on a divergent branch is not proof that it is integrated, reconciled, or verified on current `main`.

BusyBee remains the canonical owner of provider-neutral driver events and product workflows. App Observer may produce observations and reproducible fixtures; it must not become the owner of BusyBee's driver-event taxonomy or production UI.

## 6. Cleanup and delivery sequence

1. **Preserve this baseline.** Keep current `main`, the survey baseline, PR #22 and the reset branch recoverable.
2. **Audit the two App Observer apps independently.** Inventory modules, permissions, persistence/export, package/signing identities, tests, CI and known device limitations. Identify obsolete experiments without deleting them.
3. **Verify the latest richer Observer source.** Run tests/build on the exact current source head and inspect privacy boundaries. Repeat on the target Redmi/Android version before making device-compatibility claims.
4. **Separate research code from BusyBee integration.** Keep capture tooling under `research/app-observer-capture`; keep the canonical observation/event contract in BusyBee's production domain. Port or cherry-pick only reviewed, necessary changes.
5. **Reconcile incrementally onto current `main`.** Use a small, reviewable integration branch/PR. Preserve existing Driver Mirror and Offer Desk data paths and migrations. No wholesale branch merge.
6. **Prove one owned synthetic offer lifecycle end to end.** Capture observed/updated/expired evidence, classify it into canonical BusyBee events, preserve provenance/unknowns/conflicts, and prove replay/idempotency and delayed/out-of-order handling. Disappearance alone must not mean expiry.
7. **Apply validated capabilities to BusyBee.** Expose useful driver outcomes in BusyBee; do not expose App Observer's research console or require drivers to install the research instrument.
8. **Keep the existing BusyBee roadmap order.** The nearby trusted-driver alert idea remains future roadmap work and is not part of this cleanup or implementation slice.

## 7. Explicit non-actions at this checkpoint

- No Android, Rust, database, permission, signing or product code changed.
- No PR was merged. Older PR #22 was closed as superseded by PR #28; its source branch was retained.
- No branch was deleted, force-pushed or rewritten.
- No signing key or repository secret was accessed or changed.
- No physical-device acceptance is claimed.
- No new Observer feature or BusyBee feature was started.

## 8. Source-level audit findings on the reset branch

The reset branch contains a real first slice, but it is not yet the complete observation-to-event pipeline:

- **Contract foundation exists:** Kotlin and Rust define matching version-1 observation/event envelopes. The Rust classifier accepts explicit `offer_state` values (`observed`, `updated`, `expired`) and deliberately refuses to infer expiry from `disappeared`/`hidden`.
- **Actual event-producing inputs are narrow:** manual offers and CSV rows explicitly marked `record_type=offer` can produce `OfferObserved` candidates. Ride-platform notifications are stored as observations containing title/body/package facts, but do not carry a classified `offer_state`; they remain observations until a reliable classifier exists. Updating a manual offer's status currently updates the legacy offers table only; it does not emit `OfferDecisionObserved` or a lifecycle event.
- **Persistence is still split:** imported trip/earnings records and raw import rows remain in `busybee_driver_mirror.db` (`DriverMirrorStore`, schema 1). The new observation and canonical-event ledgers are in `busybee_command_centre.db` (`DriverCommandStore`, schema 4). CSV import rows are therefore written to the Mirror store and also copied into the Command Centre observation ledger. This does not yet satisfy the earlier issue #12 design for a unified durable activity query in the existing Driver Mirror database. No Android migration acceptance tests for existing user databases were found in the inspected change set.
- **Idempotency is only partial:** observation replay is deduplicated by `(observation_id, payload fingerprint)`. Canonical event IDs include the observation ID, so exact replay of one observation is idempotent, but two sources/imports describing the same real offer can still create separate event IDs. Cross-source lifecycle reconciliation and duplicate/conflict policy remain open.
- **The synthetic lifecycle is not built:** current Rust tests use in-memory envelopes; manual offers and CSV imports exercise the same classifier path. There is no owned synthetic Android provider app + HTTPS fixture proving observed → updated → expired with delayed, duplicate, malformed and out-of-order deliveries.
- **Observer-to-BusyBee adapter is not present:** the research capture app is isolated, as intended, but its exported evidence is not yet mapped through a tested adapter into BusyBee's `ObservationEnvelope`/canonical ledger. The current BusyBee event path is fed by BusyBee's own manual, CSV and notification adapters.
- **Learning claims need careful wording:** the richer Observer has on-device OCR/image labelling, heuristics, recurring-state clustering and correction-trained classification. Its own reset notes correctly state that this is not open-ended semantic learning, a validated behaviour model, calibrated confidence or verified business-outcome detection.

The reset branch's exact head is `8d8a9945c88352e85ee58ae3d282533c20fb3363`. Its current CI is verified:

- App Observer workflow run #328 (`44957222504`) passed on that exact head: unit tests, debug/release compilation, package/version checks and ZIP alignment. It uploaded unsigned release candidate artifact `14728675390`; persistent-signing steps were skipped because the four Actions signing secrets were absent.
- BusyBee observation/event contract CI run #11 (`44957222502`) passed on the same head: Rust workspace tests, Android unit tests and debug APK assembly.
- BusyBee Android workflow run #422 (`44957222582`) also passed on the same head: actionlint, Rust tests, native Android Rust library builds, Android tests and debug APK assembly. Release signing and publication were skipped because persistent signing secrets were absent.
- The Vercel check on this branch is failed only because the account hit the daily deployment limit; it is not an Android/Rust CI failure.

Therefore the reset branch is **CI-verified at source/build level**, unlike what the older PR #22 evidence alone established. Physical Redmi acceptance, capture coverage, real app classification accuracy, battery/latency behaviour, and event correctness on-device remain unverified. The reset branch still reports versionCode 8 / `0.5.0-multimodal` despite the additional scene-graph code. The clean-mainline PR #28 corrects this by using versionCode 9 and retaining the same persistent signing identity.

## 9. Next engineering gate

Before porting the reset branch, resolve the canonical persistence ownership and migration contract against issue #12, then add migration tests that preserve existing Driver Mirror and Command Centre records. Next, build the owned synthetic offer lifecycle and prove end-to-end capture, explicit event classification, replay/idempotency, delayed/out-of-order handling and evidence traceability. Only then should reviewed observation adapters and domain changes be integrated onto current `main` in small slices.
