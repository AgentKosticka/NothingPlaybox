# Workflows

`android-quality.yml` is the required engineering safety net for Nothing Playbox changes. It validates the bundled Nothing SDK, runs JVM tests and Android lint, and compiles debug, instrumentation-test, and release APKs.

Keep this workflow fast and deterministic. Hardware-specific behavior still needs real Phone (4a) Pro validation when a change touches the Glyph Matrix integration.
