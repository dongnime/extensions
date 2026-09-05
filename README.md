# 🎬 Dongnime Extensions

[![Build and Publish](https://github.com/dongnime/extensions/actions/workflows/build.yml/badge.svg)](https://github.com/dongnime/extensions/actions/workflows/build.yml)
![Extensions](https://img.shields.io/badge/extensions-3%20available-brightgreen?style=flat-square)
![Language](https://img.shields.io/badge/language-Indonesian%20(id)-red?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Aniyomi%20%7C%20Anikku-blueviolet?style=flat-square)
![License](https://img.shields.io/badge/license-Apache--2.0-blue?style=flat-square)

Repositori ekstensi resmi **Dongnime** untuk aplikasi [Aniyomi](https://aniyomi.org) dan [Anikku](https://anikku-app.github.io/), menyediakan sumber streaming donghua, anime, dan animasi dengan takarir bahasa Indonesia (*Indonesian sub*).

---

## 📲 Cara Instalasi Cepat

### 1. Tambah Otomatis (1-Klik di HP)
Jika Anda membuka halaman ini melalui browser di smartphone yang sudah terinstal aplikasi Aniyomi atau Anikku, ketuk salah satu tombol di bawah:

- **[👉 Tambahkan ke Aniyomi](aniyomi://add-repo?url=https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json)**
- **[👉 Tambahkan ke Anikku](anikku://add-repo?url=https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json)**

### 2. Scan QR Code
Jika Anda membuka halaman ini di komputer/laptop, scan kode QR di bawah menggunakan kamera smartphone Anda:

<p align="center">
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=aniyomi://add-repo?url=https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json" alt="Scan QR Code to Add Repo" width="180" height="180">
  <br>
  <sub>Scan untuk langsung menambahkan repositori ke Aniyomi / Anikku</sub>
</p>

### 3. Tambah Manual via URL
Salin salah satu tautan di bawah, lalu tambahkan di menu **Pengaturan Repositori Ekstensi**:

- **URL Utama (GitHub Raw):**
  ```text
  https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json
  ```
- **URL Mirror CDN (Gunakan ini jika ISP memblokir `raw.githubusercontent.com`):**
  ```text
  https://cdn.jsdelivr.net/gh/dongnime/extensions@repo/index.min.json
  ```

---

## 📦 Daftar Ekstensi yang Tersedia

| Icon | Nama Ekstensi | Bahasa | Rating | Versi | Sumber Website | Fitur Utama |
| :---: | :--- | :---: | :---: | :---: | :--- | :--- |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.anichin.png" width="36" height="36" alt="Anichin"> | **Anichin** | `id` | Remaja | `15.8` | [anichin.cafe](https://anichin.cafe) | Streaming donghua terpopuler, multi-mirror server, filter genre & status lengkap. |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.animexin.png" width="36" height="36" alt="AnimeXin"> | **AnimeXin** | `id` | Remaja | `15.3` | [animexin.dev](https://animexin.dev) | Serial donghua & anime lengkap, Cloudflare bypass, multi-quality player. |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.nekopoi.png" width="36" height="36" alt="Nekopoi"> | **Nekopoi** | `id` | 18+ | `15.3` | [nekopoi.care](https://nekopoi.care) | Streaming hentai & animasi dewasa berbahasa Indonesia, Doodstream & direct extractor. |

---

## ⚙️ Fitur Unggulan

- **Domain Dinamis (*Base URL Preference*)**:
  Jika situs sumber terkena pemblokiran domain (*Internet Positif / TrustPositif*), Anda dapat mengganti alamat domain alternatif secara langsung melalui menu pengaturan masing-masing ekstensi tanpa harus menunggu rilis update baru.
- **Pilihan Resolusi Default**:
  Dapat memilih preferensi kualitas pemutaran video (1080p, 720p, 480p, atau 360p) untuk menghemat kuota data seluler.
- **Dukungan Multi-Extractor**:
  Mendukung decoding video otomatis dari berbagai server hosting populer (Dailymotion, OK.ru, Doodstream, Morencius, AbyssPlayer, dsb).

---

## 📖 Panduan Langkah demi Langkah di Aplikasi

1. Buka aplikasi **Aniyomi** atau **Anikku**.
2. Masuk ke **More (Lainnya)** → **Settings (Pengaturan)** → **Browse (Jelajahi)**.
3. Pilih **Anime extension repos**.
4. Ketuk tombol **+ (Tambah)** di pojok kanan bawah.
5. Tempel (*paste*) URL repositori Dongnime:
   `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json`
6. Tekan **Save / Simpan**, kemudian kembali ke tab **Extensions**.
7. Lakukan *pull-to-refresh* (tarik layar ke bawah) untuk memuat daftar ekstensi.
8. Pilih ekstensi yang Anda inginkan dan tekan **Install**.

---

## ⚠️ Disclaimer

1. Repositori ini **TIDAK menyimpan, menampung, atau mengunggah** berkas media, film, maupun video apa pun di server kami.
2. Seluruh ekstensi yang ada di repositori ini berfungsi murni sebagai *web scraper* / perayap antarmuka yang mengindeks konten yang tersedia secara publik di internet.
3. Proyek ini dikembangkan semata-mata untuk kepentingan edukasi dan penggunaan pribadi. Pengguna bertanggung jawab penuh atas penggunaan aplikasi dan konten yang diakses.

---

## 📄 Lisensi

Didistribusikan di bawah lisensi [Apache 2.0](LICENSE). Hak cipta masing-masing konten media tetap menjadi milik pembuat aslinya.
