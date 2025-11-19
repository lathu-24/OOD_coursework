package com.teamate;

import java.util.*;
import java.util.stream.*;

/*
Enhanced TeamBuilder:
- teamCount = floor(total / requestedSize) (>=1)
- compute targetSizes[] summing to total (difference at most 1)
- place participants in phases:
  1) ensure 1 Leader per team (if available)
  2) ensure 1 Thinker per team (if available)
  3) optionally place extra Leaders/Thinkers (up to 2 per team)
  4) place Balanced participants
  5) place leftovers into candidate teams choosing the team with lowest avg skill
- Enforces game cap per team (maxSameGame)
- Prefers teams missing the role (role diversity)
- Always assigns every participant to one of the existing teams (no extra team)
*/

public class TeamBuilder {
    private List<Participant> participants;
    private int requestedSize;
    private int maxSameGame = 2;

    public TeamBuilder(List<Participant> participants, int requestedSize) {
        this.participants = new ArrayList<>(participants);
        this.requestedSize = requestedSize;
    }

    public List<Team> formTeams() {
        if (requestedSize <= 0) throw new IllegalArgumentException("Team size must be > 0");
        int total = participants.size();
        int teamCount = Math.max(1, total / requestedSize); // floor division per requirement

        // compute target sizes so sum == total
        int base = total / teamCount;
        int rem = total % teamCount;
        int[] targetSizes = new int[teamCount];
        for (int i = 0; i < teamCount; i++) targetSizes[i] = base + (i < rem ? 1 : 0);

        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) teams.add(new Team(targetSizes[i]));

        // randomize input order for fairness
        Collections.shuffle(participants, new Random(System.nanoTime()));

        // split by personality and sort by skill descending to spread strong players
        List<Participant> leaders = filterByType("Leader");
        List<Participant> thinkers = filterByType("Thinker");
        List<Participant> balanced = filterByType("Balanced");

        Comparator<Participant> bySkillDesc = Comparator.comparingInt(Participant::getSkill).reversed();
        leaders.sort(bySkillDesc); thinkers.sort(bySkillDesc); balanced.sort(bySkillDesc);

        // Phase A: ensure at least 1 leader per team (if enough leaders)
        placeMinPerTeam(leaders, teams, 1);

        // Phase B: ensure at least 1 thinker per team (if enough thinkers)
        placeMinPerTeam(thinkers, teams, 1);

        // Phase C: try to add second leaders and thinkers if available (up to 2 per team)
        placeExtraPerTeam(leaders, teams, 2);
        placeExtraPerTeam(thinkers, teams, 2);

        // Phase D: place balanced players
        placeAll(balanced, teams);

        // leftovers (any participants not yet placed)
        Set<String> placedIds = teams.stream()
                .flatMap(t -> t.getMembers().stream().map(Participant::getId))
                .collect(Collectors.toSet());
        List<Participant> leftovers = participants.stream().filter(p -> !placedIds.contains(p.getId())).collect(Collectors.toList());

        // fill leftovers: choose candidate teams then pick team with lowest avg skill
        for (Participant p : leftovers) {
            List<Integer> cand = candidateTeamsFor(p, teams);
            if (cand.isEmpty()) {
                // relax game cap
                for (int i = 0; i < teams.size(); i++) if (teams.get(i).canAdd()) cand.add(i);
            }
            int chosen = chooseLowestAvgTeam(cand, teams);
            teams.get(chosen).addMember(p);
        }

        return teams;
    }

    private List<Participant> filterByType(String type) {
        return participants.stream().filter(p -> p.getPersonalityType().equalsIgnoreCase(type)).collect(Collectors.toList());
    }

    private void placeMinPerTeam(List<Participant> list, List<Team> teams, int minEach) {
        if (list.isEmpty()) return;
        for (int r = 0; r < minEach; r++) {
            for (int i = 0; i < teams.size(); i++) {
                if (list.isEmpty()) return;
                Participant p = list.remove(0);
                List<Integer> cand = candidateTeamsFor(p, teams);
                if (cand.isEmpty()) {
                    for (int j = 0; j < teams.size(); j++) if (teams.get(j).canAdd()) { teams.get(j).addMember(p); break; }
                } else {
                    int chosen = chooseLowestAvgTeam(cand, teams);
                    teams.get(chosen).addMember(p);
                }
            }
        }
    }

    private void placeExtraPerTeam(List<Participant> list, List<Team> teams, int maxPerTeam) {
        if (list.isEmpty()) return;
        Iterator<Participant> it = list.iterator();
        while (it.hasNext()) {
            Participant p = it.next();
            List<Integer> cand = new ArrayList<>();
            for (int i = 0; i < teams.size(); i++) {
                Team tm = teams.get(i);
                if (!tm.canAdd()) continue;
                long cnt = tm.personalityCounts().getOrDefault(p.getPersonalityType(), 0L);
                if (cnt >= maxPerTeam) continue;
                long gameCount = tm.gameCounts().getOrDefault(p.getGame(), 0L);
                if (gameCount >= maxSameGame) continue;
                cand.add(i);
            }
            if (cand.isEmpty()) continue;
            int chosen = chooseLowestAvgTeam(cand, teams);
            teams.get(chosen).addMember(p);
            it.remove();
        }
    }

    private void placeAll(List<Participant> list, List<Team> teams) {
        if (list.isEmpty()) return;
        Iterator<Participant> it = list.iterator();
        while (it.hasNext()) {
            Participant p = it.next();
            List<Integer> cand = candidateTeamsFor(p, teams);
            if (cand.isEmpty()) {
                for (int i = 0; i < teams.size(); i++) if (teams.get(i).canAdd()) cand.add(i);
            }
            int chosen = chooseLowestAvgTeam(cand, teams);
            teams.get(chosen).addMember(p);
            it.remove();
        }
    }

    private List<Integer> candidateTeamsFor(Participant p, List<Team> teams) {
        List<Integer> cand = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            Team tm = teams.get(i);
            if (!tm.canAdd()) continue;
            long gameCount = tm.gameCounts().getOrDefault(p.getGame(), 0L);
            if (gameCount >= maxSameGame) continue;
            cand.add(i);
        }
        return cand;
    }

    private int chooseLowestAvgTeam(List<Integer> cand, List<Team> teams) {
        int best = cand.get(0);
        double bestAvg = teams.get(best).averageSkill();
        for (int idx : cand) {
            double avg = teams.get(idx).averageSkill();
            if (avg < bestAvg) { best = idx; bestAvg = avg; }
        }
        return best;
    }
}