package com.teamate;

import applogger.AppLogger;
import java.io.*;
import java.util.*;

public class FileHandler {

    private static final String HEADER =
            "ID,Name,Email,PreferredGame,Skill,PreferredRole,PersonalityScore,PersonalityType";


    // READ CSV
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

    // WRITE GENERATED TEAMS
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


    //remove participant
    public static boolean removeParticipantById(String path, String idToRemove) {
        List<String> lines = new ArrayList<>();
        boolean removed = false;

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(idToRemove + ",")) {
                    removed = true; // found and removed
                    continue;       // skip adding this line
                }
                lines.add(line);
            }
        } catch (Exception e) {
            System.out.println("Error reading CSV: " + e.getMessage());
            return false;
        }

        if (!removed) return false;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            for (String l : lines) {
                bw.write(l);
                bw.newLine();
            }
        } catch (Exception e) {
            System.out.println("Error writing CSV: " + e.getMessage());
            return false;
        }

        return true;
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