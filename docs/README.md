# AmazeCC-Kotlin Documentation

Welcome to the documentation for **AmazeCC-Kotlin**, the native Compose Multiplatform application for AmazeCC built with **Material 3 Expressive (M3E)**.

## Documentation Index

- [M3E Screen Migration Plan](M3E_SCREEN_MIGRATION_PLAN.md): Complete master plan, architectural guidelines, screen inventory, and phased roadmap for transplanting all web screens from `../AmazeCC` into pure stateless Compose Multiplatform screens.

## Architectural Principles
1. **Material Design 3 Expressive (MUI3 Expressive)**: Modern tokens, 5-level surface containers, wavy progress indicators, and connected button groups.
2. **100% Stateless UI Composables**: All screens take immutable state data classes and emit actions via lambda.
3. **Multithreaded Background Computations**: Heavy operations (bunk calculations, timetable slot mapping, filtering) execute on `Dispatchers.Default` and `Dispatchers.IO`.
4. **Live In-IDE Previews**: Every composable is annotated with `@Preview` for instant visual tuning in Android Studio / Fleet / IntelliJ.
5. **Headless JVM Unit Testing**: UI and logic tested headlessly on the JVM in seconds without physical devices via `.\run_tests.ps1`.
