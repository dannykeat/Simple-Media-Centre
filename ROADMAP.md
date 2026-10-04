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
- [ ] validate build in CI and on a real Android/Android TV device

## M1 — metadata

- [ ] introduce metadata-provider interface
- [ ] TMDB configuration without committing API credentials
- [ ] movie search/matching
- [ ] TV series + season/episode matching
- [ ] poster/backdrop caching
- [ ] manual "fix match" flow
- [ ] retain usable local records when metadata lookup fails

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
