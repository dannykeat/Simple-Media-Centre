# Simple Media Centre

A deliberately small Android media-centre app focused on local video libraries.

## Current MVP

The current working slice provides:

- Android Storage Access Framework folder selection where a system picker is available;
- Android TV fallback using MediaStore storage-volume discovery when no folder picker exists;
- persisted SAF folders and selected MediaStore volumes, including supported USB/external drives;
- persisted access to selected folders;
- recursive indexing of common video formats;
- movie and TV-episode filename recognition, including movie years;
- Movies, TV Shows and Continue Watching library sections;
- TMDB poster artwork with local image caching;
- TV episodes grouped under their show;
- touch and D-pad focusable poster-grid navigation;
- Media3/ExoPlayer playback with Android TV D-pad control support;
- per-file resume position;
- optional TMDB movie/TV-series matching using a user-supplied API Read Access Token;
- cached TMDB IDs, titles, summaries and artwork paths without storing credentials in Git.

Local scanning and playback remain usable when metadata is disabled or unavailable.

## Architecture

- **Native Android/Kotlin**
- **Storage Access Framework** for user-selected folders
- **Media3 1.11.1** for playback
- **Coil 3.6.3** for poster loading and memory/disk caching
- lightweight local persistence using app preferences/JSON for the MVP
- isolated filename parsing, scanning and metadata-provider layers
- TMDB accessed through its v3 search API with Bearer-token application authentication

The app deliberately does not aim to reproduce Kodi plug-ins, PVR, music, skins, transcoding, or network-server features.

## TMDB development setup

In the app, choose **TMDB** and enter your TMDB **API Read Access Token**. The token is stored in the app's private preferences and is never committed to this repository.

This project uses the TMDB API but is not endorsed or certified by TMDB.

Before any public release using TMDB data/images, add an approved TMDB logo to the About/Credits screen as required by TMDB's attribution rules.

## Build requirements

- Android Studio compatible with AGP 9.4
- Built-in Kotlin compiler 2.4.10, pinned to match Coil 3.6.3 dependencies
- JDK 17
- Gradle 9.6+
- Android SDK API 37.0

The repository currently does not include Gradle wrapper binaries. CI installs Gradle 9.6 explicitly.

```bash
gradle test lint assembleDebug
```

## Roadmap

See [ROADMAP.md](ROADMAP.md).
