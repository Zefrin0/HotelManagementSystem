import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class ReservationsPanel extends VBox {
    private final Main main;
    private final TableView<HotelData.Reservation> table = Ui.table("reservations-table");

    public ReservationsPanel(Main main) {
        this.main = main;
        Ui.page(this, "Manage Reservations");
        table.getColumns().add(Ui.column("ID", 65, r -> r.id));
        table.getColumns().add(Ui.column("Guest", 135, r -> r.guest));
        table.getColumns().add(Ui.column("Contact", 135, r -> r.contact));
        table.getColumns().add(Ui.column("Room", 65, r -> r.roomNumber));
        table.getColumns().add(Ui.column("Check-in", 105, r -> r.checkIn));
        table.getColumns().add(Ui.column("Check-out", 105, r -> r.checkOut));
        table.getColumns().add(Ui.statusColumn("Status", 115, r -> r.status));
        getChildren().addAll(table, Ui.actions(
                Ui.button("Book Reservation", "book-reservation", true, this::bookReservation),
                Ui.button("Update Details", "edit-reservation", false,
                        () -> PreviewForms.open(this, "Update Reservation",
                                table.getSelectionModel().getSelectedItem(), false, main::refreshAll)),
                Ui.button("Extend Stay", "extend-stay", false,
                        () -> PreviewForms.open(this, "Extend Stay",
                                table.getSelectionModel().getSelectedItem(), false, main::refreshAll)),
                Ui.button("Cancel Selected", "cancel-reservation", false, this::cancelReservation)));
        refreshTable();
    }

    public void refreshTable() {
        Ui.refresh(table, HotelData.reservations);
    }

    private void bookReservation() {
        TextField guest = new TextField();
        TextField contact = new TextField();
        TextField room = new TextField();
        TextField checkIn = new TextField();
        TextField checkOut = new TextField();
        GridPane form = Ui.form();
        Ui.field(form, 0, "Guest name:", guest, "guest-name");
        Ui.field(form, 1, "Contact:", contact, "guest-contact");
        Ui.field(form, 2, "Room number:", room, "reservation-room");
        Ui.field(form, 3, "Check-in (YYYY-MM-DD):", checkIn, "check-in-date");
        Ui.field(form, 4, "Check-out (YYYY-MM-DD):", checkOut, "check-out-date");
        if (!Ui.showForm(this, "New Reservation", form)) return;

        try {
            String guestName = guest.getText().trim();
            if (guestName.isEmpty()) throw new IllegalArgumentException();
            int roomNumber = Integer.parseInt(room.getText().trim());
            LocalDate in = LocalDate.parse(checkIn.getText().trim());
            LocalDate out = LocalDate.parse(checkOut.getText().trim());
            if (!out.isAfter(in)) {
                Ui.message(this, "Check-out must be after check-in.");
                return;
            }
            if (in.isBefore(LocalDate.now())) {
                Ui.message(this, "Check-in cannot be in the past.");
                return;
            }

            HotelData.Room selectedRoom = HotelData.findRoom(roomNumber);
            if (selectedRoom == null) {
                Ui.message(this, "Room not found.");
                return;
            }
            if (selectedRoom.status.equals("Maintenance")) {
                Ui.message(this, "That room is under maintenance.");
                return;
            }

            // Checks for overlapping Reserved / Checked In bookings, then saves to hotel_db
            HotelData.Reservation reservation = HotelData.createReservation(
                    guestName, contact.getText().trim(), roomNumber, in.toString(), out.toString());
            if (reservation == null) {
                Ui.message(this, "That room is already booked for those dates, "
                        + "or the reservation could not be saved.");
                return;
            }
            main.refreshAll();
            Ui.message(this, "Reservation created. ID: " + reservation.id);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            Ui.message(this, "Please enter valid reservation details. Dates look like 2026-12-25.");
        }
    }

    private void cancelReservation() {
        HotelData.Reservation reservation = table.getSelectionModel().getSelectedItem();
        if (reservation == null) {
            Ui.message(this, "Select a reservation first.");
            return;
        }
        if (reservation.status.equals("Cancelled")) {
            Ui.message(this, "This reservation is already cancelled.");
            return;
        }
        if (reservation.status.equals("Checked In") || reservation.status.equals("Checked Out")) {
            Ui.message(this, "This reservation can no longer be cancelled.");
            return;
        }
        if (!HotelData.updateReservationStatus(reservation.id, "Cancelled")) {
            Ui.message(this, "Could not cancel the reservation in the database.");
            return;
        }
        main.refreshAll();
        Ui.message(this, "Reservation cancelled.");
    }
}