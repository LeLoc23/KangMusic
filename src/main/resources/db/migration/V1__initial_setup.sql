-- V1: Initial schema â€” mirrors the entity state managed previously by ddl-auto=update
-- This is the Flyway baseline for KangMusic (H2-compatible SQL)

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(150) NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    reset_token   VARCHAR(255),
    token_expiry  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    artist        VARCHAR(150) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    emotion_label VARCHAR(100),
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Indexes (H2-compatible; for PostgreSQL add GIN/fulltext indexes in a later migration)
CREATE INDEX IF NOT EXISTS idx_media_title   ON media_items (title);
CREATE INDEX IF NOT EXISTS idx_media_artist  ON media_items (artist);
CREATE INDEX IF NOT EXISTS idx_media_type    ON media_items (type);
CREATE INDEX IF NOT EXISTS idx_media_deleted ON media_items (deleted);
CREATE INDEX IF NOT EXISTS idx_media_emotion ON media_items (emotion_label);
-- V2: Add duration_seconds column to media_items
-- Populated asynchronously via ID3 tag extraction after upload.
-- NULL = duration not yet extracted (or file has no ID3 duration tag).

ALTER TABLE media_items ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;
-- V3: Bá»• sung cÃ¡c cá»™t cÃ²n thiáº¿u trong báº£ng media_items vÃ  users
-- (Xáº£y ra khi DB Ä‘Æ°á»£c táº¡o trÆ°á»›c khi cÃ¡c tÃ­nh nÄƒng má»›i Ä‘Æ°á»£c thÃªm vÃ o)
-- DÃ¹ng IF NOT EXISTS nÃªn khÃ´ng lá»—i náº¿u cá»™t Ä‘Ã£ tá»“n táº¡i.

ALTER TABLE media_items ADD COLUMN IF NOT EXISTS deleted           BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS emotion_label     VARCHAR(100);
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS duration_seconds  INTEGER;

ALTER TABLE users ADD COLUMN IF NOT EXISTS locked              BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token         VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token_expiry  TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name           VARCHAR(255);
-- V4: ThÃªm genre, uploaded_at, play_count vÃ o media_items
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS genre       VARCHAR(50);
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS play_count  BIGINT NOT NULL DEFAULT 0;
-- V5: Táº¡o báº£ng playlist, playlist_items, user_library
CREATE TABLE IF NOT EXISTS playlists (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(500),
    cover_emoji VARCHAR(10)   DEFAULT 'ðŸŽµ',
    user_id     BIGINT        NOT NULL,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    is_public   BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS playlist_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    playlist_id   BIGINT  NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    media_item_id BIGINT  NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    position      INT     NOT NULL DEFAULT 0,
    added_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_library (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT  NOT NULL,
    media_item_id BIGINT  NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    added_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_media UNIQUE (user_id, media_item_id)
);

CREATE INDEX IF NOT EXISTS idx_playlist_user  ON playlists (user_id);
CREATE INDEX IF NOT EXISTS idx_pi_playlist    ON playlist_items (playlist_id);
CREATE INDEX IF NOT EXISTS idx_pi_media       ON playlist_items (media_item_id);
CREATE INDEX IF NOT EXISTS idx_lib_user       ON user_library (user_id);
-- V6: Táº¡o báº£ng lá»‹ch sá»­ nghe nháº¡c (dÃ¹ng cho Collaborative Filtering)
CREATE TABLE IF NOT EXISTS play_history (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT,           -- NULL náº¿u anonymous
    media_item_id    BIGINT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    played_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    seconds_listened INT
);

CREATE INDEX IF NOT EXISTS idx_ph_user   ON play_history (user_id);
CREATE INDEX IF NOT EXISTS idx_ph_media  ON play_history (media_item_id);
CREATE INDEX IF NOT EXISTS idx_ph_played ON play_history (played_at);
-- V7: ThÃªm cá»™t lyrics Ä‘á»ƒ lÆ°u lá»i bÃ i hÃ¡t (vÄƒn báº£n dÃ i)
ALTER TABLE media_items ADD COLUMN lyrics CLOB;
-- H2 dÃ¹ng CLOB cho vÄƒn báº£n dÃ i tÆ°Æ¡ng Ä‘Æ°Æ¡ng TEXT trong MySQL/PostgreSQL
-- V8: Há»— trá»£ thÆ° má»¥c lá»“ng nhau cho Playlist
ALTER TABLE playlists ADD COLUMN is_folder BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE playlists ADD COLUMN parent_id BIGINT;
ALTER TABLE playlists ADD CONSTRAINT fk_playlist_parent FOREIGN KEY (parent_id) REFERENCES playlists(id) ON DELETE CASCADE;
-- V9: ThÃªm áº£nh Poster cho Media Items
ALTER TABLE media_items ADD COLUMN poster_filename VARCHAR(255);
-- V10: Performance indexes + missing Foreign Keys
-- Fixes audit issues: DB missing FKs, composite performance indexes on play_history

-- â”€â”€ Missing FK: playlists.user_id â†’ users(id) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- H2 supports named FK constraints via ALTER TABLE
ALTER TABLE playlists
    ADD CONSTRAINT IF NOT EXISTS fk_playlist_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- â”€â”€ Missing FK: user_library.user_id â†’ users(id) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
ALTER TABLE user_library
    ADD CONSTRAINT IF NOT EXISTS fk_library_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- â”€â”€ Performance indexes on play_history â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Composite index for findRecentlyPlayedByUser (user_id + played_at)
CREATE INDEX IF NOT EXISTS idx_ph_user_played ON play_history (user_id, played_at DESC);

-- Composite index for Collaborative Filtering join (media_item_id + user_id)
CREATE INDEX IF NOT EXISTS idx_ph_media_user  ON play_history (media_item_id, user_id);

-- â”€â”€ Index on users.email for lookup speed â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
