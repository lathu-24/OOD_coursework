package com.teamate;

import applogger.AppLogger;
import java.io.*;
import java.util.*;

public class FileHandler {

    private static final String HEADER =
            "ID,Name,Email,PreferredGame,Skill,PreferredRole,PersonalityScore,PersonalityType";

    // --------------------------------------------------------------------
    // READ CSV
    // --------------------------------------------------------------------
    public static List<Participant> readParticipants(String path) throws IOException {
        List<Participant> list = new ArrayList<>();

        File file = new File(path);
        if (!file.exists()) {
            AppLogger.log("CSV file not found. Creating new file...");
            createCSVWithHeader(path);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int ln = 0;

            while ((line = br.readLine()) != null) {
                ln++;
                if (ln == 1) continue;  // skip header
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 8)
                    throw new IOException("Invalid CSV format at line " + ln);

                Participant p = new Participant(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        parts[3].trim(),
                        parts[5].trim(),
                        Integer.parseInt(parts[4].trim()),
                        Integer.parseInt(parts[6].trim()),
                        parts[7].trim()
                );

                list.add(p);
            }
        }

        AppLogger.log("Loaded " + list.size() + " participants from CSV.");
        return list;
    }

    // --------------------------------------------------------------------
    // WRITE GENERATED TEAMS
    // --------------------------------------------------------------------
    public static void writeTeams(String path, List<Team> teams) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {

            bw.write("TeamNo,Members\n");  // header for clarity
            int t = 1;
            for (Team team : teams) {
                StringBuilder line = new StringBuilder("Team " + t + ",");

                for (Participant p : team.getMembers()) {
                    line.append(p.getId()).append(" ");
                }

                bw.write(line.toString().trim());
                bw.newLine();
                t++;
            }
        }

        AppLogger.log("Generated teams saved to " + path);
    }

    // --------------------------------------------------------------------
    // APPEND NEW PARTICIPANT (from survey)
    // --------------------------------------------------------------------
    public static void appendParticipant(String path, Participant p) throws IOException {

        File file = new File(path);
        if (!file.exists()) {
            createCSVWithHeader(path);
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
            bw.write(toCSV(p));
            bw.newLine();
        }

        AppLogger.log("New participant added: " + p.getId());
    }

    // Convert participant → CSV line
    private static String toCSV(Participant p) {
        return p.getId() + "," + p.getName() + "," + p.getEmail() + "," +
                p.getGame() + "," + p.getSkill() + "," + p.getRole() + "," +
                p.getPersonalityScore() + "," + p.getPersonalityType();
    }

    // --------------------------------------------------------------------
    // GENERATE NEW PARTICIPANT ID (P001, P002 …)
    // --------------------------------------------------------------------
    public static String generateNewID(String path) throws IOException {
        List<Participant> list = readParticipants(path);

        int max = 0;
        for (Participant p : list) {
            try {
                int num = Integer.parseInt(p.getId().substring(1));
                if (num > max) max = num;
            } catch (Exception ignored) {}
        }

        int next = max + 1;
        return String.format("P%03d", next);
    }

    // Create CSV with header only
    private static void createCSVWithHeader(String path) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write(HEADER);
            bw.newLine();
        }
        AppLogger.log("CSV file created with header: " + path);
    }
}