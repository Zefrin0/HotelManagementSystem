import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

public class GuestManagePanel extends VBox {
    private HotelData.Reservation current;
    private final Label details = new Label();
    private final Label feedback = new Label();
    private final Label heading = new Label("Once your booking is retrieved");
    private final Button edit;
    private final Button extend;
    private final Button cancel;

    public GuestManagePanel() {
        Ui.page(this, "Manage my booking");
        Label note = new Label("Use your booking reference and the email provided when you booked. "
                + "No customer account needed.");
        note.setWrapText(true); note.getStyleClass().add("subtitle");

        TextField reference = new TextField();
        TextField email = new TextField();
        GridPane form = Ui.form();
        Ui.field(form, 0, "Booking reference", reference, "lookup-reference");
        Ui.field(form, 1, "Email address", email, "lookup-email");

        feedback.setWrapText(true); feedback.setMinHeight(USE_PREF_SIZE); feedback.setId("lookup-feedback");
        details.setWrapText(true); details.setMinHeight(USE_PREF_SIZE);
        heading.getStyleClass().add("section-title");

        Button find = Ui.button("Find my booking", "find-booking", true, () -> {
            feedback.getStyleClass().setAll("form-error");
            String ref = reference.getText().trim();
            String mail = email.getText().trim();
            if (ref.isEmpty() || mail.isEmpty()) {
                feedback.setText("Enter both your booking reference and your email address.");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(ref);
            } catch (NumberFormatException ex) {
                feedback.setText("The booking reference is a number, such as 3.");
                return;
            }
            // One message for both mistakes, so nobody can guess which part was wrong
            current = HotelData.lookupBooking(id, mail);
            if (current == null) {
                feedback.setText("No booking found for that reference and email.");
                showDetails();
                return;
            }
            feedback.getStyleClass().setAll("success-note");
            feedback.setText("Booking found.");
            showDetails();
        });

        edit = Ui.button("Update dates", "guest-edit", false, this::updateDates);
        extend = Ui.button("Request extra nights", "guest-extend", false, this::extendStay);
        cancel = Ui.button("Cancel booking", "guest-cancel", false, this::cancelBooking);

        showDetails();
        getChildren().addAll(note, form, find, feedback, heading, details, Ui.actions(edit, extend, cancel));
    }

    private void refreshCurrent() {
        if (current != null) current = HotelData.findReservation(current.id); // objects are rebuilt on reload
        showDetails();
    }

    private void showDetails() {
        edit.setDisable(true); extend.setDisable(true); cancel.setDisable(true);
        if (current == null) {
            heading.setText("Once your booking is retrieved");
            details.setText("Your booking details will appear here after you find your booking.");
            return;
        }
        heading.setText("Your booking");
        HotelData.Room room = HotelData.findRoom(current.roomNumber);
        double total = HotelData.getTotalBill(current.id);
        double balance = total - current.payment;
        details.setText("Reference: " + current.id
                + "\nGuest: " + current.guest
                + "\nRoom: " + current.roomNumber + (room == null ? "" : " · " + room.type)
                + "\nCheck-in: " + current.checkIn
                + "\nCheck-out: " + current.checkOut
                + "\nStatus: " + current.status
                + "\nTotal: " + Ui.money(total)
                + "\nPaid: " + Ui.money(current.payment)
                + "\nBalance: " + Ui.money(Math.max(0, balance)));
        boolean reserved = current.status.equals("Reserved");
        boolean checkedIn = current.status.equals("Checked In");
        edit.setDisable(!reserved);              // guests change dates only before arrival
        extend.setDisable(!(reserved || checkedIn));
        cancel.setDisable(!reserved);            // a guest already checked in goes through reception
    }

    private void updateDates() {
        if (current == null) return;
        LocalDate oldIn = LocalDate.parse(current.checkIn);
        LocalDate oldOut = LocalDate.parse(current.checkOut);

        GridPane form = Ui.form();
        form.add(new Label("Reservation #" + current.id + " · Room " + current.roomNumber), 0, 0, 2, 1);
        DatePicker in = new DatePicker(oldIn); in.setId("guest-edit-arrival");
        DatePicker out = new DatePicker(oldOut); out.setId("guest-edit-departure");
        in.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setDisable(empty || d.isBefore(LocalDate.now()));
            }
        });
        out.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                LocalDate base = in.getValue() == null ? LocalDate.now() : in.getValue();
                setDisable(empty || !d.isAfter(base));
            }
        });
        form.addRow(1, new Label("Check-in"), in);
        form.addRow(2, new Label("Check-out"), out);
        if (!Ui.showForm(this, "Update booking", form)) return;

        LocalDate newIn = in.getValue(), newOut = out.getValue();
        if (newIn == null || newOut == null) { Ui.message(this, "Please choose both dates."); return; }
        if (newIn.equals(oldIn) && newOut.equals(oldOut)) { Ui.message(this, "No changes to save."); return; }
        if (newIn.isBefore(LocalDate.now())) { Ui.message(this, "Check-in cannot be in the past."); return; }

        // Checks the room is free for the new dates (ignoring this booking itself)
        String error = HotelData.changeDates(current.id, newIn, newOut);
        if (error != null) { Ui.message(this, error); return; }
        refreshCurrent();
        Ui.message(this, "Your booking has been updated.");
    }

    private void extendStay() {
        if (current == null) return;
        // Same form staff use: approved only if every extra night is free
        PreviewForms.open(this, "Request extra nights", current, false, this::refreshCurrent);
    }

    private void cancelBooking() {
        if (current == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel booking #" + current.id + "? No refund is processed.", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        if (!HotelData.updateReservationStatus(current.id, "Cancelled")) {
            Ui.message(this, "Could not cancel the booking. Please contact reception.");
            return;
        }
        refreshCurrent();
        Ui.message(this, "Your booking has been cancelled.");
    }
}