package com.musicapp.services;

import org.springframework.stereotype.Service;
import java.util.Random;

/**
 * CODE-3 FIX: Renamed from LyricGeneratorService to FallbackLyricsService.
 * This generates placeholder lyrics from a fixed pool — it is NOT AI-generated.
 * The previous class name and user-facing copy were misleading ("KangMusic AI").
 */
@Service
public class FallbackLyricsService {

    private final String[] VERSES = {
        "Dưới ánh đèn vang bước chân ai đi về phương xa",
        "Gió khẽ lay nhành hoa dại bên đường vắng",
        "Ký ức ùa về như những giấc mơ chẳng tên",
        "Ta lạc mất nhau trong dòng đời hối hả",
        "Nắng tắt rồi mà lòng vẫn còn những đợi chờ",
        "Mưa rơi lạnh căm trên đôi vai gầy mệt mỏi",
        "Tìm lại chút hương xưa đã phai nhòa theo mây"
    };

    private final String[] CHORUSES = {
        "Và rồi ta sẽ lại bắt gặp nhau đâu đó giữa thế gian",
        "Yêu thương ngày nào giờ chỉ còn là những mảnh vỡ",
        "Tình yêu như cánh chim bay mãi chẳng dừng chân",
        "Hãy cứ để nỗi buồn trôi theo những giấc chiêm bao",
        "Bởi vì trái tim này đã trao hết cho một người"
    };

    public String generateLyrics(String title, String artist) {
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        
        sb.append("[Verse 1]\n");
        for (int i = 0; i < 4; i++) {
            sb.append(VERSES[rand.nextInt(VERSES.length)]).append("\n");
        }
        
        sb.append("\n[Chorus]\n");
        for (int i = 0; i < 2; i++) {
            sb.append(CHORUSES[rand.nextInt(CHORUSES.length)]).append("\n");
        }
        
        sb.append("\n[Verse 2]\n");
        for (int i = 0; i < 4; i++) {
            sb.append(VERSES[rand.nextInt(VERSES.length)]).append("\n");
        }
        
        sb.append("\n[Chorus]\n");
        for (int i = 0; i < 2; i++) {
            sb.append(CHORUSES[rand.nextInt(CHORUSES.length)]).append("\n");
        }
        
        sb.append("\n[Outro]\n");
        sb.append("Bài hát '").append(title).append("' trình bày bởi ").append(artist).append(".\n");
        // CODE-3 FIX: Removed "Tự động tạo bởi KangMusic AI" — this is not AI-generated
        sb.append("Lời tự động điền — hãy cập nhật lời thật trong trang quản trị.");
        
        return sb.toString();
    }
}
