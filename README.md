# Hotel Management System

The existing Java Swing interface has been migrated to **JavaFX (OpenJFX)**.
The same five screens, table columns, sample records, and actions are retained:

- Dashboard: total, available and occupied rooms, and recorded revenue.
- Room Availability: room table, Add Room, and Update Selected Rate.
- Reservations: reservation table, Book Reservation, and Cancel Selected.
- Reception: reservation/payment table, Check In, Check Out, and Record Payment.
- Reports: room counts and recorded revenue.

Payments and Check In / Check Out still open the same reception screen.
`RoomTable.java` also remains available as the original standalone component.
The JavaFX stylesheet is in `resources/hotel.css`; hotel data is still in `HotelData.java`.

## Run

Install **JDK 21 or newer**, then run from this folder:

```sh
./mvnw clean javafx:run
```

On Windows, use `mvnw.cmd clean javafx:run`. The included Maven wrapper downloads
Maven and the JavaFX dependencies for your operating system; a separate Maven or
JavaFX SDK installation is unnecessary. The first run needs internet access.
If Maven is already installed, `mvn clean javafx:run` also works.
See the [OpenJFX Maven setup guide](https://openjfx.io/openjfx-docs/#maven).

In IntelliJ IDEA, open **pom.xml as a project** (or add it as a Maven project),
select JDK 21 or newer as the project SDK and Maven runner JRE, and reload Maven.
Run `javafx:run` from the Maven tool window. The existing Java files stay at the
project root; Maven includes only those files as application sources and copies
`resources` to the classpath. The old plain-Java `.iml` configuration alone does
not configure the JavaFX runtime.

## Verify

```sh
./mvnw test
```

The UI regression tests use a headless JavaFX runtime, so a desktop display is
not required. Normal application runs use the native desktop runtime. The tests
also generate screenshots of all five screens at default and minimum window
sizes, plus the reservation dialog, in `target/ui-review/`.
