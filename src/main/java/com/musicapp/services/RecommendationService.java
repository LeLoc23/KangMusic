package com.musicapp.services;

import com.musicapp.models.MediaItem;
import com.musicapp.models.PlayHistory;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.PlayHistoryRepository;
import com.musicapp.repositories.UserLibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <h2>Hybrid Recommendation Engine</h2>
 *
 * <p>Kết hợp 3 tín hiệu để tính điểm khuyến nghị:
 * <pre>
 *   Hybrid Score = w1 × CF_score + w2 × CB_score + w3 × popularity_score
 * </pre>
 *
 * <ul>
 *   <li><b>Collaborative Filtering (CF)</b>: Item-based CF thuần JPQL.
 *       Tìm bài mà người có lịch sử nghe giống user hiện tại cũng thích.
 *       Score = số user chung đã nghe cả hai bài.</li>
 *   <li><b>Content-Based (CB)</b>: Matching genre, emotionLabel.
 *       Score = Σ(weight của từng trường khớp).</li>
 *   <li><b>Popularity</b>: playCount toàn hệ thống — tránh cold-start.</li>
 * </ul>
 *
 * <p>Khi user chưa có lịch sử → fallback sang:
 * <ol>
 *   <li>Bài trending toàn cầu</li>
 *   <li>Bài phổ biến nhất (playCount)</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    // Trọng số hybrid
    private static final double W_CF         = 0.50;
    private static final double W_CB         = 0.35;
    private static final double W_POPULARITY = 0.15;

    // Trọng số content-based
    private static final int CB_GENRE_MATCH   = 5;
    private static final int CB_EMOTION_MATCH = 3;

    // Số bài seed từ lịch sử gần đây
    private static final int SEED_LIMIT     = 10;
    // Giới hạn ứng viên trước khi re-rank
    private static final int CANDIDATE_POOL = 50;

    private final PlayHistoryRepository playHistoryRepo;
    private final MediaItemRepository   mediaItemRepo;
    private final UserLibraryRepository libraryRepo;

    public RecommendationService(PlayHistoryRepository playHistoryRepo,
                                  MediaItemRepository mediaItemRepo,
                                  UserLibraryRepository libraryRepo) {
        this.playHistoryRepo = playHistoryRepo;
        this.mediaItemRepo   = mediaItemRepo;
        this.libraryRepo     = libraryRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách khuyến nghị cho user.
     * @param userId  ID user hiện tại (null → fallback toàn cầu)
     * @param limit   Số bài trả về
     */
    public List<MediaItem> getRecommendations(Long userId, int limit) {
        if (userId == null) {
            return getFallback(limit);
        }

        // 1. Lấy seed — bài gần đây đã nghe
        List<MediaItem> seeds = playHistoryRepo.findRecentlyPlayedByUser(
                userId, PageRequest.of(0, SEED_LIMIT));

        if (seeds.isEmpty()) {
            // Chưa có lịch sử → dùng liked songs làm seed
            seeds = libraryRepo.findByUserIdOrderByAddedAtDesc(userId)
                    .stream()
                    .map(ul -> ul.getMediaItem())
                    .limit(SEED_LIMIT)
                    .collect(Collectors.toList());
        }

        if (seeds.isEmpty()) {
            log.debug("User {} chưa có history/library → dùng fallback global", userId);
            return getFallback(limit);
        }

        log.debug("Tính recommendation cho userId={} với {} seed(s)", userId, seeds.size());
        return computeHybrid(userId, seeds, limit);
    }

    /**
     * Ghi lịch sử nghe (gọi từ MusicController tại /api/play/{id}).
     */
    @Transactional
    public void recordPlay(Long userId, Long mediaItemId) {
        mediaItemRepo.findByIdAndDeletedFalse(mediaItemId).ifPresent(media -> {
            // MAJOR-6 FIX: Atomic UPDATE — no read-then-write race condition
            mediaItemRepo.incrementPlayCount(media.getId());
            // Only record history for authenticated users (null userId = anonymous)
            if (userId != null) {
                PlayHistory ph = new PlayHistory(userId, media);
                playHistoryRepo.save(ph);
            }
        });
    }

    // ── Hybrid Scoring ────────────────────────────────────────────────────────

    private List<MediaItem> computeHybrid(Long userId, List<MediaItem> seeds, int limit) {
        Set<Long> excludeIds   = seeds.stream().map(MediaItem::getId).collect(Collectors.toSet());
        List<Long> seedIds     = new ArrayList<>(excludeIds);

        Map<Long, Double> scoreMap = new LinkedHashMap<>();

        // ── A. Collaborative Filtering ────────────────────────────────────────
        List<Object[]> cfRows = playHistoryRepo.findRecommendationsFromSeeds(
                userId, seedIds, PageRequest.of(0, CANDIDATE_POOL));

        double maxCF = cfRows.isEmpty() ? 1.0 :
                ((Number) cfRows.get(0)[1]).doubleValue();

        for (Object[] row : cfRows) {
            MediaItem m = (MediaItem) row[0];
            if (excludeIds.contains(m.getId())) continue;
            double raw = ((Number) row[1]).doubleValue();
            double norm = maxCF > 0 ? raw / maxCF : 0;
            scoreMap.merge(m.getId(), W_CF * norm, Double::sum);
        }

        // ── B. Content-Based Filtering ────────────────────────────────────────
        Set<String> seedGenres    = seeds.stream()
                .map(MediaItem::getGenre).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> seedEmotions  = seeds.stream()
                .map(MediaItem::getEmotionLabel).filter(Objects::nonNull).collect(Collectors.toSet());

        if (!seedGenres.isEmpty() || !seedEmotions.isEmpty()) {
            // Lấy ứng viên CB từ genre đầu tiên
            String primaryGenre = seedGenres.isEmpty() ? null : seedGenres.iterator().next();
            String primaryEmotion = seedEmotions.isEmpty() ? null : seedEmotions.iterator().next();

            List<MediaItem> cbCandidates = mediaItemRepo
                    .findSimilar(seedIds.get(0), primaryGenre, primaryEmotion,
                            PageRequest.of(0, CANDIDATE_POOL)).getContent();

            // Tìm giá trị playCount lớn nhất để chuẩn hóa
            double maxPlayCount = cbCandidates.stream()
                    .mapToLong(MediaItem::getPlayCount).max().orElse(1L);

            for (MediaItem m : cbCandidates) {
                if (excludeIds.contains(m.getId())) continue;

                double cbScore = 0;
                if (m.getGenre() != null && seedGenres.contains(m.getGenre()))
                    cbScore += CB_GENRE_MATCH;
                if (m.getEmotionLabel() != null && seedEmotions.contains(m.getEmotionLabel()))
                    cbScore += CB_EMOTION_MATCH;

                double normalizedCB = cbScore / (CB_GENRE_MATCH + CB_EMOTION_MATCH);

                // Popularity bonus
                double popScore = maxPlayCount > 0 ?
                        m.getPlayCount() / maxPlayCount : 0;

                double total = W_CB * normalizedCB + W_POPULARITY * popScore;
                scoreMap.merge(m.getId(), total, Double::sum);
            }
        }

        // ── C. Nếu không đủ ứng viên → bổ sung từ global trending ─────────────
        if (scoreMap.size() < limit) {
            List<Object[]> trending = playHistoryRepo.findGlobalTrending(
                    PageRequest.of(0, limit * 2));
            double maxTrend = trending.isEmpty() ? 1.0 :
                    ((Number) trending.get(0)[1]).doubleValue();

            for (Object[] row : trending) {
                MediaItem m = (MediaItem) row[0];
                if (excludeIds.contains(m.getId()) || scoreMap.containsKey(m.getId())) continue;
                double norm = maxTrend > 0 ? ((Number) row[1]).doubleValue() / maxTrend : 0;
                scoreMap.put(m.getId(), W_POPULARITY * norm);
            }
        }

        // ── D. Sort và lấy top-N ──────────────────────────────────────────────
        List<Long> topIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Fetch entities theo thứ tự score
        Map<Long, MediaItem> entityMap = mediaItemRepo.findAllById(topIds)
                .stream().collect(Collectors.toMap(MediaItem::getId, m -> m));

        return topIds.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private List<MediaItem> getFallback(int limit) {
        // Ưu tiên trending toàn cầu
        List<Object[]> trending = playHistoryRepo.findGlobalTrending(PageRequest.of(0, limit));
        if (!trending.isEmpty()) {
            return trending.stream()
                    .map(row -> (MediaItem) row[0])
                    .collect(Collectors.toList());
        }
        // Cuối cùng fallback về playCount
        return mediaItemRepo.findTopByPlayCount(PageRequest.of(0, limit)).getContent();
    }

    // ── Scheduled cleanup: xóa history > 90 ngày ─────────────────────────────
    @Transactional
    @Scheduled(cron = "0 0 3 * * SUN") // Chạy lúc 3h sáng Chủ nhật
    public void cleanupOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = playHistoryRepo.deleteOlderThan(cutoff);
        log.info("Đã xóa {} bản ghi lịch sử nghe nhạc trước {}", deleted, cutoff);
    }
}
