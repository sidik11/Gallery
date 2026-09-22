# MS Gallery

Privacy-first, offline Android gallery.

## Principles

- No INTERNET permission.
- Media is read from Android MediaStore.
- No Firebase, analytics, ads, WebView, or cloud sync.
- No Room/SQLite/DataStore/SharedPreferences.
- No persistent thumbnail/cache layer.
- Gallery indexing is performed from MediaStore at runtime.

## Build

Open the project in Android Studio and sync Gradle. The project targets Android 10+.

## Security model

Persistent user features such as a PIN, favorites, or an encrypted vault inherently require persistent data. Those features will be implemented as explicit encrypted user-owned data rather than hidden analytics/cache storage.

## Roadmap

- High-performance thumbnails
- Full-screen image viewer and Media3 video player
- Albums, folders, timeline, search and filters
- AES-GCM encrypted vault with Android Keystore
- Biometric/PIN lock and privacy controls
- EXIF tools
- Duplicate detection and storage analyzer
- Offline editor
- Themes/settings with an explicit persistence model


## Implemented modules

### Gallery
- Runtime MediaStore indexing
- RAM-only thumbnail loading through MediaStore
- Adaptive grid
- Offline filename/MIME search
- All/Videos filter
- Full-screen image viewer with pinch zoom
- Local Media3 video playback
- Screenshot/recents protection through FLAG_SECURE

### Security
- Android BiometricPrompt / device credential integration
- Android Keystore AES-256 vault key
- AES-256-GCM authenticated encryption
- Random per-file IV
- Opaque encrypted vault filenames
- No plaintext PIN/key storage

### Media utilities
- Streaming SHA-256 hashing
- Stateless album/folder grouping
- Storage analysis
- EXIF metadata reader
- Safe MediaStore copy/export primitive

## Persistence policy

MS Gallery deliberately avoids an application database and persistent thumbnail cache. Android MediaStore remains the source of truth for normal gallery content. The encrypted vault is the one intentional persistent application-owned data area because an encrypted vault cannot exist without storing encrypted bytes.

Settings that must survive an app restart require an explicit persistent encrypted settings design; they are not silently stored in SharedPreferences/DataStore.
