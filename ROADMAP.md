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
- [ ] validate build in CI
- [ ] validate on a real Android/Android TV device

## M1 — metadata

- [x] introduce metadata-provider interface
- [x] TMDB token configuration without committing API credentials
- [x] movie search/matching
- [x] TV series search/matching
- [x] cache matched IDs, summaries and artwork paths
- [x] retain usable local records when metadata lookup fails
- [ ] fetch episode-specific metadata after series matching
- [ ] poster/backdrop image loading and disk caching
- [ ] manual "fix match" flow
- [ ] add approved TMDB logo to About/Credits before public release

## M2 — media-centre UX

- [ ] Movies / TV Shows / Recently Added / Continue Watching views
- [ ] poster-grid presentation
- [ ] Android TV / D-pad focus polish
- [ ] search and sort
- [ ] watched/unwatched state
- [ ] subtitle and audio-track selection
- [ ] multiple media sources and source management
- [ ] background/resumable rescans for large libraries

## M3 — robustness

- [ ] database-backed library once schema requirements stabilise
- [ ] detect removed/renamed files
- [ ] scan cancellation and progress
- [ ] metadata refresh policy
- [ ] migration/versioning tests
- [ ] broader device, codec and USB-drive validation
