package com.musicapp.services;

import com.musicapp.exception.WeakPasswordException;
import com.musicapp.models.CreatorProfile;
import com.musicapp.models.MediaItem;
import com.musicapp.models.Playlist;
import com.musicapp.models.User;
import com.musicapp.repositories.CreatorProfileRepository;
import com.musicapp.repositories.MediaCommentRepository;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.PlayHistoryRepository;
import com.musicapp.repositories.PlaylistRepository;
import com.musicapp.repositories.UserRepository;
import com.musicapp.repositories.UserLibraryRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_CREATOR", "ROLE_ADMIN");
    // MAJOR-3 FIX: Match the stronger pattern in AuthService
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':,./<>?]).{8,}$";

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final MediaCommentRepository mediaCommentRepository;
    private final MediaItemRepository mediaItemRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final PlaylistRepository playlistRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       CreatorProfileRepository creatorProfileRepository,
                       MediaCommentRepository mediaCommentRepository,
                       MediaItemRepository mediaItemRepository,
                       PlayHistoryRepository playHistoryRepository,
                       PlaylistRepository playlistRepository,
                       UserLibraryRepository userLibraryRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.mediaCommentRepository = mediaCommentRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.playHistoryRepository = playHistoryRepository;
        this.playlistRepository = playlistRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * CODE-4 FIX: Centralised lookup — controllers use this instead of injecting
     * UserRepository directly and duplicating the find-or-throw pattern.
     */
    @Transactional(readOnly = true)
    public Long getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username))
                .getId();
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * C1 FIX: Properly compares old password using BCrypt, then encodes new password.
     */
    public void changePassword(String username, String oldRaw, String newRaw, String confirmRaw) {
        User user = getByUsername(username);

        if (!passwordEncoder.matches(oldRaw, user.getPassword())) {
            throw new BadCredentialsException("old_wrong");
        }

        validatePasswordStrength(newRaw);

        if (!newRaw.equals(confirmRaw)) {
            throw new IllegalArgumentException("mismatch");
        }

        user.setPassword(passwordEncoder.encode(newRaw));
        userRepository.save(user);
    }

    public void updateProfile(String username, String fullName, String phoneNumber) {
        User user = getByUsername(username);
        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        userRepository.save(user);
    }

    /**
     * M2 FIX: Validates role against a strict whitelist before applying.
     */
    public void changeRole(Long id, String newRole, String currentUsername) {
        if (!ALLOWED_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("Invalid role: " + newRole);
        }
        userRepository.findById(id).ifPresent(target -> {
            if (!target.getUsername().equals(currentUsername)) {
                target.setRole(newRole);
                userRepository.save(target);
            }
        });
    }

    public void toggleLock(Long id, String currentUsername) {
        userRepository.findById(id).ifPresent(target -> {
            if (!target.getUsername().equals(currentUsername)) {
                target.setLocked(!target.isLocked());
                userRepository.save(target);
            }
        });
    }

    public void deleteUser(Long id, String currentUsername) {
        userRepository.findById(id).ifPresent(target -> {
            if (!target.getUsername().equals(currentUsername)) {
                deleteUserDependencies(id);
                userRepository.delete(target);
            }
        });
    }

    private void deleteUserDependencies(Long userId) {
        creatorProfileRepository.findByUserId(userId).ifPresent(this::deleteCreatorProfile);
        mediaCommentRepository.deleteByUserId(userId);
        userLibraryRepository.deleteByUserId(userId);
        playHistoryRepository.deleteByUserId(userId);
        deleteUserPlaylists(userId);
        mediaItemRepository.clearUploaderByUserId(userId);
        userRepository.flush();
    }

    private void deleteCreatorProfile(CreatorProfile profile) {
        List<MediaItem> linkedMedia = mediaItemRepository.findAllLinkedToCreator(profile.getId());
        linkedMedia.forEach(media ->
                media.getCreators().removeIf(creator -> profile.getId().equals(creator.getId())));
        mediaItemRepository.saveAll(linkedMedia);
        mediaItemRepository.flush();
        creatorProfileRepository.delete(profile);
        creatorProfileRepository.flush();
    }

    private void deleteUserPlaylists(Long userId) {
        List<Playlist> rootPlaylists = playlistRepository.findByUserIdAndParentNullOrderByCreatedAtDesc(userId);
        playlistRepository.deleteAll(rootPlaylists);
        playlistRepository.flush();
    }

    private void validatePasswordStrength(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new WeakPasswordException("weak_pass");
        }
    }
}
