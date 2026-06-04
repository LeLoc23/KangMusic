package com.musicapp.services;

import com.musicapp.models.MediaItem;
import com.musicapp.models.UserLibrary;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.UserLibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private final UserLibraryRepository libraryRepo;
    private final MediaItemRepository mediaItemRepo;

    public LibraryService(UserLibraryRepository libraryRepo, MediaItemRepository mediaItemRepo) {
        this.libraryRepo = libraryRepo;
        this.mediaItemRepo = mediaItemRepo;
    }

    @Transactional(readOnly = true)
    public List<MediaItem> getUserLibrary(Long userId) {
        return libraryRepo.findByUserIdOrderByAddedAtDesc(userId)
                .stream()
                .map(UserLibrary::getMediaItem)
                .filter(media -> media != null && !media.isDeleted() && media.isApproved())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long mediaItemId) {
        return libraryRepo.existsByUserIdAndMediaItemId(userId, mediaItemId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getLikedIds(Long userId) {
        return libraryRepo.findLikedMediaIds(userId);
    }

    public boolean toggleLibrary(Long userId, Long mediaItemId) {
        var existing = libraryRepo.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            libraryRepo.delete(existing.get());
            log.info("Unliked media id={} userId={}", mediaItemId, userId);
            return false;
        }

        MediaItem media = mediaItemRepo.findByIdAndDeletedFalse(mediaItemId)
                .filter(MediaItem::isApproved)
                .orElseThrow(() -> new IllegalArgumentException("media_not_found"));
        libraryRepo.save(new UserLibrary(userId, media));
        log.info("Liked media id={} userId={}", mediaItemId, userId);
        return true;
    }
}
