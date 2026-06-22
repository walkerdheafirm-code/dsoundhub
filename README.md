# PRD – D'SoundHub
### Platform Distribusi Audio & Manajemen Royalti Berbasis Multi-Tenant

> **Versi:** 1.0.0 – Draft | **Tanggal:** Juni 2026 | **Status:** In Review

---

## Daftar Isi

1. [Ringkasan Eksekutif](#1-ringkasan-eksekutif)
2. [Lingkup & Batasan Proyek](#2-lingkup--batasan-proyek)
3. [Arsitektur Sistem](#3-arsitektur-sistem)
4. [Kebutuhan Fungsional](#4-kebutuhan-fungsional)
5. [Kebutuhan Non-Fungsional](#5-kebutuhan-non-fungsional)
6. [Model Data](#6-model-data-entitas-utama)
7. [Desain API Endpoint](#7-desain-api-endpoint)
8. [Alur Keamanan](#8-alur-keamanan-security-flow)
9. [Stack Teknologi](#9-stack-teknologi)
10. [Struktur Direktori Project](#10-struktur-direktori-project)
11. [Kriteria Penerimaan](#11-kriteria-penerimaan-acceptance-criteria)
12. [Risiko & Mitigasi](#12-risiko--mitigasi)
13. [Milestone & Estimasi](#13-milestone--estimasi-pengerjaan)
14. [Pertanyaan Terbuka](#14-pertanyaan-terbuka-open-questions)
15. [Referensi](#15-referensi)

---

## 1. Ringkasan Eksekutif

D'SoundHub adalah aplikasi berbasis **Java Microservices** yang memungkinkan artis mengunggah karya musik, listener membeli dan memutar lagu, serta admin mengelola ekosistem platform secara keseluruhan.

Sistem dirancang dengan arsitektur **multi-tenant** yang terpecah menjadi dua layanan independen, menggabungkan mekanisme keamanan **Stateless (JWT)** dan **Stateful (Redis Session)** untuk menjamin performa sekaligus keamanan data secara real-time.

**Tujuan utama produk:**
- Menyediakan platform distribusi musik digital yang aman dan skalabel.
- Mengotomasi perhitungan dan distribusi royalti kepada artis.
- Mengimplementasikan arsitektur keamanan hybrid (JWT + Session) sebagai studi kasus praktis.
- Memberikan kontrol penuh kepada Admin untuk moderasi pengguna secara real-time.

---

## 2. Lingkup & Batasan Proyek

### 2.1 Dalam Lingkup (In-Scope)

- Dua microservice independen: `auth-service` dan `audio-service`.
- Sistem autentikasi dan otorisasi berbasis Role (Admin, Artist, Listener).
- Fitur upload, streaming cuplikan, dan pembelian lagu.
- Perhitungan dan pencatatan royalti per transaksi.
- Dashboard Admin untuk manajemen user (Ban/Unban).
- Antarmuka web sederhana (HTML/JS) untuk interaksi pengguna.

### 2.2 Di Luar Lingkup (Out-of-Scope)

- Pembayaran nyata (payment gateway terintegrasi).
- Aplikasi mobile native (iOS/Android).
- Rekomendasi lagu berbasis AI/ML.
- Streaming lagu penuh (hanya cuplikan/preview 30 detik).
- Integrasi media sosial.

---

## 3. Arsitektur Sistem

### 3.1 Gambaran Umum Microservices

Sistem terdiri dari dua service yang berjalan secara independen dan berkomunikasi melalui REST API. Setiap service memiliki database tersendiri (**database-per-service pattern**) untuk menjamin isolasi data.

| Service | Port | Tanggung Jawab | Database |
|---|---|---|---|
| `auth-service` | 8081 | Registrasi, login, manajemen user, session admin | PostgreSQL (`users_db`) |
| `audio-service` | 8082 | Upload lagu, streaming, transaksi beli, royalti | PostgreSQL (`audio_db`) |
| Redis | 6379 | Penyimpanan session stateful & cache status ban | In-Memory Store |

### 3.2 Pola Komunikasi Antar Service

- **auth-service → audio-service:** `audio-service` memvalidasi JWT token ke `auth-service` melalui endpoint internal `/internal/validate-token`.
- **Redis:** Diakses langsung oleh `auth-service` untuk sesi admin dan status ban/unban user.
- Semua endpoint publik dikonsumsi oleh frontend (HTML/JS) melalui `fetch` API.

### 3.3 Diagram Alur Sistem (High-Level)

```
Browser / Frontend (HTML + JS)
        │
        ├──── POST /api/auth/**  ──────────────────► auth-service (:8081)
        │                                                  │
        │                                           ┌──────┴──────┐
        │                                       PostgreSQL      Redis
        │                                       (users_db)   (sessions +
        │                                                      ban flags)
        │
        └──── GET|POST /api/audio/** ──────────► audio-service (:8082)
                                                       │
                                           ┌───────────┼───────────┐
                                       PostgreSQL    Redis      auth-service
                                       (audio_db)  (ban check)  (token validation)
```

---

## 4. Kebutuhan Fungsional

### 4.1 Autentikasi (`auth-service`)

#### 4.1.1 Registrasi Pengguna

- User dapat mendaftar dengan `username`, `email`, `password`, dan memilih role (`ARTIST` / `LISTENER`).
- Password di-hash menggunakan **BCrypt** sebelum disimpan.
- `username` dan `email` harus unik; validasi dilakukan sebelum penyimpanan.
- Role `ADMIN` hanya dapat ditetapkan secara manual oleh administrator database (tidak tersedia di form registrasi publik).

#### 4.1.2 Login & Penerbitan Token

- Semua role (Admin, Artist, Listener) menggunakan halaman `login.html` yang sama.
- Setelah kredensial valid diverifikasi, `auth-service` menerbitkan **JWT Token** yang memuat:
  - `userId`, `username`, `role`, `iat` (issued-at), `exp` (expiry – default 1 jam).
- Untuk role `ADMIN`: selain JWT, sistem juga membuat **HTTP Session di Redis** (Stateful) yang menyimpan `adminId` dan `sessionCreatedAt`.
- Respons login mengembalikan JWT token yang disimpan di `localStorage` browser.

#### 4.1.3 Manajemen Session & Ban/Unban (Stateful)

- Admin dapat melakukan **Ban** terhadap user. Status ban disimpan sebagai flag di Redis dengan key `user:ban:{userId}`.
- Saat user ter-ban mencoba mengakses endpoint manapun di `audio-service`, token JWT-nya masih valid, **namun** interceptor akan memeriksa Redis dan menolak request dengan **HTTP 403 Forbidden**.
- Admin dapat melakukan **Unban** yang menghapus key Redis tersebut secara instan (real-time).
- Admin logout akan menghapus session Redis yang bersangkutan.

### 4.2 Streaming & Distribusi Audio (`audio-service`)

#### 4.2.1 Upload Lagu (`ROLE_ARTIST`)

- Artist dapat mengunggah file audio (MP3/WAV, maks. 50MB) beserta metadata: judul, genre, harga (dalam poin virtual), durasi.
- File disimpan di server lokal (direktori `/uploads`) atau object storage yang dikonfigurasi.
- Setiap lagu terhubung ke `artistId` yang diambil dari JWT token.
- Artist dapat melihat daftar lagu yang diunggah beserta total royalti yang diterima.

#### 4.2.2 Streaming Cuplikan (`ROLE_LISTENER` & `ROLE_ARTIST`)

- Listener dapat memutar cuplikan lagu **(30 detik pertama)** tanpa biaya menggunakan JWT token.
- Streaming cuplikan menggunakan **HTTP Range Request** untuk efisiensi bandwidth.
- Endpoint: `GET /api/audio/preview/{songId}` – memerlukan JWT valid.

#### 4.2.3 Pembelian Lagu (`ROLE_LISTENER`)

- Listener dapat membeli lagu seharga yang ditentukan artis menggunakan saldo virtual (poin).
- Setelah pembelian berhasil, Listener dapat mengakses file lagu penuh.
- Satu lagu hanya bisa dibeli **sekali** per Listener (duplikasi dicegah via unique constraint).

#### 4.2.4 Perhitungan Royalti

- Setiap transaksi pembelian memicu kalkulasi royalti secara otomatis.
- **Formula royalti:** 70% dari harga lagu → Artist | 30% → Pendapatan platform.
- Royalti dicatat di tabel `royalties` dengan status `PENDING` → `SETTLED` (batch harian).
- Artist dapat melihat rincian royalti per lagu melalui endpoint `GET /api/royalties/my`.

---

## 5. Kebutuhan Non-Fungsional

| Kategori | Kebutuhan | Target |
|---|---|---|
| Performa | Waktu respons API (P95) | < 300ms |
| Keamanan | Enkripsi password | BCrypt strength 12 |
| Keamanan | JWT expiry | 1 jam (access token) |
| Ketersediaan | Uptime target | 99.5% (non-production) |
| Skalabilitas | Horizontal scaling | Setiap service dapat di-scale independen |
| Logging | Request logging | Setiap request tercatat dengan `traceId` |
| Validasi | Input sanitization | Semua input divalidasi dengan Bean Validation (`jakarta.validation`) |
| CORS | Cross-origin policy | Dikonfigurasi per service via Spring Security |

---

## 6. Model Data (Entitas Utama)

### 6.1 `auth-service` Database — `users_db`

**Tabel: `users`**

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `UUID` (PK) | Primary key, auto-generate |
| `username` | `VARCHAR(50)` | Unik, NOT NULL |
| `email` | `VARCHAR(100)` | Unik, NOT NULL |
| `password_hash` | `VARCHAR(255)` | BCrypt hash |
| `role` | `ENUM` | `ADMIN`, `ARTIST`, `LISTENER` |
| `is_banned` | `BOOLEAN` | Default `false`; sinkron dengan Redis |
| `balance` | `DECIMAL(10,2)` | Saldo virtual listener (default 0) |
| `created_at` | `TIMESTAMP` | Waktu registrasi |
| `updated_at` | `TIMESTAMP` | Waktu update terakhir |

### 6.2 `audio-service` Database — `audio_db`

**Tabel: `songs`**

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `UUID` (PK) | Primary key |
| `artist_id` | `UUID` | Referensi ke `users.id` di `auth-service` |
| `title` | `VARCHAR(200)` | Judul lagu |
| `genre` | `VARCHAR(50)` | Genre musik |
| `file_path` | `VARCHAR(500)` | Path file audio di server |
| `duration_seconds` | `INTEGER` | Durasi total lagu |
| `price` | `DECIMAL(10,2)` | Harga dalam poin virtual |
| `total_plays` | `INTEGER` | Counter streaming cuplikan |
| `status` | `VARCHAR(20)` | `PUBLISHED`, `UNPUBLISHED`, atau `DELETED` |
| `created_at` | `TIMESTAMP` | Waktu upload |

**Tabel: `transactions`**

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `UUID` (PK) | Primary key |
| `listener_id` | `UUID` | Referensi ke `users.id` |
| `song_id` | `UUID` (FK) | Referensi ke `songs.id` |
| `amount` | `DECIMAL(10,2)` | Jumlah yang dibayar |
| `status` | `ENUM` | `PENDING`, `COMPLETED`, `FAILED` |
| `created_at` | `TIMESTAMP` | Waktu transaksi |

**Tabel: `royalties`**

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `UUID` (PK) | Primary key |
| `transaction_id` | `UUID` (FK) | Referensi ke `transactions.id` |
| `artist_id` | `UUID` | Referensi ke `users.id` (artist) |
| `amount` | `DECIMAL(10,2)` | 70% dari harga transaksi |
| `status` | `ENUM` | `PENDING`, `SETTLED` |
| `settled_at` | `TIMESTAMP` | Waktu penyelesaian (nullable) |

---

## 7. Desain API Endpoint

### 7.1 `auth-service` (Port 8081)

| Method | Endpoint | Role | Deskripsi |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Registrasi user baru |
| `POST` | `/api/auth/login` | Public | Login & terima JWT + session admin |
| `POST` | `/api/auth/logout` | Authenticated | Hapus session & invalidasi token |
| `GET` | `/api/admin/users` | `ADMIN` | Daftar semua user |
| `PUT` | `/api/admin/users/{id}/ban` | `ADMIN` | Ban user (tulis ke Redis) |
| `PUT` | `/api/admin/users/{id}/unban` | `ADMIN` | Unban user (hapus dari Redis) |
| `GET` | `/internal/validate-token` | Internal | Validasi JWT dari `audio-service` |

### 7.2 `audio-service` (Port 8082)

| Method | Endpoint | Role | Deskripsi |
|---|---|---|---|
| `POST` | `/api/audio/upload` | `ARTIST` | Upload file lagu + metadata |
| `GET` | `/api/audio/songs` | Authenticated | Daftar semua lagu tersedia |
| `GET` | `/api/audio/preview/{id}` | Authenticated | Stream 30 detik pertama lagu |
| `POST` | `/api/audio/purchase/{id}` | `LISTENER` | Beli lagu dengan saldo virtual |
| `GET` | `/api/audio/my-library` | `LISTENER` | Lagu yang sudah dibeli |
| `GET` | `/api/audio/my-songs` | `ARTIST` | Lagu yang diunggah artist |
| `PATCH` | `/api/audio/my-songs/{id}/publication` | `ARTIST` | Tarik lagu dari publik atau publish ulang |
| `DELETE` | `/api/audio/my-songs/{id}` | `ARTIST` | Hapus lagu; akses pembeli lama tetap dipertahankan |
| `GET` | `/api/royalties/my` | `ARTIST` | Rincian royalti artist |
| `GET` | `/api/royalties/summary` | `ADMIN` | Ringkasan royalti semua artist |

---

## 8. Alur Keamanan (Security Flow)

### 8.1 Alur Login & Penerbitan Token

```
User
 │
 ├─ 1. Buka login.html, isi username + password
 │
 ├─ 2. POST /api/auth/login ──────────────────► auth-service
 │                                                    │
 │                                         3. Cek DB → verifikasi BCrypt
 │                                                    │
 │                                         4. Buat JWT (userId, role, exp)
 │                                                    │
 │                                    [jika ADMIN] 5. Buat Session di Redis
 │                                                    │
 └─ 6. Terima JWT ◄───────────────────────────────────┘
        │
        └─ Simpan di localStorage
```

### 8.2 Alur Akses Resource Terproteksi

```
User
 │
 ├─ 1. Request ke audio-service + Header: Authorization: Bearer {token}
 │
 ▼
audio-service (:8082)
 │
 ├─ 2. JwtAuthenticationFilter → ekstrak & validasi JWT (signature + expiry)
 │
 ├─ 3. BanCheckInterceptor → cek Redis: apakah user:ban:{userId} ada?
 │         └─ YA  → HTTP 403 "Account suspended"
 │         └─ TIDAK → lanjut
 │
 ├─ 4. Spring Security → cek role dari JWT vs. izin endpoint
 │         └─ Tidak cukup role → HTTP 403 "Access denied"
 │         └─ Role OK → lanjut
 │
 └─ 5. Request diteruskan ke Service Layer → proses bisnis
```

### 8.3 Alur Ban Real-Time

```
Admin
 │
 ├─ 1. Klik tombol "Ban" pada dashboard
 │
 ├─ 2. PUT /api/admin/users/{id}/ban ────────► auth-service
 │                                                  │
 │                                    3. Validasi session Admin di Redis
 │                                                  │
 │                                    4. Tulis key: user:ban:{userId} = true
 │                                                  │
 └─ 5. HTTP 200 OK ◄────────────────────────────────┘

[Saat user ter-ban melakukan request berikutnya]

User ──► audio-service ──► BanCheckInterceptor ──► Redis (baca key) ──► HTTP 403
                                                    (real-time, tanpa tunggu JWT expired)
```

---

## 9. Stack Teknologi

| Komponen | Teknologi | Versi |
|---|---|---|
| Bahasa | Java | **21 (LTS)** |
| Framework | Spring Boot | 3.x |
| Keamanan | Spring Security | 6.x |
| Token | JJWT (`io.jsonwebtoken`) | 0.12.x |
| Session/Cache | Redis + Spring Session | 7.x / 3.x |
| ORM | Spring Data JPA + Hibernate | 3.x |
| Database | PostgreSQL | 15+ |
| Build Tool | Maven atau Gradle | - |
| Dokumentasi API | Springdoc OpenAPI (Swagger) | 2.x |
| Containerisasi | Docker + Docker Compose | 24+ |

> **Catatan Java 21:** Manfaatkan fitur modern yang tersedia di Java 21, antara lain:
> - **Virtual Threads** (Project Loom) via `spring.threads.virtual.enabled=true` untuk meningkatkan throughput I/O-bound di kedua service.
> - **Pattern Matching for `switch`** (finalized) untuk penanganan role dan status yang lebih ekspresif.
> - **Sequenced Collections** untuk manipulasi list hasil query yang lebih readable.
> - **Record classes** untuk DTO (Data Transfer Object) yang immutable dan ringkas.

---

## 10. Struktur Direktori Project

```
dsoundhub/
├── docker-compose.yml
├── auth-service/
│   ├── pom.xml
│   └── src/main/java/com/dsoundhub/auth/
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── RedisConfig.java
│       │   └── JwtConfig.java
│       ├── controller/
│       │   ├── AuthController.java
│       │   └── AdminController.java
│       ├── service/
│       │   ├── AuthService.java
│       │   ├── UserService.java
│       │   └── SessionService.java
│       ├── repository/
│       │   └── UserRepository.java
│       ├── entity/
│       │   ├── User.java
│       │   └── Role.java          ← enum: ADMIN, ARTIST, LISTENER
│       ├── dto/                   ← gunakan Java Records
│       │   ├── LoginRequest.java
│       │   ├── RegisterRequest.java
│       │   └── TokenResponse.java
│       ├── security/
│       │   ├── JwtProvider.java
│       │   └── JwtFilter.java
│       └── exception/
│           └── GlobalExceptionHandler.java
│
└── audio-service/
    ├── pom.xml
    └── src/main/java/com/dsoundhub/audio/
        ├── config/
        │   ├── SecurityConfig.java
        │   └── RedisConfig.java
        ├── controller/
        │   ├── AudioController.java
        │   └── RoyaltyController.java
        ├── service/
        │   ├── AudioService.java
        │   ├── TransactionService.java
        │   └── RoyaltyService.java
        ├── repository/
        │   ├── SongRepository.java
        │   ├── TransactionRepository.java
        │   └── RoyaltyRepository.java
        ├── entity/
        │   ├── Song.java
        │   ├── Transaction.java
        │   └── Royalty.java
        ├── dto/                   ← gunakan Java Records
        │   ├── SongRequest.java
        │   ├── PurchaseResponse.java
        │   └── RoyaltySummary.java
        ├── security/
        │   ├── JwtValidationFilter.java
        │   └── BanCheckInterceptor.java
        └── exception/
            └── GlobalExceptionHandler.java
```

---

## 11. Kriteria Penerimaan (Acceptance Criteria)

### Auth Service

| ID | Kriteria |
|---|---|
| AC-01 | User baru dapat mendaftar dengan role Artist atau Listener; validasi duplikat username/email berjalan. |
| AC-02 | Login menghasilkan JWT token valid yang dapat di-decode dan menampilkan role yang benar. |
| AC-03 | Login Admin menghasilkan JWT token **dan** session Redis yang dapat diverifikasi. |
| AC-04 | Admin dapat melakukan ban; request selanjutnya dari user ter-ban ditolak dalam < 1 detik. |
| AC-05 | Admin logout menghapus session Redis; session tidak lagi valid setelah logout. |

### Audio Service

| ID | Kriteria |
|---|---|
| AC-06 | Artist dapat mengupload file audio; metadata tersimpan di database. |
| AC-07 | Listener dapat mengakses preview 30 detik dengan JWT valid. |
| AC-08 | Listener dapat membeli lagu; saldo berkurang dan entri transaksi terbuat. |
| AC-09 | Pembelian duplikat (lagu yang sama) ditolak dengan pesan error yang sesuai. |
| AC-10 | Royalti 70% terhitung otomatis setiap kali transaksi `COMPLETED` terbuat. |
| AC-11 | User dengan role `LISTENER` yang mencoba upload ditolak dengan **HTTP 403**. |
| AC-12 | Artist dapat menarik dan mempublikasikan ulang lagu miliknya tanpa memengaruhi library pembeli. |
| AC-13 | Lagu yang dihapus hilang dari katalog; jika pernah dibeli, file dan histori transaksi tetap tersedia bagi pembeli. |
| AC-14 | Lagu nonpublik tidak dapat dibeli atau diputar oleh listener yang tidak memilikinya. |

### Keamanan

| ID | Kriteria |
|---|---|
| AC-15 | Request tanpa JWT ke endpoint terproteksi ditolak dengan **HTTP 401**. |
| AC-16 | JWT kadaluarsa menghasilkan **HTTP 401** dengan pesan `"Token expired"`. |
| AC-17 | User ter-ban dengan JWT masih valid ditolak dengan **HTTP 403** `"Account suspended"`. |

---

## 12. Risiko & Mitigasi

| Risiko | Probabilitas | Dampak | Mitigasi |
|---|---|---|---|
| Redis down menyebabkan ban tidak terbaca | Rendah | Tinggi | Fallback: baca status `is_banned` dari DB jika Redis timeout |
| JWT tidak di-revoke saat ban | Sedang | Sedang | Dimitigasi dengan pengecekan Redis real-time di setiap request |
| Race condition pada transaksi pembelian | Sedang | Tinggi | `@Transactional` + database-level unique constraint pada `(listener_id, song_id)` |
| File audio besar memenuhi disk | Sedang | Sedang | Validasi ukuran file (maks. 50MB) + pertimbangkan object storage |
| Inter-service latency validasi token | Rendah | Rendah | Cache hasil validasi token di `audio-service` dengan TTL pendek |

---

## 13. Milestone & Estimasi Pengerjaan

| Fase | Deliverable | Estimasi |
|---|---|---|
| **Fase 1** – Foundation | Setup project, konfigurasi DB, Docker Compose | 3–4 hari |
| **Fase 2** – Auth Core | Registrasi, login, JWT provider, BCrypt | 4–5 hari |
| **Fase 3** – Security Hybrid | Redis session, ban interceptor, Spring Security config | 4–5 hari |
| **Fase 4** – Audio Core | Upload, preview streaming, metadata management | 5–6 hari |
| **Fase 5** – Transaksi & Royalti | Pembelian, kalkulasi royalti, laporan | 4–5 hari |
| **Fase 6** – Frontend & Integrasi | HTML/JS pages, testing end-to-end | 3–4 hari |
| **Fase 7** – QA & Polish | Bug fixing, Swagger docs, code review | 2–3 hari |
| **Total** | | **~25–32 hari kerja** |

---

## 14. Pertanyaan Terbuka (Open Questions)

| ID | Pertanyaan |
|---|---|
| OQ-01 | Apakah saldo virtual listener di-top-up secara manual (oleh admin) atau melalui mekanisme tertentu? |
| OQ-02 | Apakah refresh token diperlukan, atau cukup dengan expiry JWT 1 jam dan re-login? |
| OQ-03 | Format file audio apa saja yang didukung selain MP3? (WAV, FLAC, AAC?) |
| OQ-04 | Apakah diperlukan notifikasi (email/in-app) saat user di-ban atau royalti di-settle? |
| OQ-05 | Bagaimana penanganan data lagu ketika artist di-ban? Apakah lagu masih bisa dibeli? |
| OQ-06 | Apakah diperlukan fitur search/filter lagu berdasarkan genre atau judul? |
| OQ-07 | Target deployment: lokal (localhost), cloud VM, atau container orchestration (K8s)? |

---

## 15. Referensi

- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [JJWT Library](https://github.com/jwtk/jjwt)
- [Spring Session with Redis](https://docs.spring.io/spring-session/reference/)
- [Spring Boot Microservices Guide](https://spring.io/microservices)
- [Virtual Threads di Spring Boot 3.2+](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.virtual-threads)
- [Docker Compose Networking](https://docs.docker.com/compose/networking/)

---

## 16. Development Setup

### Prerequisites

Pastikan tools berikut sudah terinstall sebelum memulai:

- Java 21 LTS
- Maven atau Gradle
- Docker & Docker Compose
- Git

### Clone & Jalankan

```bash
# Clone repository
git clone https://github.com/username/dsoundhub.git
cd dsoundhub

# Jalankan semua service (DB + Redis + kedua service) sekaligus
docker-compose up -d

# Atau jalankan masing-masing service secara manual (untuk development)
cd auth-service && ./mvnw spring-boot:run
cd audio-service && ./mvnw spring-boot:run
```

### Commit Convention

Untuk menjaga riwayat commit tetap rapi dan mudah dibaca, gunakan format berikut:

```
<type>(<scope>): <pesan singkat>

Contoh:
feat(auth): add JWT token generation on login
fix(audio): resolve race condition on song purchase
chore(docker): update postgres image to 15-alpine
refactor(auth): convert LoginRequest to Java Record
```

| Type | Kapan dipakai |
|---|---|
| `feat` | Menambah fitur baru |
| `fix` | Memperbaiki bug |
| `refactor` | Restrukturisasi kode tanpa mengubah perilaku |
| `chore` | Update konfigurasi, dependency, atau tooling |
| `docs` | Perubahan dokumentasi / README |
| `test` | Menambah atau memperbaiki unit/integration test |

### Rekomendasi Struktur Branch

Meski proyek solo, tetap disarankan memisahkan branch agar mudah rollback jika ada yang bermasalah:

```
main          ← kode stabil / siap demo
└── dev       ← branch utama pengerjaan harian
    ├── feat/auth-jwt
    ├── feat/audio-upload
    └── fix/ban-interceptor
```

---

*Dokumen ini bersifat living document — update seiring perkembangan requirement.*
