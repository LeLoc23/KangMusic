package com.musicapp.services;

import com.musicapp.models.Playlist;
import com.musicapp.models.PlaylistItem;
import com.musicapp.models.MediaItem;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.PlaylistItemRepository;
import com.musicapp.repositories.PlaylistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final PlaylistRepository       playlistRepo;
    private final PlaylistItemRepository   playlistItemRepo;
    private final MediaItemRepository      mediaItemRepo;

    public PlaylistService(PlaylistRepository playlistRepo,
                           PlaylistItemRepository playlistItemRepo,
                           MediaItemRepository mediaItemRepo) {
        this.playlistRepo     = playlistRepo;
        this.playlistItemRepo = playlistItemRepo;
        this.mediaItemRepo    = mediaItemRepo;
    }

    // ── Đọc ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Playlist> getUserPlaylists(Long userId) {
        // LEFT JOIN FETCH để load subPlaylists ngay trong transaction
        // Tránh LazyInitializationException khi Thymeleaf render folder children
        return playlistRepo.findRootPlaylistsWithChildren(userId);
    }

    @Transactional(readOnly = true)
    public List<Playlist> getAllUserPlaylists(Long userId) {
        // CRIT-3 FIX: Proper indexed DB query — no more full-table scan + Java filter
        return playlistRepo.findByUserIdAndIsFolderFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Playlist> getUserFolders(Long userId) {
        return playlistRepo.findByUserIdAndIsFolderTrue(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Playlist> getPlaylistById(Long id) {
        return playlistRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Playlist> getPlaylistByIdAndOwner(Long id, Long userId) {
        return playlistRepo.findByIdAndUserId(id, userId);
    }

    // ── Tạo / Sửa ────────────────────────────────────────────────────────────

    public Playlist createPlaylist(Long userId, String name, String description, String coverEmoji, boolean isFolder, Long parentId) {
        Playlist p = new Playlist(name, userId);
        p.setDescription(description);
        p.setFolder(isFolder);
        if (coverEmoji != null && !coverEmoji.isBlank()) p.setCoverEmoji(coverEmoji);
        else if (isFolder) p.setCoverEmoji("📁");

        if (parentId != null) {
            playlistRepo.findByIdAndUserId(parentId, userId).ifPresent(p::setParent);
        }

        Playlist saved = playlistRepo.save(p);
        log.info("Tạo {} id={} name='{}' userId={}", isFolder ? "folder" : "playlist", saved.getId(), name, userId);
        return saved;
    }

    public void renamePlaylist(Long playlistId, Long userId, String newName) {
        playlistRepo.findByIdAndUserId(playlistId, userId).ifPresent(p -> {
            p.setName(newName);
            playlistRepo.save(p);
        });
    }

    public void moveToFolder(Long playlistId, Long userId, Long folderId) {
        playlistRepo.findByIdAndUserId(playlistId, userId).ifPresent(p -> {
            if (folderId == null) {
                p.setParent(null); // Xóa khỏi thư mục
            } else {
                playlistRepo.findByIdAndUserId(folderId, userId)
                        .filter(Playlist::isFolder)
                        .ifPresent(p::setParent);
            }
            playlistRepo.save(p);
            log.info("Di chuyển playlist id={} vào folder id={} userId={}", playlistId, folderId, userId);
        });
    }

    // ── Thêm / Xóa bài ───────────────────────────────────────────────────────

    /**
     * Thêm bài vào playlist. Nếu bài đã có → không thêm lần nữa.
     * @return true nếu thêm thành công, false nếu đã tồn tại
     */
    public boolean addToPlaylist(Long playlistId, Long userId, Long mediaItemId) {
        Playlist playlist = playlistRepo.findByIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist không tồn tại hoặc không có quyền"));

        if (playlistItemRepo.existsByPlaylistIdAndMediaItemId(playlistId, mediaItemId)) {
            return false; // đã có
        }

        MediaItem media = mediaItemRepo.findByIdAndDeletedFalse(mediaItemId)
                .orElseThrow(() -> new IllegalArgumentException("Bài hát không tồn tại"));

        int nextPos = playlistItemRepo.findMaxPosition(playlistId) + 1;
        PlaylistItem item = new PlaylistItem(playlist, media, nextPos);
        playlistItemRepo.save(item);
        log.info("Thêm bài id={} vào playlist id={}", mediaItemId, playlistId);
        return true;
    }

    public void removeFromPlaylist(Long playlistItemId, Long userId) {
        playlistItemRepo.findById(playlistItemId).ifPresent(pi -> {
            if (pi.getPlaylist().getUserId().equals(userId)) {
                playlistItemRepo.delete(pi);
                log.info("Xóa item id={} khỏi playlist", playlistItemId);
            }
        });
    }

    // ── Xóa playlist ─────────────────────────────────────────────────────────

    public void deletePlaylist(Long playlistId, Long userId) {
        playlistRepo.findByIdAndUserId(playlistId, userId).ifPresent(p -> {
            playlistRepo.delete(p);
            log.info("Xóa playlist id={} userId={}", playlistId, userId);
        });
    }
}
