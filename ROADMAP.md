# Roadmap

## M0 — local library and playback

- [x] Android application skeleton
- [x] select SAF media folders
- [x] persist folder grants
- [x] Android TV MediaStore fallback when no system folder picker exists
- [x] recursively scan supported video files
- [x] basic movie/TV filename parsing
- [x] library list
- [x] Media3 playback
- [x] resume position
- [x] parser unit tests
- [x] validate command-line build on JDK 17 / Gradle 9.6 / API 37.0
- [ ] complete real Android TV validation (folder selection and ~600-file indexing passed on MECOOL; playback failed and is under active repair)

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

- [x] Home / Movies / TV Shows / Videos / Continue Watching views
- [x] poster-grid presentation with local video-frame fallback
- [x] initial Android TV / D-pad focus treatment
- [x] Recently Added view
- [x] search, Movies/TV/Videos sorting, A–Z jump navigation, watched filters, decade filters and TMDB genre categories
- [x] watched/unwatched state
- [x] subtitle and audio-track selection
- [x] multiple-source management and Movies / TV Shows / Videos / Mixed source typing
- [x] MediaStore subfolder selection with independent per-folder content types
- [x] background scans with live progress and cooperative cancellation
- [ ] resume an interrupted scan after process/app restart
- [ ] further visual polish and responsive phone/tablet/TV sizing
- [x] TV Show → Season → Episode browsing
- [x] configuration consolidated under Settings
- [x] movie/video details and Resume flow

### Current hardware findings

- MECOOL Android TV: folder/source selection works.
- Approximately 600 movies indexed with correct parsed titles.
- TMDB artwork was absent during the test; local video-frame thumbnail fallback has now been added.
- Large-library browsing needed search/sort/alphabet navigation; these are now implemented for the next test.
- Movie playback failed on hardware; Media3 decoder fallback, persistent error diagnostics, retry and external-player fallback are implemented for the next test.

## M3 — robustness

- [ ] database-backed library once schema requirements stabilise
- [x] detect removed files on rescan and preserve state across safely detected renames/moves
- [x] scan cancellation and progress
- [x] batched/incremental metadata enrichment with stale-job cancellation
- [ ] long-term metadata refresh/expiry policy
- [x] publish local scan results before optional online metadata enrichment
- [x] cached-library schema version marker and tolerant legacy loading
- [ ] Android migration/versioning tests
- [ ] broader device, codec and USB-drive validation
- [x] preserve cached MediaStore library records while a configured USB volume is temporarily unavailable
- [x] manual CI trigger and debug APK artifact for repeatable hardware testing
