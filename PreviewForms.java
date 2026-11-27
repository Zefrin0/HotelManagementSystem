import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PreviewForms {
    private PreviewForms() { }

    /** Old signature kept so nothing else breaks; the table will not refresh by itself. */
    public static void open(Node owner, String title, HotelData.Reservation reservation, boolean fine) {
        open(owner, title, reservation, fine, null);
    }

    public static void open(Node owner, String title, HotelData.Reservation reservation,
                            boolean fine, Runnable onSaved) {
        if (reservation == null) { Ui.message(owner, "Select a reservation first."); return; }
        if (fine) {
            Ui.message(owner, "Damage fines are applied from the Reception screen.");
            return;
        }
        if (!reservation.status.equals("Reserved") && !reservation.status.equals("Checked In")) {
            Ui.message(owner, "Only Reserved or Checked In reservations can be changed.");
            return;
        }
        if (title.toLowerCase().contains("extend")) extend(owner, title, reservation, onSaved);
        else edit(owner, title, reservation, onSaved);
    }

    private static void edit(Node owner, String title, HotelData.Reservation reservation, Runnable onSaved) {
        LocalDate oldIn = LocalDate.parse(reservation.checkIn);
        LocalDate oldOut = LocalDate.parse(reservation.checkOut);
        boolean checkedIn = reservation.status.equals("Checked In");

        GridPane form = Ui.form();
        form.add(new Label("Reservation #" + reservation.id + " · Room " + reservation.roomNumber), 0, 0, 2, 1);
        TextField name = new TextField(reservation.guest);
        Ui.field(form, 1, "Guest name", name, "edit-guest");
        DatePicker in = new DatePicker(oldIn); in.setId("edit-arrival");
        DatePicker out = new DatePicker(oldOut); out.setId("edit-departure");
        in.setDisable(checkedIn); // a guest already checked in keeps their arrival date
        form.addRow(2, new Label("Check-in date"), in);
        form.addRow(3, new Label("Check-out date"), out);
        if (checkedIn) {
            form.add(new Label("The guest is checked in, so only the check-out date can change."), 0, 4, 2, 1);
        }

        if (!Ui.showForm(owner, title, form)) return;

        String newName = name.getText().trim();
        LocalDate newIn = in.getValue(), newOut = out.getValue();
        if (newName.isEmpty() || newIn == null || newOut == null) {
            Ui.message(owner, "Please enter a guest name and both dates.");
            return;
        }
        if (!newIn.equals(oldIn) && newIn.isBefore(LocalDate.now())) {
            Ui.message(owner, "Check-in cannot be moved to a past date.");
            return;
        }
        boolean datesChanged = !newIn.equals(oldIn) || !newOut.equals(oldOut);
        boolean nameChanged = !newName.equals(reservation.guest);
        if (!datesChanged && !nameChanged) {
            Ui.message(owner, "No changes to save.");
            return;
        }

        // Dates first: if the room is not free, nothing else is changed
        if (datesChanged) {
            String error = HotelData.changeDates(reservation.id, newIn, newOut);
            if (error != null) { Ui.message(owner, error); return; }
        }
        if (nameChanged && !HotelData.updateGuestName(reservation.id, newName)) {
            Ui.message(owner, "The dates were saved, but the guest name could not be saved.");
        } else {
            Ui.message(owner, "Reservation #" + reservation.id + " updated.");
        }
        if (onSaved != null) onSaved.run();
    }

    private static void extend(Node owner, String title, HotelData.Reservation reservation, Runnable onSaved) {
        LocalDate currentOut = LocalDate.parse(reservation.checkOut);
        HotelData.Room room = HotelData.findRoom(reservation.roomNumber);
        double rate = room == null ? 0 : room.rate;

        GridPane form = Ui.form();
        form.add(new Label("Reservation #" + reservation.id + " · " + reservation.guest
                + " · Room " + reservation.roomNumber), 0, 0, 2, 1);
        form.addRow(1, new Label("Current check-out"), new Label(reservation.checkOut));

        DatePicker newOut = new DatePicker(); newOut.setId("extend-departure");
        newOut.setPromptText("Choose new check-out");
        newOut.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || !date.isAfter(currentOut));
            }
        });
        form.addRow(2, new Label("New check-out"), newOut);

        Label info = new Label("Choose a later check-out date.");
        info.setWrapText(true);
        newOut.valueProperty().addListener((obs, o, n) -> {
            if (n == null || !n.isAfter(currentOut)) { info.setText("Choose a later check-out date."); return; }
            long extra = ChronoUnit.DAYS.between(currentOut, n);
            info.setText("Extra nights: " + extra + " · Additional room charge: " + Ui.money(rate * extra));
        });
        form.add(info, 0, 3, 2, 1);

        if (!Ui.showForm(owner, title, form)) return;

        if (newOut.getValue() == null) { Ui.message(owner, "Choose a new check-out date."); return; }
        // Checks every extra night against other Reserved / Checked In bookings
        String error = HotelData.extendStay(reservation.id, newOut.getValue());
        if (error != null) { Ui.message(owner, error); return; }
        Ui.message(owner, "Stay extended to " + newOut.getValue() + ". New total bill: "
                + Ui.money(HotelData.getTotalBill(reservation.id)));
        if (onSaved != null) onSaved.run();
    }
}