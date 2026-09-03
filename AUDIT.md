# Nothing Playbox engineering audit

This document captures durable, high-level priorities discovered during the initial project-wide audit. It is intentionally short; GitHub issues and pull requests should carry implementation detail.

## Highest-impact priorities

1. Keep every change buildable and regression-checked through Android CI.
2. Split the oversized UI/activity surface as features evolve so editor, import, playback, and home behavior stay independently testable.
3. Make Pixel Lab history and playback semantics consistent across frames and loop modes.
4. Harden video import against decoder quirks, long/variable-frame-rate media, rotation, cancellation, and partial decode failures.
5. Grow built-in effects with deterministic procedural generators and reusable animation primitives instead of one-off frame code.
6. Reduce editor friction with better timeline operations, reversible bulk transforms, clearer timing controls, and immediate preview.
7. Keep hardware behavior focused on the Nothing Phone (4a) Pro 13×13 / 137-LED matrix, without root or Shizuku.
