import java.sql.*;
public class TestDB {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:./music_data/MusicAppDB", "sa", "")) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT id, name, user_id, parent_id, is_folder FROM playlists");
            while (rs.next()) {
                System.out.printf("ID: %d, Name: %s, User: %d, Parent: %d, isFolder: %b\n",
                    rs.getLong("id"), rs.getString("name"), rs.getLong("user_id"), rs.getLong("parent_id"), rs.getBoolean("is_folder"));
            }
        }
    }
}
