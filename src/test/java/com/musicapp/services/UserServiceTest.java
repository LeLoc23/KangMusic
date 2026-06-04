package com.musicapp.services;

import com.musicapp.models.CreatorProfile;
import com.musicapp.models.MediaComment;
import com.musicapp.models.MediaItem;
import com.musicapp.models.PlayHistory;
import com.musicapp.models.Playlist;
import com.musicapp.models.User;
import com.musicapp.models.UserLibrary;
import com.musicapp.repositories.CreatorProfileRepository;
import com.musicapp.repositories.MediaCommentRepository;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.PlayHistoryRepository;
import com.musicapp.repositories.PlaylistRepository;
import com.musicapp.repositories.UserLibraryRepository;
import com.musicapp.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private MediaItemRepository mediaItemRepository;

    @Autowired
    private MediaCommentRepository mediaCommentRepository;

    @Autowired
    private UserLibraryRepository userLibraryRepository;

    @Autowired
    private PlayHistoryRepository playHistoryRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Test
    void deleteUserRemovesDependentRowsBeforeDeletingUser() {
        User user = userRepository.save(new User(
                "creator-to-delete",
                "encoded-password",
                "delete-me@kangmusic.local",
                "Creator To Delete",
                "ROLE_CREATOR"));

        CreatorProfile profile = creatorProfileRepository.save(new CreatorProfile(user, "Delete Me", ""));

        MediaItem media = new MediaItem("Song", "Delete Me", "song.mp3", "AUDIO", "calm");
        media.setUploadedByUserId(user.getId());
        media.getCreators().add(profile);
        media = mediaItemRepository.save(media);

        mediaCommentRepository.save(new MediaComment(user, media, "comment", null));
        userLibraryRepository.save(new UserLibrary(user.getId(), media));
        playHistoryRepository.save(new PlayHistory(user.getId(), media));
        playlistRepository.save(new Playlist("Playlist", user.getId()));

        userService.deleteUser(user.getId(), "admin");

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(creatorProfileRepository.findByUserId(user.getId())).isEmpty();
        assertThat(mediaCommentRepository.findAll()).isEmpty();
        assertThat(userLibraryRepository.findAll()).isEmpty();
        assertThat(playHistoryRepository.findAll()).isEmpty();
        assertThat(playlistRepository.findAll()).isEmpty();

        MediaItem preservedMedia = mediaItemRepository.findById(media.getId()).orElseThrow();
        assertThat(preservedMedia.getUploadedByUserId()).isNull();
        assertThat(mediaItemRepository.findAllLinkedToCreator(profile.getId())).isEmpty();
    }
}
