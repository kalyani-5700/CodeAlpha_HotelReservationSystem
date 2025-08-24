import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** ===== Domain Models ===== */
class Room {
    String id;
    String category; // Standard, Deluxe, Suite
    double pricePerNight;

    Room(String id, String category, double pricePerNight) {
        this.id = id;
        this.category = category;
        this.pricePerNight = pricePerNight;
    }
}

class Reservation {
    String reservationId;
    String customerName;
    String roomId;
    String category;
    LocalDate checkIn;
    LocalDate checkOut; // exclusive
    long nights;
    double totalCost;
    String status;         // CONFIRMED, CANCELLED
    String paymentStatus;  // PAID, REFUNDED, FAILED
    LocalDateTime createdAt;

    Reservation(String reservationId, String customerName, String roomId, String category,
                LocalDate checkIn, LocalDate checkOut, long nights, double totalCost,
                String status, String paymentStatus, LocalDateTime createdAt) {
        this.reservationId = reservationId;
        this.customerName = customerName;
        this.roomId = roomId;
        this.category = category;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.nights = nights;
        this.totalCost = totalCost;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.createdAt = createdAt;
    }
}

/** ===== Persistence & Core Logic ===== */
class HotelRepository {
    private static final String ROOMS_FILE = "rooms.csv";
    private static final String BOOKINGS_FILE = "bookings.csv";
    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    List<Room> loadRooms() {
        try {
            if (!Files.exists(Path.of(ROOMS_FILE))) seedRooms();
            List<String> lines = Files.readAllLines(Path.of(ROOMS_FILE));
            List<Room> rooms = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) { // skip header
                String[] p = lines.get(i).split(",", -1);
                if (p.length >= 3) rooms.add(new Room(p[0], p[1], Double.parseDouble(p[2])));
            }
            return rooms;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load rooms: " + e.getMessage(), e);
        }
    }

    void saveRooms(List<Room> rooms) {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(ROOMS_FILE))) {
            bw.write("roomId,category,pricePerNight\n");
            for (Room r : rooms) {
                bw.write(String.join(",", r.id, r.category, String.valueOf(r.pricePerNight)));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save rooms: " + e.getMessage(), e);
        }
    }

    List<Reservation> loadReservations() {
        List<Reservation> list = new ArrayList<>();
        if (!Files.exists(Path.of(BOOKINGS_FILE))) {
            try (BufferedWriter bw = Files.newBufferedWriter(Path.of(BOOKINGS_FILE))) {
                bw.write("reservationId,customerName,roomId,category,checkIn,checkOut,nights,totalCost,status,paymentStatus,createdAt\n");
            } catch (IOException e) { /* ignore */ }
            return list;
        }
        try (BufferedReader br = Files.newBufferedReader(Path.of(BOOKINGS_FILE))) {
            String header = br.readLine(); // skip
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 11) continue;
                Reservation r = new Reservation(
                        p[0], p[1], p[2], p[3],
                        LocalDate.parse(p[4], DF),
                        LocalDate.parse(p[5], DF),
                        Long.parseLong(p[6]),
                        Double.parseDouble(p[7]),
                        p[8], p[9],
                        LocalDateTime.parse(p[10], DTF)
                );
                list.add(r);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load bookings: " + e.getMessage(), e);
        }
        return list;
    }

    void saveReservations(List<Reservation> list) {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(BOOKINGS_FILE))) {
            bw.write("reservationId,customerName,roomId,category,checkIn,checkOut,nights,totalCost,status,paymentStatus,createdAt\n");
            for (Reservation r : list) {
                bw.write(String.join(",",
                        r.reservationId,
                        esc(r.customerName),
                        r.roomId,
                        r.category,
                        r.checkIn.format(DF),
                        r.checkOut.format(DF),
                        String.valueOf(r.nights),
                        String.valueOf(r.totalCost),
                        r.status,
                        r.paymentStatus,
                        r.createdAt.format(DTF)
                ));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save bookings: " + e.getMessage(), e);
        }
    }

    private String esc(String s){ return s.replace(",", " "); }

    private void seedRooms() throws IOException {
        List<Room> seed = new ArrayList<>();
        // 3 categories, a few rooms each
        seed.add(new Room("S101", "Standard", 2500));
        seed.add(new Room("S102", "Standard", 2500));
        seed.add(new Room("S103", "Standard", 2400));
        seed.add(new Room("D201", "Deluxe", 4000));
        seed.add(new Room("D202", "Deluxe", 4200));
        seed.add(new Room("D203", "Deluxe", 4100));
        seed.add(new Room("U301", "Suite", 7000));
        seed.add(new Room("U302", "Suite", 7200));
        saveRooms(seed);
    }
}

