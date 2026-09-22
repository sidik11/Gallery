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
