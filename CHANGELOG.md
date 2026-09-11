# Changelog

## 1.2.1

- Memindahkan menu Grup Buddy ke slot 9 paling kiri.
- Membiarkan slot 10 kosong sebelum Quick Match Compass agar jarak antarmenu konsisten dan rapi.
- Memigrasikan otomatis `main.group-slot: 10` dari konfigurasi versi 1.2.0 menjadi slot 9.

## 1.2.0

- Menambahkan Grup Buddy persisten dengan kapasitas maksimal 4 pemain.
- Menu Grup hanya muncul setelah dua pemain saling accept, tepat di kiri Compass pada GUI utama.
- GUI anggota, daftar kandidat online, undangan Accept/Decline, keluar grup, kick anggota, dan pergantian ketua otomatis.
- Anggota baru diteleport aman ke dekat grup; movement tidak membatalkan countdown.
- ActionBar dan Buddy Compass kini melacak anggota grup terdekat.
- Menambahkan buku Panduan Friendfy yang terbuka langsung dari GUI tanpa masuk inventory, di kanan Clock.
- Menambahkan `/fr group`, `/fr guide`, permission default, placeholder grup, dan migrasi konfigurasi/data lama.

## 1.1.2

- World Survival dideteksi otomatis tanpa perlu mengisi nama world.
- Konfigurasi lama `survival-world: world` otomatis mengikuti detector baru.
- Detector memprioritaskan Overworld non-lobby, nama Survival, dan worldborder 30.000.
- `/fr admin status` menampilkan target RTP hasil deteksi.

## 1.1.1

- Menambahkan parent permission `friendfy.player` dengan `default: true`.
- Semua fitur pemain langsung dapat digunakan tanpa konfigurasi LuckPerms.
- `friendfy.admin` sekarang mewariskan seluruh permission admin dan tetap khusus OP.

## 1.1.0

- Nama plugin menjadi Friendfy dengan command utama `/fr`.
- Alias lama tetap tersedia untuk kompatibilitas NPC/config.
- GUI Accept/Decline otomatis untuk pemain Bedrock/Floodgate.
- Memperbaiki SafeRTP yang terus menganggap lokasi tidak aman.
- Titik kedua sekarang wajib lolos validasi; tidak ada fallback koordinat mentah.
- Pencarian dinaikkan menjadi 200 percobaan dan dapat memakai chunk yang sudah termuat.
- BetterRTP sepenuhnya opsional; validasi WorldGuard/GriefPrevention dilakukan langsung.
- Pesan error world dan ringkasan diagnosis SafeRTP ditambahkan.

## 1.0.0

- GUI-first buddy matchmaking dengan kategori aktivitas.
- Match hanya dilanjutkan setelah kedua player accept.
- Ekspansi newbie ke low-progress dan Volunteer Buddy.
- Smart Never Alone untuk BetterTeams dan Marry2026.
- Satu SafeRTP destination area untuk dua player; tidak membuat chunk baru secara default.
- Movement tidak membatalkan countdown RTP.
- Buddy Session persisten, ActionBar tracker, format Bedrock, Buddy Compass, reconnect grace, dan Buddy TP dengan persetujuan.
- Block, DND, cooldown, combat check, dan perlindungan duplikasi compass.
- SQLite/MariaDB, admin status/debug, developer API, events, Citizens, Floodgate, dan PlaceholderAPI.
