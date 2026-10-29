# Traffic Engine contributor instructions

## Stable Git commit headings

- On `mc-1.20.1`, every commit subject must be exactly `Minecraft 1.20.1`.
- On `mc-1.21.1`, every commit subject must be exactly `Minecraft 1.21.1`.
- Keep these subjects unchanged when the Traffic Engine mod version changes.
- Put release versions and descriptions of changes in the commit body.
- Enable the tracked commit hook for local work with `git config core.hooksPath .githooks`. The hook keeps the original subject in the body when it normalizes a subject.
- Use the same subjects for commits created through GitHub or another tool that does not run local hooks.

## Validation and performance

- Prioritize runtime performance: avoid unnecessary per-frame/per-tick work, allocations, rendering passes, world scans and network updates. Prefer bounded caches and event-driven invalidation.
- For code changes, run the affected Gradle `compileJava` tasks by default, plus targeted regression/performance checks where appropriate.
- Run full packaging/build tasks or create JARs only when the user explicitly requests those artifacts.
- Do not claim zero FPS impact without measurements.

## Interface text

- Center all current and future menu/control captions horizontally and vertically using the displayed glyph bounds in every supported language. Check localized text for clipping; keep measurements cached and clear them when fonts or language resources reload. Apply the same rule to new interfaces.

## Placement selection outlines

- For new diagonal, inclined or rotated equipment, use the existing cached oriented selection-outline system. Match the rendered model with a simple outer frame, hide internal seams/carrier spans, and retain accurate picking and collision volumes. Do not replace it with a dense staircase of visible voxel edges.
- Keep outline geometry bounded and cached, reuse the existing selection buffer, and invalidate mesh-derived caches on model/resource reload rather than adding per-frame geometry construction or rendering passes.

- Diagonal/inclined variants must reuse the normal variant for breaking and hit particles, preserving its texture, shape, density reductions and count. Add each new placement type to PlacementParticles and its regression coverage; never emit particles from a dense diagonal voxel staircase.
- Historical item IDs are not the displayed Turkish names: highway_direction_sign = Büyük Yol Tabelası; large_road_direction_sign = Otoban Tabelası. Check localization before adjusting their inventory sizes.
