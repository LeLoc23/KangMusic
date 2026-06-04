package com.musicapp.services;

import com.musicapp.models.CreatorProfile;
import com.musicapp.models.MediaApprovalStatus;
import com.musicapp.models.MediaItem;
import com.musicapp.models.MediaType;
import com.musicapp.repositories.CreatorProfileRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "mp4", "flac", "ogg", "wav", "m4a", "webm");
    private static final Set<String> ALLOWED_POSTER_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    public static final int PAGE_SIZE = 20;
    public static final int ADMIN_PAGE_SIZE = 300;

    private final MediaItemRepository mediaItemRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final StorageService storageService;
    private final FallbackLyricsService lyricGenerator;
    private final LyricsService lyricsService;

    public MediaService(MediaItemRepository mediaItemRepository,
                        CreatorProfileRepository creatorProfileRepository,
                        StorageService storageService,
                        FallbackLyricsService lyricGenerator,
                        LyricsService lyricsService) {
        this.mediaItemRepository = mediaItemRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.storageService = storageService;
        this.lyricGenerator = lyricGenerator;
        this.lyricsService = lyricsService;
    }

    @Transactional(readOnly = true)
    public Page<MediaItem> findPaginated(String query, String genre, Pageable pageable) {
        return mediaItemRepository.searchActive(query, genre, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MediaItem> findPaginated(String query, Pageable pageable) {
        return findPaginated(query, null, pageable);
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findAllForAdmin() {
        return mediaItemRepository
            .findAllActive(PageRequest.of(0, ADMIN_PAGE_SIZE))
            .getContent();
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findPendingReview() {
        return mediaItemRepository.findByApprovalStatusAndDeletedFalseOrderByUploadedAtAsc(MediaApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findByUploader(Long userId) {
        return mediaItemRepository.findByUploadedByUserIdAndDeletedFalseOrderByUploadedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findNewReleases(int limit) {
        return mediaItemRepository.findNewReleases(PageRequest.of(0, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findByGenre(String genre, int limit) {
        return mediaItemRepository.findByGenreActive(genre, PageRequest.of(0, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findSimilar(Long excludeId, String genre, String emotionLabel, int limit) {
        return mediaItemRepository.findSimilar(excludeId, genre, emotionLabel, PageRequest.of(0, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public MediaItem findById(Long id) {
        return mediaItemRepository.findByIdAndDeletedFalse(id)
            .filter(MediaItem::isApproved)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public MediaItem findByIdForManagement(Long id) {
        return mediaItemRepository.findByIdAndDeletedFalse(id).orElse(null);
    }

    public void saveMedia(String title,
                          String artist,
                          MultipartFile file,
                          MultipartFile posterFile,
                          String type,
                          String emotionLabel,
                          Integer durationSeconds,
                          String genre,
                          String album,
                          String lyrics,
                          Long uploadedByUserId,
                          boolean adminUpload,
                          List<Long> creatorIds) throws IOException {

        String originalName = StringUtils.cleanPath(
            file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("invalid_file_type");
        }

        MediaType mediaType = MediaItem.parseType(type);
        String fileKey = storageService.store(file, ext);
        String posterKey = storePosterIfPresent(posterFile);

        List<CreatorProfile> creators = resolveCreators(creatorIds);
        String displayArtist = displayArtist(artist, creators);
        String finalLyrics = lyrics;
        if (finalLyrics == null || finalLyrics.trim().isEmpty()) {
            finalLyrics = lyricGenerator.generateLyrics(title, displayArtist);
        }

        MediaItem item = new MediaItem(title, displayArtist, fileKey, mediaType.name(), emotionLabel);
        item.setDurationSeconds(durationSeconds);
        item.setGenre(blankToNull(genre));
        item.setAlbum(blankToNull(album));
        item.setLyrics(finalLyrics);
        item.setPosterFilename(posterKey);
        item.setUploadedByUserId(uploadedByUserId);
        item.setApprovalStatus(adminUpload ? MediaApprovalStatus.APPROVED : MediaApprovalStatus.PENDING);
        item.setCreators(creators);
        item.setArtist(displayArtist(artist, creators));
        mediaItemRepository.save(item);

        log.info("Saved media id={} title='{}' status={} uploader={}",
            item.getId(), title, item.getApprovalStatus(), uploadedByUserId);

        // Async lyrics generation via OpenAI Whisper
        lyricsService.generateLyricsAsync(item.getId());
    }

    public void saveMedia(String title, String artist, MultipartFile file,
                          MultipartFile posterFile, String type, String emotionLabel,
                          Integer durationSeconds, String genre, String lyrics) throws IOException {
        saveMedia(title, artist, file, posterFile, type, emotionLabel, durationSeconds,
                genre, null, lyrics, null, true, List.of());
    }

    public void updateMedia(Long id,
                            String title,
                            String artist,
                            MultipartFile posterFile,
                            String type,
                            String emotionLabel,
                            Integer durationSeconds,
                            String genre,
                            String album,
                            String lyrics,
                            boolean adminEdit,
                            Long currentUserId,
                            List<Long> creatorIds) throws IOException {
        MediaItem item = mediaItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("media_not_found"));

        if (!adminEdit) {
            boolean isUploader = item.getUploadedByUserId() != null && item.getUploadedByUserId().equals(currentUserId);
            boolean isCreator = item.getCreators().stream()
                    .anyMatch(cp -> cp.getUser() != null && cp.getUser().getId().equals(currentUserId));
            if (!isUploader && !isCreator) {
                throw new IllegalArgumentException("access_denied");
            }
        }

        List<CreatorProfile> creators = resolveCreators(creatorIds);
        item.setTitle(title);
        item.setType(MediaItem.parseType(type));
        item.setEmotionLabel(blankToNull(emotionLabel));
        item.setDurationSeconds(durationSeconds);
        item.setGenre(blankToNull(genre));
        item.setAlbum(blankToNull(album));
        item.setLyrics(lyrics);
        item.setCreators(creators);
        item.setArtist(displayArtist(artist, creators));

        String posterKey = storePosterIfPresent(posterFile);
        if (posterKey != null) {
            item.setPosterFilename(posterKey);
        }

        if (!adminEdit) {
            item.setApprovalStatus(MediaApprovalStatus.PENDING);
        }
        mediaItemRepository.save(item);
    }

    public void approveMedia(Long id) {
        mediaItemRepository.findByIdAndDeletedFalse(id).ifPresent(item -> {
            item.setApprovalStatus(MediaApprovalStatus.APPROVED);
            mediaItemRepository.save(item);
        });
    }

    public List<MediaItem> findByCreatorIdActive(Long creatorId) {
        return mediaItemRepository.findByCreatorIdActive(creatorId);
    }

    @Transactional(readOnly = true)
    public List<MediaItem> findByCreatorOrUploader(Long creatorId, Long userId) {
        return mediaItemRepository.findByCreatorOrUploader(creatorId, userId);
    }


    public List<MediaItem> findAllActiveList() {
        return mediaItemRepository.findAllActiveList();
    }

    public void rejectMedia(Long id) {
        mediaItemRepository.findByIdAndDeletedFalse(id).ifPresent(item -> {
            item.setApprovalStatus(MediaApprovalStatus.REJECTED);
            mediaItemRepository.save(item);
        });
    }

    @Deprecated
    public void incrementPlayCount(Long id) {
        mediaItemRepository.incrementPlayCount(id);
    }

    public void deleteMedia(Long id) {
        mediaItemRepository.findByIdAndDeletedFalse(id).ifPresent(item -> {
            item.setDeleted(true);
            mediaItemRepository.save(item);
            try {
                storageService.delete(item.getFileName());
            } catch (IOException e) {
                log.warn("Cannot delete media file '{}': {}", item.getFileName(), e.getMessage());
            }
        });
    }

    private List<CreatorProfile> resolveCreators(List<Long> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) return new ArrayList<>();
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(creatorIds));
        return creatorProfileRepository.findAllById(uniqueIds).stream()
                .filter(CreatorProfile::isApproved)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String displayArtist(String fallbackArtist, List<CreatorProfile> creators) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (creators != null && !creators.isEmpty()) {
            creators.stream()
                    .map(CreatorProfile::getStageName)
                    .map(this::blankToNull)
                    .filter(name -> name != null)
                    .forEach(names::add);
        }
        if (fallbackArtist != null) {
            for (String name : fallbackArtist.split(",")) {
                String clean = blankToNull(name);
                if (clean != null) {
                    names.add(clean);
                }
            }
        }
        return names.isEmpty() ? "Unknown Artist" : String.join(", ", names);
    }

    private String storePosterIfPresent(MultipartFile posterFile) throws IOException {
        if (posterFile == null || posterFile.isEmpty()) return null;
        String original = posterFile.getOriginalFilename() != null ? posterFile.getOriginalFilename() : "";
        String ext = extensionOf(original);
        if (!ALLOWED_POSTER_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("invalid_poster_type");
        }
        return storageService.store(posterFile, ext);
    }

    private String extensionOf(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx >= 0 ? filename.substring(dotIdx + 1).toLowerCase() : "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
