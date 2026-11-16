# BusyBee map icon system

## Design rule

Map symbols are functional controls or spatial evidence, not decoration. One drawn icon family is used for map controls, workspace navigation, road events, driver reports and route endpoints. The existing vehicle, building-name and entrance artwork remains part of the same map visual language.

## Implemented semantics

| Context | Icon | Meaning |
|---|---|---|
| Roadworks | Construction cone | OSM-mapped roadworks/construction observation |
| Hazard | Warning triangle | OSM-mapped hazard observation |
| Closure | Barrier | A closure category if a source supplies one; not inferred from ordinary roadworks |
| Weather | Cloud/rain | Weather category only; not a crash or road-incident warning |
| Driver report | Speech bubble | Private driver-submitted report, distinct from public OSM observations |
| Route start | Ring and centre dot | Route origin |
| Destination | Flag | Route destination |
| Driver location | Vehicle silhouette | Device GPS position and heading |
| Building / entrance | Label chips | OSM-derived building and entrance references |
| Controls | Menu, recenter, report pictograms | Accessible map actions with content descriptions |
| Workspaces | Map, offer, earnings, vehicle hub, source-network pictograms | Navigation destinations; labels remain visible |

## Data honesty

- Marker shape communicates category. Colour reinforces the category but is not the only distinction.
- Severity, freshness and confidence are not drawn unless the underlying source actually provides them. Current Overpass observations do not supply a trustworthy observed-at timestamp or severity, so the UI does not invent either.
- OSRM route endpoints are route geometry markers, not live traffic or turn-by-turn navigation guarantees.
- Road event snippets retain the OSM source label; private driver reports remain separately labelled.
- Map icons are generated locally as bitmap artwork; there are no external icon-font or network dependencies.

## Acceptance still required

- Inspect at realistic Kraków zoom levels on the target phone: marker overlap, tap targets, map readability, day/night contrast and label density.
- Confirm icons remain legible on both map styles and do not obscure the vehicle or route line.
- Verify menu, recenter and report controls are reachable and have TalkBack descriptions.
- Check that map pan/follow, 2D/3D camera and style transitions do not duplicate or lose marker annotations.
- Do not add severity badges or live-state animations until source freshness and confidence are genuinely available.