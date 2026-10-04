# Simple Media Centre

A deliberately small Android media-centre app focused on local video libraries.

## Goal

Provide the useful core of a Kodi-style video library without Kodi's add-on/PVR/music/skin complexity:

- choose one or more local or USB-storage folders using Android's Storage Access Framework;
- recursively index common video files;
- recognise movie and TV-episode filenames;
- present a simple remote- and touch-friendly library;
- play files with AndroidX Media3;
- retain library data and playback progress;
- later enrich matches with online movie/TV metadata and artwork.

## Current status

Initial Android MVP foundation is being built.

## Architecture principles

- Native Android/Kotlin.
- Storage Access Framework instead of fragile raw filesystem assumptions.
- Media3/ExoPlayer for playback.
- Small, testable filename parsing and scanning components.
- Online metadata is an enrichment layer; local playback must continue to work without it.
- No Kodi-compatible plug-ins, skins, PVR, music library, server, or transcoding.

## Development

The project targets current Android tooling. JDK 17 and Gradle 9.6+ are required when building outside Android Studio.
