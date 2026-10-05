# Hardware acceptance checklist

Target device: MECOOL Android TV with externally powered USB hard drive.

## Install / identify build

- Confirm **Simple Media Centre 0.4.0** in Settings → About.
- Start with the existing working HDD connected.
- Do not delete media files during testing.

## Sources and scanning

- Open Settings → Manage sources.
- Confirm existing source configuration is preserved.
- On the MediaStore fallback path, choose storage folders rather than the whole drive.
- Configure at least:
  - Movies → Movies
  - TV → TV Shows
  - a miscellaneous/download folder → Videos
- Confirm folders outside the selected source folders are excluded.
- Rename a source and confirm the friendly name is shown afterward.
- Rescan and confirm the source settings remain intact.

## Large movie library

- Confirm the local movie list appears before online metadata enrichment finishes.
- Confirm correct parsed titles.
- Confirm local frame thumbnails appear where TMDB artwork is unavailable.
- Test Search.
- Test Browse → Sort movies.
- Test Browse → Filter movies:
  - unwatched
  - watched
  - decade
- Test Browse → Jump A–Z.
- Confirm D-pad focus remains obvious while moving through a large grid.

## Metadata

If a TMDB token is configured:

- Confirm the local library remains usable while matching continues.
- Confirm progress such as `metadata 20/600` is visible between batches.
- Confirm matched artwork/titles replace local fallbacks as enrichment progresses.
- Confirm Videos sources are not sent for automatic TMDB matching.

If TMDB is not configured:

- Confirm movies remain usable with parsed titles and local thumbnails.
- Confirm the Metadata screen clearly shows that online metadata is optional.

## TV Shows

- Open TV Shows.
- Select a show with more than one season.
- Confirm navigation is Show → Season → Episode.
- Confirm watched counts are sensible.
- Confirm Resume appears for a partially watched episode.

## Videos

- Confirm miscellaneous/personal/downloaded content appears under Videos.
- Confirm Videos are grouped by their source/folder where path data is available.
- Confirm friendly source names are reflected in browsing.
- Confirm no TMDB matching is attempted for Videos.

## Playback

Test several known files, ideally with different containers/codecs.

For each:

- Open details.
- Start playback.
- Confirm D-pad player controls.
- Confirm audio-track selector.
- Confirm subtitle selector.
- Stop partway through and confirm Resume.
- Use Actions → Restart from beginning and confirm playback starts from zero.
- Play to near the end and confirm watched state.

If internal playback fails:

- Record the exact Media3 error code shown.
- Try Retry.
- Try Other player.
- Note whether the external player succeeds with the same file.
- Note the file extension and, if known, video/audio codecs.

This distinguishes storage-permission failures from Media3/decoder compatibility failures.

## Reconnect / robustness

- Exit the app.
- Disconnect and reconnect the HDD.
- Relaunch and confirm the configured source remains usable.
- Rescan.
- Remove one configured source and confirm its files disappear from the library without being deleted from disk.
- Re-add it and confirm playback/resume behaviour for unchanged URIs where Android preserves the same URI.

## Report back

Useful results:

- app version
- Android/MECOOL model and Android version
- number of indexed movies/TV episodes/videos
- whether local thumbnails appeared
- whether TMDB artwork appeared
- exact playback error code for any failed file
- whether Other player could play that same file
- any D-pad/focus traps
- any folder that was included/excluded incorrectly
