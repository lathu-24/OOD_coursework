package com.teamate;

import java.io.*;
import java.util.*;

public class FileHandler {
    // Reads from path like "data/participants_sample.csv"
    // Expected header: ID,Name,Email,PreferredGame,Skill,PreferredRole,PersonalityScore,PersonalityType
    public static List<Participant> readParticipants(String path) throws IOException {
        List<Participant> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line; int ln = 0;
            while ((line = br.readLine()) != null) {
                ln++;
                if (ln == 1) continue; // skip header
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 8) throw new IOException("Invalid CSV at line " + ln);
                String id = parts[0].trim();
                String name = parts[1].trim();
                String email = parts[2].trim();
                String game = parts[3].trim();
                int skill = Integer.parseInt(parts[4].trim());
                String role = parts[5].trim();
                int pScore = Integer.parseInt(parts[6].trim());
                String pType = parts[7].trim();
                Participant p = new Participant(id, name, email, game, role, skill, pScore, pType);
                list.add(p);
            }
        }
        return list;
    }

    public static void writeTeams(String path, List<Team> teams) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            int t = 1;
            for (Team team : teams) {
                bw.write("Team " + t);
                for (Participant p : team.getMembers()) {
                    bw.write("," + p.getId());
                }
                bw.newLine();
                t++;
            }
        }
    }
}