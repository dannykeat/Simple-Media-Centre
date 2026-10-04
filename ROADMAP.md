# Roadmap

## M0 — local library and playback

- [x] Android application skeleton
- [x] select SAF media folders
- [x] persist folder grants
- [x] recursively scan supported video files
- [x] basic movie/TV filename parsing
- [x] library list
- [x] Media3 playback
- [x] resume position
- [x] parser unit tests
- [x] validate command-line build on JDK 17 / Gradle 9.6 / API 37.0
- [ ] validate on a real Android/Android TV device

## M1 — metadata

- [x] introduce metadata-provider interface
- [x] TMDB token configuration without committing API credentials
- [x] movie search/matching
- [x] TV series search/matching
- [x] cache matched IDs, summaries and artwork paths
- [x] retain usable local records when metadata lookup fails
- [x] load poster artwork with memory/disk caching
- [x] fetch episode-specific metadata after series matching
- [x] manual "fix match" flow
- [ ] add approved TMDB logo to About/Credits before public release

## M2 — media-centre UX

- [x] Movies / TV Shows / Continue Watching views
- [x] poster-grid presentation
- [x] initial Android TV / D-pad focus treatment
- [x] Recently Added view
- [ ] search and sort
- [x] watched/unwatched state
- [ ] subtitle and audio-track selection
- [ ] multiple media sources and source management
- [ ] background/resumable rescans for large libraries
- [ ] visual polish and responsive phone/tablet/TV sizing

## M3 — robustness

- [ ] database-backed library once schema requirements stabilise
- [ ] detect removed/renamed files
- [ ] scan cancellation and progress
- [ ] metadata refresh policy
- [ ] migration/versioning tests
- [ ] broader device, codec and USB-drive validation
