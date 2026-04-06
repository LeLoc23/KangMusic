package com.musicapp.services;

import com.musicapp.models.MediaItem;
import com.musicapp.models.MediaType;
import com.musicapp.repositories.MediaItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "mp4", "flac", "ogg", "wav", "m4a", "webm");

    /** Số bài hát mỗi trang trên trang chủ. */
    public static final int PAGE_SIZE = 20;

    /** Giới hạn tối đa cho trang admin — tránh OOM. */
    public static final int ADMIN_PAGE_SIZE = 200;

    private final MediaItemRepository mediaItemRepository;
    private final StorageService storageService;
    private final FallbackLyricsService lyricGenerator;

    public MediaService(MediaItemRepository mediaItemRepository, 
                        StorageService storageService,
                        FallbackLyricsService lyricGenerator) {
        this.mediaItemRepository = mediaItemRepository;
        this.storageService = storageService;
        this.lyricGenerator = lyricGenerator;
    }

    // ── Đọc ──────────────────────────────────────────────────────────────────

    /** Tìm kiếm có phân trang, lọc theo từ khóa và thể loại. */
    @Transactional(readOnly = true)
    public Page<MediaItem> findPaginated(String query, String genre, Pageable pageable) {
        return mediaItemRepository.searchActive(query, genre, pageable);
    }

    /** Overload không có genre — giữ tương thích ngược */
    @Transactional(readOnly = true)
    public Page<MediaItem> findPaginated(String query, Pageable pageable) {
        return findPaginated(query, null, pageable);
    }

    /** Lấy danh sách có giới hạn cho trang admin. */
    @Transactional(readOnly = true)
    public List<MediaItem> findAllForAdmin() {
        return mediaItemRepository
                .findAllActive(PageRequest.of(0, ADMIN_PAGE_SIZE))
                .getContent();
    }

    /** 10 bài mới nhất — mục "Nhạc mới ra". */
    @Transactional(readOnly = true)
    public List<MediaItem> findNewReleases(int limit) {
        return mediaItemRepository.findNewReleases(PageRequest.of(0, limit)).getContent();
    }

    /** Danh sách bài cùng thể loại — dùng cho auto-fill queue. */
    @Transactional(readOnly = true)
    public List<MediaItem> findByGenre(String genre, int limit) {
        return mediaItemRepository.findByGenreActive(genre, PageRequest.of(0, limit)).getContent();
    }

    /** Danh sách bài cùng thể loại/cảm xúc, loại trừ bài hiện tại — khuyến nghị đơn giản. */
    @Transactional(readOnly = true)
    public List<MediaItem> findSimilar(Long excludeId, String genre, String emotionLabel, int limit) {
        return mediaItemRepository.findSimilar(excludeId, genre, emotionLabel, PageRequest.of(0, limit)).getContent();
    }

    /** Lấy một bài hát theo ID (trả về null nếu không tồn tại hoặc đã xóa mềm). */
    @Transactional(readOnly = true)
    public MediaItem findById(Long id) {
        return mediaItemRepository.findByIdAndDeletedFalse(id).orElse(null);
    }

    // ── Ghi ──────────────────────────────────────────────────────────────────

    /**
     * Xác thực và lưu file nhạc được upload.
     */
    public void saveMedia(String title, String artist, MultipartFile file,
                          MultipartFile posterFile,
                          String type, String emotionLabel,
                          Integer durationSeconds, String genre, String lyrics) throws IOException {

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "");

        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalName.substring(dotIdx + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "Loại file không được phép. Chỉ chấp nhận: " + ALLOWED_EXTENSIONS);
        }

        // MAJOR-1 FIX: parse via safe enum method (defaults AUDIO on unknown value)
        MediaType mediaType = MediaItem.parseType(type);

        String fileKey = storageService.store(file, ext);
        
        // Poster xử lý
        String posterKey = null;
        if (posterFile != null && !posterFile.isEmpty()) {
            String pOrig = posterFile.getOriginalFilename();
            String pExt = "jpg";
            if (pOrig != null && pOrig.contains(".")) {
                pExt = pOrig.substring(pOrig.lastIndexOf(".") + 1).toLowerCase();
            }
            posterKey = storageService.store(posterFile, pExt);
        }

        // Tự tạo Lyric nếu trống
        if (lyrics == null || lyrics.trim().isEmpty()) {
            lyrics = lyricGenerator.generateLyrics(title, artist);
        }

        MediaItem item = new MediaItem(title, artist, fileKey, mediaType.name(), emotionLabel);
        item.setDurationSeconds(durationSeconds);
        item.setGenre(genre);
        item.setLyrics(lyrics);
        item.setPosterFilename(posterKey);
        mediaItemRepository.save(item);

        log.info("Đã lưu bài hát id={} title='{}' genre='{}'", item.getId(), title, genre);
    }

    public void saveMedia(String title, String artist, MultipartFile file,
                          MultipartFile posterFile,
                          String type, String emotionLabel,
                          Integer durationSeconds, String genre) throws IOException {
        saveMedia(title, artist, file, posterFile, type, emotionLabel, durationSeconds, genre, null);
    }

    /** @deprecated Prefer MediaItemRepository.incrementPlayCount() for atomic update. */
    @Deprecated
    public void incrementPlayCount(Long id) {
        mediaItemRepository.findByIdAndDeletedFalse(id).ifPresent(item -> {
            item.incrementPlayCount();
            mediaItemRepository.save(item);
        });
    }

    // ── Xóa mềm ──────────────────────────────────────────────────────────────

    public void deleteMedia(Long id) {
        mediaItemRepository.findByIdAndDeletedFalse(id).ifPresent(item -> {
            item.setDeleted(true);
            mediaItemRepository.save(item);
            log.info("Đã xóa mềm bài hát id={} title='{}'", item.getId(), item.getTitle());

            try {
                storageService.delete(item.getFileName());
            } catch (java.io.IOException e) {
                log.warn("Không xóa được file '{}': {}", item.getFileName(), e.getMessage());
            }
        });
    }
}
