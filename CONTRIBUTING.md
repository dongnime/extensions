# Panduan Kontribusi Dongnime Extensions

Terima kasih atas minat Anda untuk berkontribusi pada repositori **Dongnime Extensions**! Dokumen ini memuat panduan dasar untuk mengembangkan dan menguji ekstensi Aniyomi/Anikku.

---

## 🏗️ Struktur Repositori

```text
├── core/                # Modul inti & antarmuka dasar ekstensi Aniyomi
├── lib/                 # Pustaka utilitas & extractor bersama (Dailymotion, OK.ru, Doodstream, Unpacker, Cloudflare)
├── src/
│   └── id/              # Seluruh ekstensi berbahasa Indonesia
│       ├── anichin/     # Modul ekstensi Anichin
│       ├── animexin/    # Modul ekstensi AnimeXin
│       └── nekopoi/     # Modul ekstensi Nekopoi
```

---

## 🛠️ Persyaratan Lingkungan (Prerequisites)

- **JDK 17** (OpenJDK 17 disarankan)
- **Android SDK** (Build Tools 34.0.0, API Level 34)
- **Git**

---

## 🚀 Cara Menjalankan & Menguji Secara Lokal

1. **Clone repositori:**
   ```bash
   git clone https://github.com/dongnime/extensions.git
   cd extensions
   ```

2. **Kompilasi build ekstensi (Debug):**
   ```bash
   # Kompilasi seluruh modul
   ./gradlew assembleDebug

   # Atau kompilasi modul tertentu saja
   ./gradlew :src:id:anichin:assembleDebug
   ```

3. **Install langsung ke perangkat/emulator Android via ADB:**
   ```bash
   adb install -r src/id/anichin/build/outputs/apk/debug/aniyomi-id.anichin-*-debug.apk
   ```

---

## 📋 Pedoman Menambah Ekstensi Baru

1. Buat folder baru di bawah `src/id/<nama_situs>/`.
2. Sertakan konfigurasi `build.gradle` dengan `extName`, `extClass`, dan `extVersionCode = 1`.
3. Sediakan icon `ic_launcher.png` resmi berukuran 192x192 RGBA untuk semua density:
   - `res/mipmap-mdpi/` (48x48)
   - `res/mipmap-hdpi/` (72x72)
   - `res/mipmap-xhdpi/` (96x96)
   - `res/mipmap-xxhdpi/` (144x144)
   - `res/mipmap-xxxhdpi/` (192x192)
4. Daftarkan dependensi library extractor yang dibutuhkan di `build.gradle`.
5. Implementasikan class ekstensi turunan dari `AnimeHttpSource()`.
6. Selalu tambahkan pengaturan **Base URL Preference** agar pengguna dapat mengganti domain jika diblokir oleh ISP.

---

## 🔀 Alur Pengajuan Pull Request (PR)

1. Buat branch baru dari `master`:
   ```bash
   git checkout -b feat/tambah-ekstensi-xyz
   ```
2. Pastikan kode lulus kompilasi:
   ```bash
   ./gradlew assembleDebug
   ```
3. Commit perubahan dengan pesan commit yang jelas mengikuti format Conventional Commits:
   - `feat(xyz): add XYZ streaming extension`
   - `fix(anichin): fix video stream resolver on mirror server`
4. Buka Pull Request ke branch `master` dan isi checklist pada template PR.
