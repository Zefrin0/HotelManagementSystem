import java.sql.*;

public class DbCheck {
    public static void main(String[] args) {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT room_number, room_type, rate, status FROM rooms")) {
            while (rs.next()) {
                System.out.println(rs.getString("room_number") + " "
                    + rs.getString("room_type") + " "
                    + rs.getBigDecimal("rate") + " "
                    + rs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}