class HotelService {
    private final List<Room> rooms;
    private final List<Reservation> reservations;
    private final HotelRepository repo;

    HotelService(HotelRepository repo) {
        this.repo = repo;
        this.rooms = new ArrayList<>(repo.loadRooms());
        this.reservations = new ArrayList<>(repo.loadReservations());
    }

    List<Room> search(String category, LocalDate in, LocalDate out) {
        return rooms.stream()
                .filter(r -> category == null || category.equalsIgnoreCase(r.category))
                .filter(r -> isRoomAvailable(r.id, in, out))
                .collect(Collectors.toList());
    }

    boolean isRoomAvailable(String roomId, LocalDate in, LocalDate out) {
        for (Reservation r : reservations) {
            if (!"CONFIRMED".equals(r.status)) continue;
            if (!r.roomId.equals(roomId)) continue;
            if (datesOverlap(in, out, r.checkIn, r.checkOut)) return false;
        }
        return true;
    }

    private boolean datesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        // Using [start, end) intervals: overlap if aStart < bEnd && bStart < aEnd
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    Reservation book(String customer, String category, LocalDate in, LocalDate out, Scanner sc) {
        List<Room> available = search(category, in, out);
        if (available.isEmpty()) return null;

        // pick first available; could present choices in UI
        Room room = available.get(0);
        long nights = Duration.between(in.atStartOfDay(), out.atStartOfDay()).toDays();
        double total = nights * room.pricePerNight;

        System.out.println("\n--- Payment Simulation ---");
        System.out.print("Enter fake card number (16 digits): ");
        String card = sc.nextLine().trim();
        System.out.print("Enter fake CVV (3 digits): ");
        String cvv = sc.nextLine().trim();

        boolean paymentOK = simulatePayment(card, cvv, total);
        String paymentStatus = paymentOK ? "PAID" : "FAILED";
        String status = paymentOK ? "CONFIRMED" : "CANCELLED";
        String resId = generateReservationId();

        Reservation res = new Reservation(
                resId, customer, room.id, room.category,
                in, out, nights, total, status, paymentStatus, LocalDateTime.now()
        );

        reservations.add(res);
        repo.saveReservations(reservations);

        return res;
    }

    boolean cancel(String reservationId) {
        for (Reservation r : reservations) {
            if (r.reservationId.equalsIgnoreCase(reservationId)) {
                if ("CANCELLED".equals(r.status)) return false;
                r.status = "CANCELLED";
                if ("PAID".equalsIgnoreCase(r.paymentStatus)) {
                    r.paymentStatus = "REFUNDED";
                }
                repo.saveReservations(reservations);
                return true;
            }
        }
        return false;
    }

    Reservation find(String reservationId) {
        for (Reservation r : reservations) if (r.reservationId.equalsIgnoreCase(reservationId)) return r;
        return null;
    }

    List<Reservation> allReservations() { return new ArrayList<>(reservations); }

    private String generateReservationId() {
        return "R" + (100000 + new Random().nextInt(900000));
    }

    private boolean simulatePayment(String card, String cvv, double amount) {
        // Simple validation + ~90% success
        if (!card.matches("\\d{16}") || !cvv.matches("\\d{3}")) return false;
        return new Random().nextInt(10) != 0; // 0..9 -> 10% fail
    }
}

/** ===== Console UI ===== */
public class HotelReservationSystem {
    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        HotelRepository repo = new HotelRepository();
        HotelService service = new HotelService(repo);

        System.out.println("==== Hotel Reservation System ====");
        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Choose: ");
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1": handleSearch(service, sc); break;
                    case "2": handleBook(service, sc); break;
                    case "3": handleCancel(service, sc); break;
                    case "4": handleView(service, sc); break;
                    case "5": handleList(service); break;
                    case "0": running = false; break;
                    default: System.out.println("Invalid option.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
        System.out.println("Goodbye!");
    }

