import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

public class HotelData {

    public static class Room {
        int number;
        String type;
        double rate;
        String status;

        Room(int number, String type, double rate, String status) {
            this.number = number;
            this.type = type;
            this.rate = rate;
            this.status = status;
        }
    }

    public static class Reservation {
        int id;
        String guest;
        String contact;
        int roomNumber;
        String checkIn;
        String checkOut;
        String status;
        double payment;

        Reservation(int id, String guest, String contact, int roomNumber,
                    String checkIn, String checkOut) {
            this.id = id;
            this.guest = guest;
            this.contact = contact;
            this.roomNumber = roomNumber;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.status = "Reserved";
            this.payment = 0;
        }
    }

    public static ArrayList<Room> rooms = new ArrayList<>();
    public static ArrayList<Reservation> reservations = new ArrayList<>();
    public static int nextReservationId = 1001; // no longer used: the database assigns ids

    static {
        reload();
    }

    // ---------- Loading from the database ----------

    /** Re-reads rooms and reservations from hotel_db. */
    public static void reload() {
        rooms.clear();
        reservations.clear();
        try (Connection c = Database.getConnection()) {

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT room_number, room_type, rate, status FROM rooms ORDER BY room_number");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(new Room(
                            Integer.parseInt(rs.getString("room_number")),
                            rs.getString("room_type"),
                            rs.getDouble("rate"),
                            rs.getString("status")));
                }
            }

            String sql = "SELECT b.booking_id, g.full_name, g.contact_number, r.room_number, "
                    + "b.check_in, b.check_out, b.status, "
                    + "COALESCE((SELECT SUM(p.amount) FROM payments p "
                    + "          WHERE p.booking_id = b.booking_id), 0) AS paid "
                    + "FROM bookings b "
                    + "JOIN guests g ON g.guest_id = b.guest_id "
                    + "JOIN rooms r ON r.room_id = b.room_id "
                    + "ORDER BY b.booking_id";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String contact = rs.getString("contact_number");
                    Reservation res = new Reservation(
                            rs.getInt("booking_id"),
                            rs.getString("full_name"),
                            contact == null ? "" : contact,
                            Integer.parseInt(rs.getString("room_number")),
                            rs.getDate("check_in").toString(),
                            rs.getDate("check_out").toString());
                    res.status = rs.getString("status");
                    res.payment = rs.getDouble("paid");
                    reservations.add(res);
                }
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("Could not load data from hotel_db. Is MySQL running in XAMPP?");
            e.printStackTrace();
        }
    }

    // ---------- Lookups ----------

    public static Room findRoom(int number) {
        for (Room room : rooms) {
            if (room.number == number) return room;
        }
        return null;
    }

    public static Reservation findReservation(int id) {
        for (Reservation reservation : reservations) {
            if (reservation.id == id) return reservation;
        }
        return null;
    }

    public static int countRooms(String status) {
        int count = 0;
        for (Room room : rooms) {
            if (room.status.equals(status)) count++;
        }
        return count;
    }

    public static double getRevenue() {
        double total = 0;
        for (Reservation reservation : reservations) {
            total += reservation.payment;
        }
        return total;
    }

    // ---------- Rooms ----------

    public static boolean addRoom(int number, String type, double rate) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO rooms (room_number, room_type, rate, status) "
                     + "VALUES (?, ?, ?, 'Available')")) {
            ps.setString(1, String.valueOf(number));
            ps.setString(2, type);
            ps.setDouble(3, rate);
            ps.executeUpdate();
            rooms.add(new Room(number, type, rate, "Available"));
            rooms.sort((a, b) -> Integer.compare(a.number, b.number));
            return true;
        } catch (SQLException e) {
            e.printStackTrace(); // e.g. duplicate room number or type longer than 20 characters
            return false;
        }
    }

    public static boolean updateRoomRate(int number, double rate) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE rooms SET rate = ? WHERE room_number = ?")) {
            ps.setDouble(1, rate);
            ps.setString(2, String.valueOf(number));
            ps.executeUpdate();
            Room room = findRoom(number);
            if (room != null) room.rate = rate;
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateRoomStatus(int roomNumber, String status) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE rooms SET status = ? WHERE room_number = ?")) {
            ps.setString(1, status);
            ps.setString(2, String.valueOf(roomNumber));
            ps.executeUpdate();
            Room room = findRoom(roomNumber);
            if (room != null) room.status = status;
            return true;
        } catch (SQLException e) {
            e.printStackTrace(); // also happens if status isn't Available, Occupied, Cleaning or Maintenance
            return false;
        }
    }

    // ---------- Availability ----------

    /** Rooms that can be booked for these dates (no overlapping Reserved/Checked In booking). */
    public static ArrayList<Room> getAvailableRooms(LocalDate in, LocalDate out) {
        ArrayList<Room> result = new ArrayList<>();
        String sql = "SELECT r.room_number, r.room_type, r.rate, r.status FROM rooms r "
                + "WHERE r.status <> 'Maintenance' "
                + "AND NOT (r.status = 'Cleaning' AND ? <= CURDATE()) "
                + "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id = r.room_id "
                + "  AND b.status IN ('Reserved','Checked In') "
                + "  AND b.check_in < ? AND b.check_out > ?) "
                + "ORDER BY r.room_number";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(in));
            ps.setDate(2, Date.valueOf(out));
            ps.setDate(3, Date.valueOf(in));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Room(
                            Integer.parseInt(rs.getString("room_number")),
                            rs.getString("room_type"),
                            rs.getDouble("rate"),
                            rs.getString("status")));
                }
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static boolean hasConflict(Connection c, int roomId, LocalDate in, LocalDate out,
                                       int ignoreBookingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND booking_id <> ? "
                + "AND status IN ('Reserved','Checked In') AND check_in < ? AND check_out > ?")) {
            ps.setInt(1, roomId);
            ps.setInt(2, ignoreBookingId);
            ps.setDate(3, Date.valueOf(out));
            ps.setDate(4, Date.valueOf(in));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    // ---------- Creating and updating reservations ----------

    /** Creates a Reserved booking. Returns null if the room is taken for those dates or on error. */
    public static Reservation createReservation(String guest, String contact, String email,
                                                int roomNumber, String checkIn, String checkOut) {
        try (Connection c = Database.getConnection()) {
            int roomId;
            double rate;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT room_id, rate FROM rooms WHERE room_number = ?")) {
                ps.setString(1, String.valueOf(roomNumber));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    roomId = rs.getInt("room_id");
                    rate = rs.getDouble("rate");
                }
            }

            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);
            if (hasConflict(c, roomId, in, out, 0)) return null;

            int guestId = findOrCreateGuest(c, guest, contact, email);
            long nights = ChronoUnit.DAYS.between(in, out);

            int bookingId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO bookings (guest_id, room_id, check_in, check_out, total_price, status) "
                    + "VALUES (?, ?, ?, ?, ?, 'Reserved')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, guestId);
                ps.setInt(2, roomId);
                ps.setDate(3, Date.valueOf(in));
                ps.setDate(4, Date.valueOf(out));
                ps.setDouble(5, rate * nights);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    bookingId = keys.getInt(1);
                }
            }

            Reservation res = new Reservation(bookingId, guest, contact, roomNumber, checkIn, checkOut);
            reservations.add(res);
            return res;
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Older 5-argument version, used by ReservationsPanel (no email). */
    public static Reservation createReservation(String guest, String contact, int roomNumber,
                                                String checkIn, String checkOut) {
        return createReservation(guest, contact, "", roomNumber, checkIn, checkOut);
    }

    public static boolean updateReservationStatus(int id, String status) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE bookings SET status = ? WHERE booking_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
            Reservation res = findReservation(id);
            if (res != null) res.status = status;
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------- Update details / extend stay ----------
    // These methods return null on success, or a message to show the user.

    public static String changeDates(int bookingId, LocalDate newIn, LocalDate newOut) {
        if (!newOut.isAfter(newIn)) return "Check-out must be after check-in.";
        try (Connection c = Database.getConnection()) {
            int roomId;
            String status;
            LocalDate oldIn;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT room_id, status, check_in FROM bookings WHERE booking_id = ?")) {
                ps.setInt(1, bookingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "Reservation not found.";
                    roomId = rs.getInt("room_id");
                    status = rs.getString("status");
                    oldIn = rs.getDate("check_in").toLocalDate();
                }
            }
            if (status.equals("Checked In")) {
                if (!newIn.equals(oldIn)) return "The check-in date of a guest already checked in cannot change.";
            } else if (!status.equals("Reserved")) {
                return "Only Reserved or Checked In reservations can be changed.";
            }
            if (hasConflict(c, roomId, newIn, newOut, bookingId)) {
                return "The room is not available for those dates.";
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE bookings b JOIN rooms r ON r.room_id = b.room_id "
                    + "SET b.check_in = ?, b.check_out = ?, b.total_price = r.rate * DATEDIFF(?, ?) "
                    + "WHERE b.booking_id = ?")) {
                ps.setDate(1, Date.valueOf(newIn));
                ps.setDate(2, Date.valueOf(newOut));
                ps.setDate(3, Date.valueOf(newOut));
                ps.setDate(4, Date.valueOf(newIn));
                ps.setInt(5, bookingId);
                ps.executeUpdate();
            }
            reload();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Could not save the change to the database.";
        }
    }

    /** Extends the stay if every extra night is free. */
    public static String extendStay(int bookingId, LocalDate newCheckOut) {
        Reservation res = findReservation(bookingId);
        if (res == null) return "Reservation not found.";
        LocalDate currentIn = LocalDate.parse(res.checkIn);
        LocalDate currentOut = LocalDate.parse(res.checkOut);
        if (!newCheckOut.isAfter(currentOut)) return "The new check-out must be after the current one.";
        return changeDates(bookingId, currentIn, newCheckOut);
    }

    // ---------- Check-in, check-out, fines, bill ----------

    public static String checkIn(int bookingId) {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int roomId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT b.status, b.room_id, r.status AS room_status FROM bookings b "
                        + "JOIN rooms r ON r.room_id = b.room_id WHERE b.booking_id = ?")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return "Reservation not found.";
                        if (!rs.getString("status").equals("Reserved")) {
                            return "Only a Reserved booking can be checked in.";
                        }
                        if (!rs.getString("room_status").equals("Available")) {
                            return "The room is not ready (status: " + rs.getString("room_status") + ").";
                        }
                        roomId = rs.getInt("room_id");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookings SET status = 'Checked In' WHERE booking_id = ?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE rooms SET status = 'Occupied' WHERE room_id = ?")) {
                    ps.setInt(1, roomId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
            reload();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Could not save check-in to the database.";
        }
    }

    /** Total Bill = (nights x room rate) + damage fines. */
    public static double getTotalBill(int bookingId) {
        String sql = "SELECT DATEDIFF(b.check_out, b.check_in) * r.rate "
                + "+ COALESCE((SELECT SUM(f.amount) FROM damage_fines f WHERE f.booking_id = b.booking_id), 0) "
                + "FROM bookings b JOIN rooms r ON r.room_id = b.room_id WHERE b.booking_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** Remaining balance = total bill minus recorded payments. */
    public static double getBalance(int bookingId) {
        Reservation res = findReservation(bookingId);
        double paid = res == null ? 0 : res.payment;
        return getTotalBill(bookingId) - paid;
    }

    public static boolean addPayment(int reservationId, double amount, String details) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO payments (booking_id, amount, payment_date, details) "
                     + "VALUES (?, ?, CURDATE(), ?)")) {
            ps.setInt(1, reservationId);
            ps.setDouble(2, amount);
            ps.setString(3, details);
            ps.executeUpdate();
            Reservation res = findReservation(reservationId);
            if (res != null) res.payment += amount;
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addDamageFine(int bookingId, String description, double amount) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO damage_fines (booking_id, description, amount) VALUES (?, ?, ?)")) {
            ps.setInt(1, bookingId);
            ps.setString(2, description);
            ps.setDouble(3, amount);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Checks out a Checked In guest once the balance is settled; the room becomes Cleaning. */
    public static String checkOut(int bookingId) {
        Reservation res = findReservation(bookingId);
        if (res == null) return "Reservation not found.";
        if (!res.status.equals("Checked In")) return "Only a Checked In guest can be checked out.";
        double balance = getBalance(bookingId);
        if (balance > 0.005) {
            return "Balance remaining: " + String.format("%.2f", balance) + ". Record the payment first.";
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookings SET status = 'Checked Out' WHERE booking_id = ?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE rooms r JOIN bookings b ON b.room_id = r.room_id "
                        + "SET r.status = 'Cleaning' WHERE b.booking_id = ?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
            reload();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Could not save check-out to the database.";
        }
    }
        public static boolean updateGuestName(int bookingId, String name) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE guests g JOIN bookings b ON b.guest_id = g.guest_id "
                     + "SET g.full_name = ? WHERE b.booking_id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, bookingId);
            ps.executeUpdate();
            reload();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------- Guests ----------
        /** Finds a booking only if the reference AND the guest's email both match. Returns null otherwise. */
    public static Reservation lookupBooking(int bookingId, String email) {
        String sql = "SELECT 1 FROM bookings b JOIN guests g ON g.guest_id = b.guest_id "
                + "WHERE b.booking_id = ? AND LOWER(g.email) = LOWER(?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setString(2, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        reload(); // make sure we show the latest data
        return findReservation(bookingId);
    }

    private static int findOrCreateGuest(Connection c, String name, String contact, String email)
            throws SQLException {
        String mail = (email == null || email.isBlank()) ? null : email.trim();
        if (mail != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT guest_id FROM guests WHERE email = ?")) {
                ps.setString(1, mail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT guest_id FROM guests WHERE full_name = ? AND contact_number = ?")) {
                ps.setString(1, name);
                ps.setString(2, contact);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO guests (full_name, contact_number, email) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, contact);
            ps.setString(3, mail);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }
}