import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;

public class GuestBookingPanel extends VBox {

    private static String describe(HotelData.Room r) {
        return r.number + " · " + r.type + " · " + Ui.money(r.rate) + " / night";
    }

    /** A button cell that shows the chosen room, or the given hint when nothing is selected. */
    private static ListCell<HotelData.Room> buttonCell(String hint) {
        return new ListCell<>() {
            @Override protected void updateItem(HotelData.Room r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? hint : describe(r));
            }
        };
    }

    public GuestBookingPanel() {
        Ui.page(this, "Book a room");
        Label note = new Label("No account needed. Provide your name and contact email for your booking.\n"
                + "Choose your dates first, then pick from the rooms available for those dates.");
        note.setWrapText(true); note.setMinHeight(USE_PREF_SIZE); note.getStyleClass().add("subtitle");
        GridPane form = Ui.form();
        TextField name = new TextField(), email = new TextField(), phone = new TextField();
        Ui.field(form, 0, "Full name *", name, "booking-name");
        Ui.field(form, 1, "Email address *", email, "booking-email");
        email.setPromptText("For reservation updates from the hotel");
        Ui.field(form, 2, "Phone (optional)", phone, "booking-phone");

        DatePicker arrival = new DatePicker(), departure = new DatePicker();
        arrival.setId("booking-arrival"); departure.setId("booking-departure");
        arrival.setPromptText("Choose arrival date"); departure.setPromptText("Choose departure date");

        // Grey out dates that cannot be chosen
        arrival.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        departure.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate earliest = (arrival.getValue() == null ? LocalDate.now() : arrival.getValue()).plusDays(1);
                setDisable(empty || date.isBefore(earliest));
            }
        });

        form.addRow(3, new Label("Check-in *"), arrival);
        form.addRow(4, new Label("Check-out *"), departure);

        ComboBox<HotelData.Room> room = new ComboBox<>();
        room.setId("booking-room");
        room.setMaxWidth(Double.MAX_VALUE);
        room.setConverter(new StringConverter<>() {
            @Override public String toString(HotelData.Room r) { return r == null ? "" : describe(r); }
            @Override public HotelData.Room fromString(String s) { return null; }
        });
        room.setButtonCell(buttonCell("Choose your dates first"));
        form.addRow(5, new Label("Room *"), room);

        // Whenever the dates change, list only the rooms free for those dates
        Runnable refreshRooms = () -> {
            room.getItems().clear();
            room.setValue(null);
            LocalDate in = arrival.getValue(), out = departure.getValue();
            if (in == null || out == null || !out.isAfter(in)) {
                room.setButtonCell(buttonCell("Choose your dates first"));
                return;
            }
            room.getItems().setAll(HotelData.getAvailableRooms(in, out));
            room.setButtonCell(buttonCell(room.getItems().isEmpty()
                    ? "No rooms available for those dates" : "Select a room"));
        };
        arrival.valueProperty().addListener((obs, o, n) -> refreshRooms.run());
        departure.valueProperty().addListener((obs, o, n) -> refreshRooms.run());

        CheckBox consent = new CheckBox("I agree to share my contact details with the hotel.");
        consent.setId("booking-consent");
        Label feedback = new Label(); feedback.setWrapText(true); feedback.setMinHeight(USE_PREF_SIZE); feedback.setId("booking-feedback");

        Button book = Ui.button("Confirm booking", "review-booking", true, () -> {
            feedback.getStyleClass().setAll("form-error");
            if (name.getText().isBlank() || !email.getText().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    || room.getValue() == null || arrival.getValue() == null || departure.getValue() == null
                    || !consent.isSelected()) {
                feedback.setText("Complete the required fields and accept the consent checkbox."); return;
            }
            if (!departure.getValue().isAfter(arrival.getValue())) {
                feedback.setText("Check-out must be after check-in."); return;
            }
            if (arrival.getValue().isBefore(LocalDate.now())) {
                feedback.setText("Check-in cannot be in the past."); return;
            }

            // Saves to hotel_db; re-checks for overlapping bookings before inserting
            HotelData.Reservation reservation = HotelData.createReservation(
                    name.getText().trim(), phone.getText().trim(), email.getText().trim(),
                    room.getValue().number, arrival.getValue().toString(), departure.getValue().toString());
            if (reservation == null) {
                feedback.setText("Sorry, that room was just booked for those dates, or the booking could not be saved. "
                        + "Please choose another room.");
                refreshRooms.run();
                return;
            }
            String confirmedEmail = email.getText().trim();
            feedback.getStyleClass().setAll("success-note");
            feedback.setText("Booking confirmed. Your reservation ID is " + reservation.id
                    + ". Keep it to manage your booking. Hotel updates will go to " + confirmedEmail + ".");
            name.clear(); email.clear(); phone.clear();
            arrival.setValue(null); departure.setValue(null);
            consent.setSelected(false);
            refreshRooms.run();
        });
        getChildren().addAll(note, form, consent, book, feedback);
    }
}