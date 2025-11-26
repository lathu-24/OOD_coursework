package com.teamate;

import java.util.*;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        try {
            String csvPath = "data/participants_sample.csv"; // fixed CSV file path
            List<Participant> participants = FileHandler.readParticipants(csvPath);
            System.out.println("Loaded participants: " + participants.size());

            Scanner sc = new Scanner(System.in);
            System.out.print("Enter team size (N): ");
            int teamSize = sc.nextInt();

            // ---------- TEAM SIZE VALIDATION ----------
            if (teamSize < 3 || teamSize > 50) {
                System.err.println("Invalid team size. Team size must be between 3 and 50.");
                return;
            }
            // -------------------------------------------

            ExecutorService ex = Executors.newSingleThreadExecutor();
            TeamBuilder builder = new TeamBuilder(participants, teamSize);
            Future<List<Team>> future = ex.submit(() -> builder.formTeams());
            System.out.println("Forming teams in background...");
            List<Team> teams = future.get();
            ex.shutdown();

            System.out.println("\n---- Teams ----");
            int t=1;
            for (Team team : teams) {
                System.out.println("Team " + t + " (size=" + team.size() + ", avgSkill=" + String.format("%.2f", team.averageSkill()) + "):");
                for (Participant p : team.getMembers()) {
                    System.out.println("  " + p.toString());
                }
                t++; System.out.println();
            }

            String out = "data/formed_teams.csv";
            FileHandler.writeTeams(out, teams);
            System.out.println("Saved formed teams to: " + out);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}