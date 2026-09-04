# Anichin Extension Repo — Design

## Goal

Build a self-hosted Aniyomi/Anikku extension repo, published at
`github.com/dongnime/extensions`, that provides a single extension for
`anichin.cafe` (donghua streaming, WordPress "AnimeStream" theme). The
repo must be addable in-app via Extensions → "+" → repo URL, giving
users auto-updates like an official extension repo.

## Target site research

- CMS: WordPress 6.9, theme `wp-content/themes/animestream` — a theme
  family shared by many donghua/anime streaming sites.
- Series detail: `https://anichin.cafe/seri/<slug>/`
  - Title: `h1.entry-title`
  - Genres: `div.genxed a`
  - Status/Network/Studio/Country/Episodes/Type: `div.spe span`
  - Episode list: anchors matching `.../<slug>-episode-<n>-subtitle-indonesia/`
- Episode page: `https://anichin.cafe/<slug>-episode-<n>-subtitle-indonesia/`
  - Video servers: `select.mirror > option`, each `value` is
    **base64-encoded `<iframe>` HTML**; decode → extract `src`.
    Observed hosts: custom player `anichin.stream`, `ok.ru`,
    `dailymotion.com`, `rumble.com`, `player.abyssplayer.com`,
    `morencius.com`. Not all are ad-free; all must be attempted, and a
    host that fails to resolve is skipped rather than failing the
    whole video list.
- Browse/listing: homepage + `/page/N/` (WP pagination), filterable
  archive at `/anime/?status=&type=&order=`.
- Search: `/?s=<query>` (WP native search).
- Genre filter: `/genres/<slug>/`.

## Ecosystem context

- Official `aniyomiorg/aniyomi-extensions` monorepo is archived.
  Community has moved to independently-hosted monorepos using the
  same `extensions-lib` API, build-logic Gradle conventions, and CI
  scripts that generate `index.min.json` + APKs on a `repo` branch
  (e.g. `salmanbappi/sb-extensions-source`, actively maintained).
- This is the pattern our repo will follow: derive the build tooling
  from an active community monorepo, strip every extension module
  except our own.

## Approach (confirmed)

Fork the build tooling (not the content) of an active community
extensions-source monorepo:

1. Bring in `build-logic/`, version catalog, and the CI workflow that
   assembles APKs and generates `index.min.json` unmodified from the
   reference monorepo.
2. Bring in only the `lib/*-extractor` modules needed for the mirrors
   Anichin actually uses (ok.ru, dailymotion, rumble, and whichever
   generic extractors cover abyssplayer/morencius if they exist
   upstream).
3. Delete every other extension source module.
4. Add one new module: `src/id/anichin/`.

This avoids re-implementing Gradle convention plugins and the
index/APK signing pipeline from scratch, which is the highest-risk,
least interesting part of this project to get right by hand.

## New module: `src/id/anichin`

`Anichin.kt` implements `AnimeHttpSource`:

- `popularAnimeRequest/ParseResponse` → `/anime/?order=popular`,
  parse `<article class="bs">` cards (thumbnail, title, detail link).
- `latestUpdatesRequest/ParseResponse` → homepage / `/page/N/`.
- `searchAnimeRequest/ParseResponse` → `/?s=<query>`; genre browsing
  exposed via `AnimeFilterList` mapping to `/genres/<slug>/`.
- `animeDetailsParse` → parse `/seri/<slug>/` metadata described above.
- `episodeListParse` → episode anchors on the series page.
- `videoListParse` → for each `select.mirror option`: base64-decode,
  parse the embedded `<iframe src>`, and dispatch to the matching
  extractor. A server whose extractor fails or is unavailable is
  dropped from the result list, not treated as a fatal error.

`anichin.stream`'s own player has no known public extractor yet — it
will need reverse-engineering (network trace of what the player page
requests) during implementation; treated as a normal implementation
task, not a blocker for the rest of the module.

## Distribution

- Repo: `github.com/dongnime/extensions` (org repo, must stay public —
  `raw.githubusercontent.com` is unauthenticated from the app).
- GitHub Actions: on push to main, build the `anichin` module's APK,
  run the upstream index-generation script, push `index.min.json` +
  `apk/` to a `repo` branch.
- Users add the repo via: Aniyomi/Anikku → Extensions → "+" →
  `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json`.

## Testing

- Build locally: `./gradlew :src:id:anichin:assembleDebug`.
- Install the debug APK on a device/emulator running Aniyomi or
  Anikku.
- Manually verify against the live site, in order: popular/latest
  browse, search, genre filter, series detail, episode list, video
  playback for each mirror that has an extractor.
- No automated test suite is standard for this ecosystem's extensions
  (they're thin scraping adapters); manual verification against the
  live site is the accepted practice and what upstream monorepos rely
  on too.

## Out of scope (for this first version)

- Additional donghua sites beyond anichin.cafe (structure supports
  adding more modules later, but none are planned yet).
- NSFW flagging / age gating — not applicable to this content.
- Localization beyond the site's own Indonesian subtitles.

## Disclaimer

README will carry the standard ecosystem disclaimer: the extension
does not host any content, it only provides an interface to a
publicly available website. Repo intended for personal/educational
use, matching the norms of this extension ecosystem.
