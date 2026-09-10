# Friendfy 1.1.2

Plugin matchmaking teman untuk Paper 1.21.11+, Java Edition dan Bedrock melalui Geyser/Floodgate.

## Instalasi

1. Gunakan Java 21 untuk menjalankan Paper 1.21.11.
2. Hapus JAR Friendfy/JustFriend versi lama, lalu masukkan `Friendfy-1.1.2.jar` ke folder `plugins`.
3. BetterTeams 5.0.0, Marry2026 2.0.0, BetterRTP 3.6.13, Floodgate, Citizens, PlaceholderAPI, WorldGuard, dan GriefPrevention bersifat opsional.
4. Restart server. Jangan memakai `/reload` Bukkit.
5. World Survival dideteksi otomatis. Radius RTP, NPC ID, message, dan GUI tetap dapat diubah dalam folder `plugins/Friendfy/` jika diperlukan.

## Deteksi World Otomatis

`survival-world: auto` dan `world-detection.enabled: true` aktif secara default. Friendfy menilai world Overworld non-lobby dari nama, ukuran worldborder (prioritas 30.000), world pemain, dan chunk yang termuat. `/fr admin status` menampilkan hasil akhirnya. Konfigurasi lama yang masih berisi `survival-world: world` juga tetap memakai deteksi otomatis selama `world-detection.enabled` tidak dimatikan.

Paper akan mengambil library SQLite/MariaDB yang dideklarasikan di `plugin.yml` ketika plugin pertama kali dijalankan.

## Command Player

Semua command pemain otomatis tersedia melalui permission bawaan `friendfy.player` (`default: true`). Tidak perlu menambahkan permission satu per satu melalui LuckPerms.

- `/fr` — GUI utama.
- `/fr queue [activity]` — masuk matchmaking.
- `/fr cancel` — keluar antrean.
- `/fr accept` atau `/fr decline` — jawab match Java; Bedrock memperoleh GUI Accept/Decline.
- `/fr settings` — pengaturan GUI.
- `/fr volunteer` — aktif/nonaktif Volunteer Buddy.
- `/fr tp` — minta teleport ke buddy.
- `/fr tp accept|deny` — jawab permintaan Buddy TP.
- `/fr block <player>` dan `/fr unblock <player>`.
- `/fr end` — akhiri Buddy Session.

Alias lama `/justfriend`, `/jf`, dan `/friendfinder` tetap tersedia agar NPC/config lama tidak langsung rusak.

## Command Admin

- `/fr admin`
- `/fr admin status`
- `/fr admin queue`
- `/fr admin sessions`
- `/fr admin end <player>`
- `/fr admin debug <player>`
- `/fr admin reload`

## Integrasi JAR yang dianalisis

- BetterTeams 5.0.0: memakai API `Team.getTeam(OfflinePlayer)` dan `getOnlineMembers()`.
- Marry2026 2.0.0: tidak memiliki API publik. Adapter reflection diisolasi dan akan gagal dengan aman bila versi berubah.
- BetterRTP 3.6.13: opsional dan tidak dibutuhkan untuk Friendfy. Versi ini memakai SafeRTP dan validator WorldGuard/GriefPrevention internal agar kegagalan BetterRTP tidak menggagalkan semua kandidat.

Ketiga JAR referensi tidak dimodifikasi.

## Perilaku Teleport

`teleport.cancel-on-move` default `false`. Player bebas bergerak selama countdown setelah kedua pemain accept maupun selama countdown Buddy TP. Logout, mati, combat (jika `cancel-on-damage: true`), atau destination yang berubah tidak aman tetap membatalkan teleport.

SafeRTP tidak membuat chunk baru bila `safe-rtp.require-generated-chunk: true`. Pencarian dibatasi worldborder dan menolak lava, void, air (default), powder snow, cactus, blok tertutup, serta lokasi yang ditolak validator integrasi.

## PlaceholderAPI

- `%friendfy_queue%`
- `%friendfy_waiting_players%`
- `%friendfy_buddy%`
- `%friendfy_buddy_distance%`
- `%friendfy_status%`
- `%friendfy_social_level%`
- `%friendfy_volunteers%`

## Penyimpanan

SQLite adalah default. MySQL/MariaDB dapat dipilih melalui `storage.yml`. Query dijalankan pada satu executor asynchronous dan tracker memakai cache, bukan query database setiap detik.
