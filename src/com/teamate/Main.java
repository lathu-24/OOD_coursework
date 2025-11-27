package com.teamate;

import java.util.*;
import applogger.AppLogger;
import java.util.concurrent.*;


public class Main {
    public static void main(String[] args) {

        AppLogger.log("System started. Loading CSV file...");
        try {
            String csvPath = "data/participants_sample.csv"; // fixed CSV file path
            List<Participant> participants = FileHandler.readParticipants(csvPath);
            AppLogger.log("Team formation started.");
            System.out.println("Loaded participants: " + participants.size());
            Scanner sc = new Scanner(System.in);
            System.out.print("Enter team size : ");

            int teamSize = sc.nextInt();

            if (teamSize < 3 || teamSize > 30) {
                System.err.println("Invalid team size. Team size must be between 3 and 30.");
                return;
            }


            TeamBuilder builder = new TeamBuilder(participants, teamSize);
            System.out.println("Forming teams...");
            List<Team> teams = builder.formTeams();

            System.out.println("\n---- Teams ----");
            int t = 1;
            for (Team team : teams) {

                // ✔ Sort team members in correct personality order
                team.getMembers().sort((a, b) -> {
                    return personalityRank(a.getPersonalityType()) - personalityRank(b.getPersonalityType());
                });

                System.out.println("Team " + t + " (size=" + team.size()
                        + ", avgSkill=" + String.format("%.2f", team.averageSkill()) + "):");

                for (Participant p : team.getMembers()) {
                    System.out.println("  " + p.toString());
                }
                t++;
                System.out.println();
            }

            String out = "data/formed_teams.csv";
            FileHandler.writeTeams(out, teams);
            System.out.println("Saved formed teams to: " + out);

        } catch (Exception e) {
            System.err.println("Invalid input");
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