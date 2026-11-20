# BusyBee brand v0.1

**Status:** first working brand system implemented in the Android app; intentionally open to refinement.

## Brand definition

- **Name:** BusyBee
- **Descriptor:** Driver Command Centre
- **Purpose:** a calm, capable co-pilot that helps professional drivers understand the road and their own work.
- **Personality:** alert, dependable, street-smart, practical and composed. Never childish, frantic or overly playful.
- **Promise:** one place to see what matters for the workday, without pretending uncertain information is certain.

The name is the selected working product brand in the app. This document does not claim trademark clearance, domain ownership or a completed commercial brand strategy.

## Logo mark

The first launcher mark is an original geometric bee. Its wings suggest movement and agility; the abdomen's dark bands double as a road motif; the small mint stinger gives the mark a directional point. It is designed to remain legible at Android launcher sizes.

The mark is supplied as Android vector/adaptive-icon resources, not a temporary platform placeholder:
- `drawable/busybee_launcher_foreground.xml`
- `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi-v26/ic_launcher_round.xml`

The adaptive background is deep navy. The launcher label is **BusyBee**.

## Colour tokens

| Token | Hex | Role |
|---|---|---|
| Night | `#07131B` | App and icon background; calm, high-contrast base |
| Deep panel | `#10232E` | Secondary surfaces and icon road bands |
| Signal mint | `#8FE7C1` | Primary action, active state, logo wings and direction |
| Warm amber | `#FFC978` | Attention, caution and logo body accent |
| White | `#FFFFFF` | Primary information |
| Muted blue-grey | `#9AAEB8` | Supporting information |

Use mint for active/action states, not every decorative element. Reserve amber for meaningful caution and attention. Keep body text high-contrast and map attribution readable.

## Typography and UI

- Use the existing Android sans-serif family.
- Prefer clear, compact labels and strong numeric hierarchy.
- Keep the map and active-driving surface glanceable; avoid decorative motion that competes with road information.
- Use rounded dark panels consistently, with mint reserved for the selected tab and primary action.
- The in-app wordmark is set as **BUSYBEE**; the launcher name is **BusyBee**.

## Brand boundaries

- The bee mark is a product identifier, not a claim that a trademark search has been completed.
- Do not use third-party ride-hailing logos as BusyBee's own identity.
- Do not introduce unrelated gradients, neon colours or stock bee illustrations.
- Keep the mark and colour tokens consistent across the launcher icon, app header, future splash screen, download page and any future web presence.

## Next brand work

1. Review the icon on a real Android launcher at small and large sizes, including themed-icon rendering.
2. Refine the bee silhouette and spacing only after checking it on-device.
3. Add a splash/launch treatment and use the same tokens on the Vercel download page.
4. Before public commercial release, check name, trademark and domain availability in relevant markets.
