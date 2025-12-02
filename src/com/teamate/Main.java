package com.teamate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import applogger.AppLogger;

public class Main {
    private static final String PARTICIPANTS_CSV = "data/participants_sample.csv";
    private static final String FORMED_TEAMS_CSV = "data/formed_teams.csv";
    private static List<Participant> participants = new ArrayList<>();
    private static List<Team> lastFormedTeams = null;

    private static Integer storedTeamSize = null;
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        AppLogger.log("Application started.");
        List<Participant> participants = new ArrayList<>();
        List<Team> lastFormedTeams = null;
        Integer storedTeamSize = null;

        // Try to load participants at startup (non-fatal)
        try {
            participants = FileHandler.readParticipants(PARTICIPANTS_CSV);
            System.out.println("Loaded participants: " + participants.size());
            AppLogger.log("Loaded participants at startup: " + participants.size());
        } catch (Exception e) {
            System.out.println("Could not load participants at startup (file missing or invalid). You can load via Organizer -> Load CSV.");
            AppLogger.log("Startup load failed: " + e.getMessage());
        }

        while (true) {
            System.out.println("\nAre you:\n1. Organizer\n2. Participant\n0. Exit");
            System.out.print("Choose (0/1/2): ");
            String roleChoice = sc.nextLine().trim();
            if (roleChoice.equals("0")) {
                AppLogger.log("Application exiting by user.");
                System.out.println("Goodbye.");
                break;
            } else if (roleChoice.equals("1")) {
                // Organizer menu
                boolean back = false;
                while (!back) {
                    System.out.println("\nOrganizer Menu:");
                    System.out.println("1. Load CSV file");
                    System.out.println("2. Input team size");
                    System.out.println("3. Initiate team formation");
                    System.out.println("4. View generated teams");
                    System.out.print("5. Remove participants ");
                    System.out.println("\n0. Back");
                    System.out.print("Choose: ");
                    String opt = sc.nextLine().trim();

                    switch (opt) {
                        case "1" -> {
                            try {
                                participants = FileHandler.readParticipants(PARTICIPANTS_CSV);
                                System.out.println("CSV loaded. Participants: " + participants.size());
                                AppLogger.log("Organizer loaded CSV, participants=" + participants.size());
                            } catch (Exception e) {
                                System.err.println("Failed to load CSV: " + e.getMessage());
                                AppLogger.log("Load CSV failed: " + e.getMessage());
                            }
                        }
                        case "2" -> {
                            System.out.print("Enter team size (N) [3 - 30]: ");
                            String input = sc.nextLine().trim();
                            try {
                                int size = Integer.parseInt(input);
                                if (size < 3 || size > 30) {
                                    System.err.println("Invalid team size. Must be between 3 and 30.");
                                    AppLogger.log("Organizer provided invalid team size: " + input);
                                } else {
                                    storedTeamSize = size;
                                    System.out.println("Team size stored: " + storedTeamSize);
                                    AppLogger.log("Organizer set team size: " + storedTeamSize);
                                }
                            } catch (NumberFormatException nfe) {
                                System.err.println("Please enter a valid integer.");
                            }
                        }
                        case "3" -> {
                            if (participants == null || participants.isEmpty()) {
                                System.err.println("No participants loaded. Load CSV first.");
                                break;
                            }
                            if (storedTeamSize == null) {
                                System.err.println("Team size not set. Input team size first.");
                                break;
                            }
                            // Run TeamBuilder on background thread (demonstrate concurrency)
                            System.out.println("Initiating team formation (background)...");
                            AppLogger.log("Organizer initiated team formation. teamSize=" + storedTeamSize + ", participants=" + participants.size());
                            ExecutorService ex = Executors.newSingleThreadExecutor();
                            TeamBuilder builder = new TeamBuilder(participants, storedTeamSize);
                            Future<List<Team>> future = ex.submit(builder::formTeams);
                            try {
                                lastFormedTeams = future.get();
                                System.out.println("Team formation completed. Teams created: " + lastFormedTeams.size());
                                AppLogger.log("Team formation completed. teams=" + lastFormedTeams.size());
                                // Save formed teams to CSV
                                FileHandler.writeTeams(FORMED_TEAMS_CSV, lastFormedTeams);
                                System.out.println("Saved formed teams to: " + FORMED_TEAMS_CSV);
                            } catch (ExecutionException ee) {
                                System.err.println("Team formation failed: " + ee.getCause().getMessage());
                                AppLogger.log("Team formation failed: " + ee.getCause().getMessage());
                            } catch (InterruptedException ie) {
                                System.err.println("Team formation interrupted.");
                                AppLogger.log("Team formation interrupted.");
                                Thread.currentThread().interrupt();
                            } catch (Exception e) {
                                System.err.println("Error saving teams: " + e.getMessage());
                                AppLogger.log("Error saving teams: " + e.getMessage());
                            } finally {
                                ex.shutdown();
                            }
                        }
                        case "4" -> {
                            if (lastFormedTeams != null && !lastFormedTeams.isEmpty()) {
                                displayTeamsInMemory(lastFormedTeams);
                            } else {
                                // try to read from CSV file
                                displayTeamsFromFile(FORMED_TEAMS_CSV);
                            }
                        }

                        case "5" -> {
                            System.out.print("Enter Participant ID to remove: ");
                            String rid = sc.nextLine().trim();

                            boolean ok = FileHandler.removeParticipantById(PARTICIPANTS_CSV, rid);

                            if (ok) {
                                System.out.println("Participant removed successfully.");
                                AppLogger.log("Participant " + rid + " removed from CSV.");
                            } else {
                                System.out.println("Participant ID not found.");
                            }
                        }

                        case "0" -> back = true;
                        default -> System.err.println("Invalid option.");
                    }
                }
            }else if (roleChoice.equals("2")) {
                // Participant menu
                boolean back = false;
                while (!back) {
                    System.out.println("\nParticipant Menu:");
                    System.out.println("1. Complete survey (add participant)");
                    System.out.println("2. View generated teams");
                    System.out.println("0. Back");
                    System.out.print("Choose: ");
                    String opt = sc.nextLine().trim();

                    switch (opt) {

                        case "1" -> {
                            try {
                                // Read new participant info
                                Participant newP = readParticipantFromConsole();

                                // Append to CSV
                                appendParticipantToCSV(newP, PARTICIPANTS_CSV);

                                // Add to in-memory list
                                participants.add(newP);

                                System.out.println("Survey submitted successfully!");
                                AppLogger.log("Participant submitted survey: " + newP.getId());
                            }
                            catch (Exception e) {
                                System.err.println("Failed to submit survey: " + e.getMessage());
                                AppLogger.log("Participant submit failed: " + e.getMessage());
                            }
                        }

                        case "2" -> {
                            if (lastFormedTeams != null && !lastFormedTeams.isEmpty()) {
                                displayTeamsInMemory(lastFormedTeams);
                            } else {
                                displayTeamsFromFile(FORMED_TEAMS_CSV);
                            }
                        }

                        case "0" -> back = true;

                        default -> System.err.println("Invalid option.");
                    }
                }
            } else {
                System.err.println("Invalid selection. Please enter 1 (Organizer) or 2 (Participant) or 0 to exit.");
            }
        } // while
    } // main

    // ---------------- Helper methods ----------------

    private static Participant readParticipantFromConsole() {
        // 🔄 Always refresh participants list from CSV to ensure duplicate checking works
        try {
            participants = FileHandler.readParticipants(PARTICIPANTS_CSV);
        } catch (Exception e) {
            System.err.println("Could not load CSV for duplicate ID checking.");
        }
        // --- ID validation ---
        String id;
        while (true) {
            System.out.print("Enter ID (e.g. P101): ");
            id = sc.nextLine().trim();

            boolean exists = false;
            for (Participant p : participants) {
                if (p.getId().equalsIgnoreCase(id)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                System.out.println("❌ ID already exists. Please enter a new ID.");
            } else {
                break;
            }
        }

        // --- Basic info ---
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        // --- Email validation ---
        String email;
        while (true) {
            System.out.print("Email: ");
            email = sc.nextLine().trim();

            // Simple email validation regex
            if (email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                break;
            } else {
                System.out.println("❌ Invalid email format. Please enter a valid email (e.g., user@example.com).");
            }
        }

        System.out.print("Preferred game: ");
        String game = sc.nextLine().trim();

        int skill = readIntWithRange("Skill level (1-10): ", 1, 10);

        System.out.println("Preferred role (choose number):");
        System.out.println("1. Strategist  2. Defender  3. Attacker  4. Coordinator");
        String role = switch (readIntWithRange("Role (1-4): ", 1, 4)) {
            case 1 -> "Strategist";
            case 2 -> "Defender";
            case 3 -> "Attacker";
            default -> "Coordinator";
        };

        // -------------------------------
        //  Personality Survey (5 questions)
        // -------------------------------
        System.out.println("\nRate each question from 1 (Strongly Disagree) to 5 (Strongly Agree)");

        int q1 = readIntWithRange("Q1. I enjoy taking the lead: ", 1, 5);
        int q2 = readIntWithRange("Q2. I prefer analyzing situations: ", 1, 5);
        int q3 = readIntWithRange("Q3. I work well with others: ", 1, 5);
        int q4 = readIntWithRange("Q4. I stay calm under pressure: ", 1, 5);
        int q5 = readIntWithRange("Q5. I like making quick decisions: ", 1, 5);

        int total = q1 + q2 + q3 + q4 + q5;         // 5–25
        int scaledScore = total * 4;                // 0–100 scale

        // classify personality using your rules
        String personalityType = PersonalityClassifier.classify(scaledScore);

        System.out.println("\nCalculated Personality Score: " + scaledScore);
        System.out.println("Assigned Personality Type: " + personalityType);

        // create and return participant
        return new Participant(id, name, email, game, role, skill, scaledScore, personalityType);
    }
    private static int readIntWithRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                int x = Integer.parseInt(s);
                if (x < min || x > max) {
                    System.err.println("Value must be between " + min + " and " + max);
                    continue;
                }
                return x;
            } catch (NumberFormatException e) {
                System.err.println("Please enter a valid integer.");
            }
        }
    }

    private static void appendParticipantToCSV(Participant p, String path) throws IOException {
        // simple append: CSV columns: ID,Name,Email,PreferredGame,Skill,PreferredRole,PersonalityScore,PersonalityType
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
            String line = String.join(",",
                    p.getId(),
                    p.getName(),
                    p.getEmail(),
                    p.getGame(),
                    String.valueOf(p.getSkill()),
                    p.getRole(),
                    String.valueOf(p.getPersonalityScore()),
                    p.getPersonalityType()
            );
            bw.newLine();
            bw.write(line);
            bw.flush();
        }
    }

    private static void displayTeamsInMemory(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            System.out.println("No teams formed yet.");
            return;
        }
        System.out.println("\n---- Teams (in-memory) ----");
        int t = 1;
        for (Team team : teams) {
            System.out.println("Team " + t + " (size=" + team.size() + ", avgSkill=" + String.format("%.2f", team.averageSkill()) + "):");
            // order members as Leader -> Thinker -> Balanced
            team.getMembers().sort(Comparator.comparingInt(m -> personalityRank(m.getPersonalityType())));
            for (Participant p : team.getMembers()) System.out.println("  " + p.toString());
            t++;
            System.out.println();
        }
    }

    private static void displayTeamsFromFile(String path) {
        try (Scanner fileScanner = new Scanner(new java.io.File(path))) {
            System.out.println("\n---- Teams (from file) ----");
            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();
                System.out.println(line);
            }
        } catch (Exception e) {
            System.out.println("No formed teams file found or cannot read file: " + e.getMessage());
        }
    }

    public static int personalityRank(String p) {
        return switch (p) {
            case "Leader" -> 1;
            case "Thinker" -> 2;
            case "Balanced" -> 3;
            default -> 4;
        };
    }
}