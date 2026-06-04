-- Keep existing SQL Server databases aligned with the current JPA model.

IF COL_LENGTH('dbo.users', 'email_verified') IS NULL
    ALTER TABLE dbo.users ADD email_verified BIT NOT NULL CONSTRAINT df_users_email_verified DEFAULT 0;

IF COL_LENGTH('dbo.users', 'email_verification_code') IS NULL
    ALTER TABLE dbo.users ADD email_verification_code NVARCHAR(6);

IF COL_LENGTH('dbo.users', 'email_verification_expiry') IS NULL
    ALTER TABLE dbo.users ADD email_verification_expiry DATETIME2;

IF COL_LENGTH('dbo.media_items', 'approval_status') IS NULL
    ALTER TABLE dbo.media_items ADD approval_status NVARCHAR(20) NOT NULL CONSTRAINT df_media_items_approval_status DEFAULT 'APPROVED';

IF COL_LENGTH('dbo.media_items', 'lyrics_status') IS NULL
    ALTER TABLE dbo.media_items ADD lyrics_status NVARCHAR(20) NOT NULL CONSTRAINT df_media_items_lyrics_status DEFAULT 'NONE';

IF OBJECT_ID(N'dbo.song_lyrics', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.song_lyrics (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        song_id BIGINT NOT NULL UNIQUE,
        lyrics_text NVARCHAR(MAX),
        lyrics_json NVARCHAR(MAX),
        format NVARCHAR(50),
        generated_by NVARCHAR(100),
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2,
        CONSTRAINT fk_song_lyrics_media FOREIGN KEY (song_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_creator_stage_name' AND object_id = OBJECT_ID('dbo.creator_profiles'))
    CREATE INDEX idx_creator_stage_name ON dbo.creator_profiles(stage_name);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_playlist_user' AND object_id = OBJECT_ID('dbo.playlists'))
    CREATE INDEX idx_playlist_user ON dbo.playlists(user_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_comment_media' AND object_id = OBJECT_ID('dbo.media_comments'))
    CREATE INDEX idx_comment_media ON dbo.media_comments(media_item_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_lyrics_song_id' AND object_id = OBJECT_ID('dbo.song_lyrics'))
    CREATE INDEX idx_lyrics_song_id ON dbo.song_lyrics(song_id);
