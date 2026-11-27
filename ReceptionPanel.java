import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class ReceptionPanel extends VBox {
    private final Main main;
    private final TableView<HotelData.Reservation> table = Ui.table("reception-table");

    public ReceptionPanel(Main main) {
        this.main = main;
        Ui.page(this, "Reception: Check-in, Check-out and Payments");
        table.getColumns().add(Ui.column("ID", 80, r -> r.id));
        table.getColumns().add(Ui.column("Guest", 220, r -> r.guest));
        table.getColumns().add(Ui.column("Room", 90, r -> r.roomNumber));
        table.getColumns().add(Ui.statusColumn("Status", 170, r -> r.status));
        table.getColumns().add(Ui.moneyColumn("Paid", 160, r -> r.payment));
        getChildren().addAll(table, Ui.actions(
                Ui.button("Check In", "check-in", true, this::checkIn),
                Ui.button("Check Out", "check-out", false, this::checkOut),
                Ui.button("Record Payment", "record-payment", false, this::recordPayment),
                Ui.button("Apply Damage Fine", "damage-fine", false, this::applyDamageFine),
                Ui.button("Mark Room Ready", "room-ready", false, this::markRoomReady)));
        refreshTable();
    }

    public void refreshTable() {
        Ui.refresh(table, HotelData.reservations);
    }

    private HotelData.Reservation selectedReservation() {
        HotelData.Reservation reservation = table.getSelectionModel().getSelectedItem();
        if (reservation == null) Ui.message(this, "Please select a reservation.");
        return reservation;
    }

    private void checkIn() {
        HotelData.Reservation reservation = selectedReservation();
        if (reservation == null) return;
        // Needs status Reserved and a room that is Available; room becomes Occupied
        String error = HotelData.checkIn(reservation.id);
        if (error != null) {
            Ui.message(this, error);
            return;
        }
        main.refreshAll();
        Ui.message(this, "Check In completed. Room " + reservation.roomNumber + " is now Occupied.");
    }

    private void checkOut() {
        HotelData.Reservation reservation = selectedReservation();
        if (reservation == null) return;
        // Refused until Total Bill (nights x rate + fines) minus payments is zero;
        // the room becomes Cleaning, not Available
        String error = HotelData.checkOut(reservation.id);
        if (error != null) {
            Ui.message(this, error);
            return;
        }
        main.refreshAll();
        Ui.message(this, "Check Out completed. Room " + reservation.roomNumber
                + " is now Cleaning. Use Mark Room Ready when it is clean.");
    }

    private void recordPayment() {
        HotelData.Reservation reservation = selectedReservation();
        if (reservation == null) return;
        if (reservation.status.equals("Cancelled")) {
            Ui.message(this, "Cannot record payment for a cancelled reservation.");
            return;
        }
        double balance = HotelData.getBalance(reservation.id);
        if (balance <= 0.005) {
            Ui.message(this, "This reservation has no balance due.");
            return;
        }
        var value = Ui.input(this, "Balance due: " + Ui.money(balance) + "\nEnter payment amount:");
        if (value.isEmpty()) return;
        try {
            double amount = Double.parseDouble(value.get().trim());
            if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
            if (amount > balance + 0.005) {
                Ui.message(this, "The payment is more than the balance due (" + Ui.money(balance) + ").");
                return;
            }
            var details = Ui.input(this, "Payment details (for example cash or a reference number):");
            if (details.isEmpty()) return;
            if (!HotelData.addPayment(reservation.id, amount, details.get().trim())) {
                Ui.message(this, "Could not save the payment to the database.");
                return;
            }
            main.refreshAll();
            Ui.message(this, "Payment recorded. Remaining balance: "
                    + Ui.money(Math.max(0, HotelData.getBalance(reservation.id))));
        } catch (NumberFormatException ex) {
            Ui.message(this, "Enter a valid payment amount.");
        }
    }

    private void applyDamageFine() {
        HotelData.Reservation reservation = selectedReservation();
        if (reservation == null) return;
        // Per the documentation, fines are applied during checkout of a Checked In guest
        if (!reservation.status.equals("Checked In")) {
            Ui.message(this, "Damage fines can only be applied to a guest who is checked in.");
            return;
        }
        var description = Ui.input(this, "Describe the damage:");
        if (description.isEmpty()) return;
        if (description.get().trim().isEmpty()) {
            Ui.message(this, "Please describe the damage.");
            return;
        }
        var value = Ui.input(this, "Fine amount:");
        if (value.isEmpty()) return;
        try {
            double amount = Double.parseDouble(value.get().trim());
            if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
            if (!HotelData.addDamageFine(reservation.id, description.get().trim(), amount)) {
                Ui.message(this, "Could not save the fine to the database.");
                return;
            }
            main.refreshAll();
            Ui.message(this, "Damage fine recorded. Balance due: "
                    + Ui.money(Math.max(0, HotelData.getBalance(reservation.id))));
        } catch (NumberFormatException ex) {
            Ui.message(this, "Enter a valid fine amount.");
        }
    }

    private void markRoomReady() {
        HotelData.Reservation reservation = selectedReservation();
        if (reservation == null) return;
        HotelData.Room room = HotelData.findRoom(reservation.roomNumber);
        if (room == null) {
            Ui.message(this, "Room not found.");
            return;
        }
        if (!room.status.equals("Cleaning")) {
            Ui.message(this, "Room " + room.number + " is not waiting for cleaning (status: " + room.status + ").");
            return;
        }
        if (!HotelData.updateRoomStatus(room.number, "Available")) {
            Ui.message(this, "Could not update the room in the database.");
            return;
        }
        main.refreshAll();
        Ui.message(this, "Room " + room.number + " is Available again.");
    }
}