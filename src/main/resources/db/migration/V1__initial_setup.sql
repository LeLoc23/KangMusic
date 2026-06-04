-- SQL Server baseline schema for KangMusic.

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        username NVARCHAR(50) NOT NULL UNIQUE,
        password NVARCHAR(255) NOT NULL,
        email NVARCHAR(100) NOT NULL UNIQUE,
        full_name NVARCHAR(100),
        phone_number NVARCHAR(30),
        role NVARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
        locked BIT NOT NULL DEFAULT 0,
        reset_token NVARCHAR(255),
        reset_token_expiry DATETIME2
    );
END;

IF OBJECT_ID(N'dbo.creator_profiles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.creator_profiles (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE,
        stage_name NVARCHAR(150) NOT NULL,
        bio NVARCHAR(1000),
        status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
        requested_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        reviewed_at DATETIME2,
        reviewed_by NVARCHAR(100),
        rejection_reason NVARCHAR(500),
        CONSTRAINT fk_creator_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE
    );
END;

IF OBJECT_ID(N'dbo.media_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.media_items (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(150) NOT NULL,
        artist NVARCHAR(150) NOT NULL,
        file_name NVARCHAR(255) NOT NULL,
        type NVARCHAR(20) NOT NULL,
        emotion_label NVARCHAR(100),
        genre NVARCHAR(50),
        album NVARCHAR(150),
        uploaded_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        play_count BIGINT NOT NULL DEFAULT 0,
        uploaded_by_user_id BIGINT,
        approval_status NVARCHAR(20) NOT NULL DEFAULT 'APPROVED',
        duration_seconds INT,
        deleted BIT NOT NULL DEFAULT 0,
        lyrics NVARCHAR(MAX),
        poster_filename NVARCHAR(255),
        CONSTRAINT fk_media_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES dbo.users(id)
    );
END;

IF OBJECT_ID(N'dbo.media_creators', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.media_creators (
        media_item_id BIGINT NOT NULL,
        creator_profile_id BIGINT NOT NULL,
        PRIMARY KEY (media_item_id, creator_profile_id),
        CONSTRAINT fk_media_creators_media FOREIGN KEY (media_item_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE,
        CONSTRAINT fk_media_creators_creator FOREIGN KEY (creator_profile_id) REFERENCES dbo.creator_profiles(id) ON DELETE CASCADE
    );
END;

IF OBJECT_ID(N'dbo.playlists', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.playlists (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        name NVARCHAR(150) NOT NULL,
        description NVARCHAR(500),
        cover_emoji NVARCHAR(10) DEFAULT N'🎵',
        user_id BIGINT NOT NULL,
        created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        is_public BIT NOT NULL DEFAULT 0,
        is_folder BIT NOT NULL DEFAULT 0,
        parent_id BIGINT,
        CONSTRAINT fk_playlist_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT fk_playlist_parent FOREIGN KEY (parent_id) REFERENCES dbo.playlists(id)
    );
END;

IF OBJECT_ID(N'dbo.playlist_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.playlist_items (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        playlist_id BIGINT NOT NULL,
        media_item_id BIGINT NOT NULL,
        position INT NOT NULL DEFAULT 0,
        added_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_pi_playlist FOREIGN KEY (playlist_id) REFERENCES dbo.playlists(id) ON DELETE CASCADE,
        CONSTRAINT fk_pi_media FOREIGN KEY (media_item_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE
    );
END;

IF OBJECT_ID(N'dbo.user_library', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_library (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        media_item_id BIGINT NOT NULL,
        added_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        CONSTRAINT uq_user_media UNIQUE (user_id, media_item_id),
        CONSTRAINT fk_library_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT fk_library_media FOREIGN KEY (media_item_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE
    );
END;

IF OBJECT_ID(N'dbo.play_history', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.play_history (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT,
        media_item_id BIGINT NOT NULL,
        played_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        seconds_listened INT,
        CONSTRAINT fk_ph_media FOREIGN KEY (media_item_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE
    );
END;

IF OBJECT_ID(N'dbo.media_comments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.media_comments (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        media_item_id BIGINT NOT NULL,
        content NVARCHAR(1000) NOT NULL,
        timestamp_seconds INT,
        created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
        CONSTRAINT fk_comment_media FOREIGN KEY (media_item_id) REFERENCES dbo.media_items(id) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_media_title' AND object_id = OBJECT_ID('dbo.media_items'))
    CREATE INDEX idx_media_title ON dbo.media_items(title);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_media_artist' AND object_id = OBJECT_ID('dbo.media_items'))
    CREATE INDEX idx_media_artist ON dbo.media_items(artist);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_media_genre' AND object_id = OBJECT_ID('dbo.media_items'))
    CREATE INDEX idx_media_genre ON dbo.media_items(genre);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_media_approval' AND object_id = OBJECT_ID('dbo.media_items'))
    CREATE INDEX idx_media_approval ON dbo.media_items(approval_status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_creator_status' AND object_id = OBJECT_ID('dbo.creator_profiles'))
    CREATE INDEX idx_creator_status ON dbo.creator_profiles(status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_ph_user_played' AND object_id = OBJECT_ID('dbo.play_history'))
    CREATE INDEX idx_ph_user_played ON dbo.play_history(user_id, played_at DESC);