    private static void printMenu() {
        System.out.println("\n1) Search Available Rooms");
        System.out.println("2) Book a Room");
        System.out.println("3) Cancel Reservation");
        System.out.println("4) View Booking Details");
        System.out.println("5) List All Bookings");
        System.out.println("0) Exit");
    }

    private static void handleSearch(HotelService service, Scanner sc) {
        String category = askCategory(sc);
        LocalDate in = askDate(sc, "Check-in (YYYY-MM-DD): ");
        LocalDate out = askDate(sc, "Check-out (YYYY-MM-DD): ");
        if (!validateRange(in, out)) return;

        List<Room> rooms = service.search(category, in, out);
        if (rooms.isEmpty()) {
            System.out.println("No rooms available for " + category + " between " + in + " and " + out);
        } else {
            System.out.println("\nAvailable rooms:");
            for (Room r : rooms) {
                System.out.printf("- %s | %s | ₹%.2f per night%n", r.id, r.category, r.pricePerNight);
            }
        }
    }

    private static void handleBook(HotelService service, Scanner sc) {
        System.out.print("Customer Name: ");
        String customer = sc.nextLine().trim();
        String category = askCategory(sc);
        LocalDate in = askDate(sc, "Check-in (YYYY-MM-DD): ");
        LocalDate out = askDate(sc, "Check-out (YYYY-MM-DD): ");
        if (!validateRange(in, out)) return;

        Reservation r = service.book(customer, category, in, out, sc);
        if (r == null) {
            System.out.println("No available rooms found for the selected dates.");
            return;
        }
        printReservation(r);
    }

    private static void handleCancel(HotelService service, Scanner sc) {
        System.out.print("Reservation ID to cancel: ");
        String id = sc.nextLine().trim();
        boolean ok = service.cancel(id);
        System.out.println(ok ? "Reservation cancelled (refund simulated if applicable)." : "Not found or already cancelled.");
    }

    private static void handleView(HotelService service, Scanner sc) {
        System.out.print("Reservation ID: ");
        String id = sc.nextLine().trim();
        Reservation r = service.find(id);
        if (r == null) System.out.println("Reservation not found.");
        else printReservation(r);
    }

    private static void handleList(HotelService service) {
        List<Reservation> all = service.allReservations();
        if (all.isEmpty()) { System.out.println("No bookings yet."); return; }
        for (Reservation r : all) printReservationBrief(r);
    }

    private static String askCategory(Scanner sc) {
        System.out.print("Category [Standard/Deluxe/Suite or Enter for Any]: ");
        String cat = sc.nextLine().trim();
        if (cat.isEmpty()) return null;
        if (!cat.equalsIgnoreCase("Standard") && !cat.equalsIgnoreCase("Deluxe") && !cat.equalsIgnoreCase("Suite")) {
            System.out.println("Unknown category. Defaulting to Any.");
            return null;
        }
        return capitalize(cat);
    }

    private static LocalDate askDate(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                return LocalDate.parse(s, DF);
            } catch (Exception e) {
                System.out.println("Invalid date format. Try again (YYYY-MM-DD).");
            }
        }
    }

    private static boolean validateRange(LocalDate in, LocalDate out) {
        if (!in.isBefore(out)) {
            System.out.println("Check-out must be after check-in.");
            return false;
        }
        if (in.isBefore(LocalDate.now())) {
            System.out.println("Check-in cannot be in the past.");
            return false;
        }
        return true;
    }

    private static void printReservation(Reservation r) {
        System.out.println("\n===== Booking Details =====");
        System.out.println("Reservation ID : " + r.reservationId);
        System.out.println("Customer       : " + r.customerName);
        System.out.println("Room           : " + r.roomId + " (" + r.category + ")");
        System.out.println("Check-in       : " + r.checkIn);
        System.out.println("Check-out      : " + r.checkOut);
        System.out.println("Nights         : " + r.nights);
        System.out.printf ("Total Cost     : ₹%.2f%n", r.totalCost);
        System.out.println("Status         : " + r.status);
        System.out.println("Payment        : " + r.paymentStatus);
        System.out.println("Created At     : " + r.createdAt);
    }

    private static void printReservationBrief(Reservation r) {
        System.out.printf("%s | %s | %s | %s→%s | ₹%.2f | %s/%s%n",
                r.reservationId, r.customerName, r.roomId, r.checkIn, r.checkOut, r.totalCost, r.status, r.paymentStatus);
    }

    private static String capitalize(String s) {
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
