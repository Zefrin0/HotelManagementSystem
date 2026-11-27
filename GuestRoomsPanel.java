import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.ArrayList;

public class GuestRoomsPanel extends VBox {
    private final TableView<HotelData.Room> table = Ui.table("guest-rooms-table");
    private final DatePicker arrival = new DatePicker();
    private final DatePicker departure = new DatePicker();
    private final Label feedback = new Label();

    public GuestRoomsPanel(Main main) {
        Ui.page(this, main.getRole() == Role.CUSTOMER ? "Find your next stay" : "Room Availability");
        Label note = new Label("Explore room types and nightly rates. Choose check-in and check-out dates "
                + "to see only the rooms that are free for your stay.");
        note.setWrapText(true); note.getStyleClass().add("subtitle");

        arrival.setPromptText("Check-in date"); arrival.setId("browse-arrival");
        departure.setPromptText("Check-out date"); departure.setId("browse-departure");
        arrival.valueProperty().addListener((obs, o, n) -> refreshData());
        departure.valueProperty().addListener((obs, o, n) -> refreshData());

        feedback.setWrapText(true); feedback.setMinHeight(USE_PREF_SIZE); feedback.setId("browse-feedback");

        table.getColumns().add(Ui.column("Room", 100, r -> r.number));
        table.getColumns().add(Ui.column("Room type", 180, r -> r.type));
        table.getColumns().add(Ui.moneyColumn("Per night", 160, r -> r.rate));
        table.getColumns().add(Ui.statusColumn("Current status", 150, r -> r.status));

        getChildren().addAll(note, new HBox(12, arrival, departure), feedback, table);
        getChildren().add(Ui.button(main.getRole() == Role.CUSTOMER ? "Book a Room" : "Create Reservation",
                "room-book", true, () -> main.showPage(main.getRole() == Role.CUSTOMER ? "Book" : "Reservations")));
        refreshData();
    }

    /** All rooms, where an Available room booked for tonight shows as Reserved. */
    private ArrayList<HotelData.Room> currentView() {
        ArrayList<HotelData.Room> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        ArrayList<HotelData.Room> freeTonight = HotelData.getAvailableRooms(today, today.plusDays(1));
        for (HotelData.Room r : HotelData.rooms) {
            String status = r.status;
            if (status.equals("Available")) {
                boolean free = false;
                for (HotelData.Room f : freeTonight) {
                    if (f.number == r.number) free = true;
                }
                if (!free) status = "Reserved";
            }
            result.add(new HotelData.Room(r.number, r.type, r.rate, status));
        }
        return result;
    }

    public void refreshData() {
        LocalDate in = arrival.getValue();
        LocalDate out = departure.getValue();
        feedback.getStyleClass().setAll("subtitle");
        feedback.setText("");

        // No dates (or only one) chosen: every room, with tonight's status
        if (in == null || out == null) {
            table.getColumns().get(3).setText("Current status");
            Ui.refresh(table, currentView());
            return;
        }

        if (!out.isAfter(in)) {
            feedback.getStyleClass().setAll("form-error");
            feedback.setText("Check-out must be after check-in.");
            table.getColumns().get(3).setText("Current status");
            Ui.refresh(table, currentView());
            return;
        }
        if (in.isBefore(LocalDate.now())) {
            feedback.getStyleClass().setAll("form-error");
            feedback.setText("Check-in cannot be in the past.");
            table.getColumns().get(3).setText("Current status");
            Ui.refresh(table, currentView());
            return;
        }

        // Dates chosen: only rooms with no overlapping Reserved / Checked In booking
        ArrayList<HotelData.Room> free = new ArrayList<>();
        for (HotelData.Room r : HotelData.getAvailableRooms(in, out)) {
            // Free for these dates, even if occupied now by a guest leaving earlier
            free.add(new HotelData.Room(r.number, r.type, r.rate, "Available"));
        }
        table.getColumns().get(3).setText("For your dates");
        Ui.refresh(table, free);
        if (free.isEmpty()) {
            feedback.setText("No rooms are available for those dates. Try different dates.");
        }
    }
}