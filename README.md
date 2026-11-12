# Majin Vegeta Self-Destruct — Fabric 1.21.1

Java 21 + Fabric Loader 0.16.5 + Fabric API 0.105.0 for Minecraft 1.21.1.

## Build

`./gradlew build` produces a jar in `build/libs/`. GitHub Actions builds it automatically.

## Commands

`/majin charge`, `/majin release`, `/majin cancel`, `/majin dummy spawn`

The mod uses vanilla particles, reaches a 200-block full-charge radius, applies 30% max-health damage once per second, pushes living entities outward, and clears terrain in bounded batches.
