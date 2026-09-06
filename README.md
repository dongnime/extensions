# 🎬 Dongnime Extensions

[![Build and Publish](https://github.com/dongnime/extensions/actions/workflows/build.yml/badge.svg)](https://github.com/dongnime/extensions/actions/workflows/build.yml)
![Extensions](https://img.shields.io/badge/extensions-3%20available-brightgreen?style=flat-square)
![Language](https://img.shields.io/badge/language-Indonesian%20(id)-red?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Aniyomi%20%7C%20Anikku-blueviolet?style=flat-square)
![License](https://img.shields.io/badge/license-Apache--2.0-blue?style=flat-square)

Repositori modular resmi **Dongnime** yang memuat modul ekstensi pengikis web (*source web parsers*) untuk aplikasi pemutar media Android seperti [Aniyomi](https://aniyomi.org) dan [Anikku](https://anikku-app.github.io/).

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

## 📦 Katalog Ekstensi yang Tersedia

| Icon | Nama Ekstensi | Bahasa | Kategori | Versi | Sumber Antarmuka | Deskripsi Teknis |
| :---: | :--- | :---: | :---: | :---: | :--- | :--- |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.anichin.png" width="36" height="36" alt="Anichin"> | **Anichin** | `id` | Umum | `15.8` | [anichin.cafe](https://anichin.cafe) | Parser serial donghua, multi-server extractor, filter genre & status katalog. |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.animexin.png" width="36" height="36" alt="AnimeXin"> | **AnimeXin** | `id` | Umum | `15.3` | [animexin.dev](https://animexin.dev) | Parser serial donghua & anime, multi-server video resolver, multi-quality stream. |
| <img src="https://raw.githubusercontent.com/dongnime/extensions/repo/icon/eu.kanade.tachiyomi.animeextension.id.nekopoi.png" width="36" height="36" alt="Nekopoi"> | **Nekopoi** | `id` | **18+ (NSFW)** | `15.3` | [nekopoi.care](https://nekopoi.care) | Parser animasi dewasa, Doodstream & direct video resolver. |

> [!NOTE]
> **Kontrol Orang Tua & Penyaringan Konten Dewasa (NSFW)**:  
> Ekstensi yang ditandai **18+ (NSFW)** secara otomatis disaring oleh fitur bawaan aplikasi Aniyomi / Anikku. Anda dapat mengaktifkan atau menyembunyikan seluruh sumber dewasa kapan saja melalui menu **Settings → Browse → Show NSFW sources**.

---

## ⚙️ Fitur Ekstensi

- **Domain Dinamis (*Base URL Preference*)**:
  Jika situs sumber melakukan pergantian domain atau domain utama tidak dapat diakses di jaringan lokal, pengguna dapat menyesuaikan URL domain baru secara mandiri melalui menu preferensi masing-masing ekstensi.
- **Pilihan Resolusi Default**:
  Pengguna dapat menentukan prioritas kualitas pemutaran video (1080p, 720p, 480p, atau 360p) untuk mengoptimalkan penggunaan bandwidth.
- **Dukungan Modular Extractor**:
  Menggunakan parser modular untuk berbagai host pemutar video pihak ketiga (Dailymotion, OK.ru, Doodstream, Morencius, AbyssPlayer, dsb).

---

## 📖 Panduan Penggunaan di Aplikasi

1. Buka aplikasi **Aniyomi** atau **Anikku**.
2. Masuk ke **More (Lainnya)** → **Settings (Pengaturan)** → **Browse (Jelajahi)**.
3. Pilih **Anime extension repos**.
4. Ketuk tombol **+ (Tambah)** di pojok kanan bawah.
5. Masukkan URL repositori Dongnime:
   `https://raw.githubusercontent.com/dongnime/extensions/repo/index.min.json`
6. Simpan, lalu buka tab **Extensions**.
7. Lakukan *pull-to-refresh* (tarik layar ke bawah) untuk memuat daftar ekstensi.
8. Pilih ekstensi yang dibutuhkan dan tekan **Install**.

---

## ⚖️ Penafian Hukum & Merek Dagang (Disclaimer & Trademarks)

1. **Bukan Penyedia Konten**: Repositori ini **TIDAK menyimpan, menampung, menyiarkan, atau mengunggah** berkas media, film, ataupun video apa pun di server kami.
2. **Karakteristik Parser**: Seluruh modul ekstensi dalam repositori ini berfungsi murni sebagai alat perayap antarmuka (*client-side web scraper*) yang membaca data teks/HTML publik yang telah tersedia di internet, serupa dengan prinsip kerja peramban web (*web browser*).
3. **Hak Milik Intelektual & Logo**: Seluruh nama produk, merek dagang, dan logo pihak ketiga yang dirujuk dalam repositori ini adalah milik dari masing-masing pemegang hak ciptanya. Penggunaan nama dan logo hanya bersifat *nominative fair use* untuk tujuan identifikasi visual dalam antarmuka aplikasi klien, tanpa menyiratkan afiliasi atau dukungan apa pun.
4. **Kebijakan Hak Cipta**: Untuk prosedur permintaan penghapusan modul ekstensi secara kooperatif, silakan merujuk pada [DMCA & Takedown Policy](DMCA.md) kami.

---

## 📄 Lisensi

Kode sumber repositori ini didistribusikan di bawah lisensi terbuka [Apache 2.0](LICENSE).
