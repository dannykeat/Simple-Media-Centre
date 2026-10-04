# Simple Media Centre

A deliberately small Android media-centre app focused on local video libraries.

## Current MVP

The first working slice provides:

- Android Storage Access Framework folder selection, including supported USB/external drives;
- persisted access to selected folders;
- recursive indexing of common video formats;
- movie and TV-episode filename recognition;
- a simple touch/remote-friendly library list;
- Media3/ExoPlayer playback;
- per-file resume position;
- rescanning without requiring raw-storage permissions.

Online metadata and artwork are intentionally the next layer. Local scanning and playback do not depend on an online service.

## Architecture

- **Native Android/Kotlin**
- **Storage Access Framework** for user-selected folders rather than raw filesystem paths
- **Media3 1.11.1** for playback
- lightweight local persistence using app preferences/JSON for the MVP
- isolated filename parsing so metadata matching can be added without changing storage or playback

The app deliberately does not aim to reproduce Kodi plug-ins, PVR, music, skins, transcoding, or network-server features.

## Build requirements

- Android Studio Rabbit 1 / another IDE compatible with AGP 9.4
- JDK 17
- Gradle 9.6+
- Android SDK API 37

The repository currently does not include Gradle wrapper binaries. CI installs Gradle 9.6 explicitly.

From a configured command line:

```bash
gradle test lint assembleDebug
```

## Roadmap

See [ROADMAP.md](ROADMAP.md).
