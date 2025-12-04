package com.teamate;
import applogger.AppLogger;
import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;

/**
 * TeamBuilder - improved implementation following user requirements:
 *  - fixed number of teams = floor(total / requestedSize) (>=1)
 *  - target sizes sum == total and differ by at most 1
 *  - distribute Leaders, Thinkers, Balanced in that order across teams
 *  - attempt to keep average skill balanced across teams
 *  - enforce game cap per team (maxSameGame)
 *  - attempt to ensure leaders <= thinkers <= balanced per team via corrective swaps
 *  - assign every participant (no leftovers)
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
        AppLogger.log("Assigning participants to teams...");
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

        // shuffle input for randomness
        Collections.shuffle(participants, new Random(System.nanoTime()));

        // split participants by personality
        List<Participant> leaders = filterByType("Leader");
        List<Participant> thinkers = filterByType("Thinker");
        List<Participant> balanced = filterByType("Balanced");

        // sort each category by skill DESC (so high skill are spread when we assign by lowest-average heuristic)
        Comparator<Participant> bySkillDesc = Comparator.comparingInt(Participant::getSkill).reversed();
        leaders.sort(bySkillDesc);
        thinkers.sort(bySkillDesc);
        balanced.sort(bySkillDesc);

        // compute quotas per team for each type
        int[] leaderQuota = computeQuotas(leaders.size(), teamCount);
        int[] thinkerQuota = computeQuotas(thinkers.size(), teamCount);
        int[] balancedQuota = computeQuotas(balanced.size(), teamCount);

        // Phase 1: distribute leaders according to quotas
        distributeWithQuotas(leaders, leaderQuota, teams);

        // Phase 2: distribute thinkers according to quotas
        distributeWithQuotas(thinkers, thinkerQuota, teams);

        // Phase 3: distribute balanced according to quotas
        distributeWithQuotas(balanced, balancedQuota, teams);

        // Collect any participants not placed (should be none, but keep safe)
        Set<String> placed = teams.stream()
                .flatMap(t -> t.getMembers().stream().map(Participant::getId))
                .collect(Collectors.toSet());
        List<Participant> leftovers = participants.stream()
                .filter(p -> !placed.contains(p.getId()))
                .collect(Collectors.toList());

        // Fill leftovers preferring teams that can accept (respecting game cap), pick lowest avg skill among candidates
        for (Participant p : leftovers) {
            List<Integer> cand = candidateTeamsFor(p, teams);
            if (cand.isEmpty()) {
                // relax game cap
                for (int i = 0; i < teams.size(); i++) if (teams.get(i).canAdd()) cand.add(i);
            }
            int chosen = chooseLowestAvgTeam(cand, teams);
            teams.get(chosen).addMember(p);
        }

        // Corrective phase: try to ensure leaders <= thinkers <= balanced per team by simple swaps
        enforceLTBequality(teams);

        // Final safety fill (in case some teams still have capacity)
        for (Participant p : participants) {
            // if p already placed skip
            boolean isPlaced = teams.stream().anyMatch(t -> t.getMembers().stream().anyMatch(m -> m.getId().equals(p.getId())));
            if (isPlaced) continue;
            for (Team t : teams) {
                if (t.canAdd()) { t.addMember(p); break; }
            }
        }

        // Optionally: sort members in each team to show leader->thinker->balanced order in output
        for (Team t : teams) {
            t.getMembers().sort(Comparator.comparingInt((Participant p) -> personalityOrder(p.getPersonalityType()))
                    .thenComparing(Participant::getName));
        }

        return teams;
    }

    // compute quotas array length = teamCount, base + distribute remainder to first rem teams
    private int[] computeQuotas(int totalOfType, int teamCount) {
        int[] quotas = new int[teamCount];
        if (teamCount == 0) return quotas;
        int base = totalOfType / teamCount;
        int rem = totalOfType % teamCount;
        for (int i = 0; i < teamCount; i++) quotas[i] = base + (i < rem ? 1 : 0);
        return quotas;
    }

    // Distribute participants according to quotas. For each participant we pick a candidate team (teams with remaining quota and canAdd and respecting game cap)
    // from those candidates choose the one with lower avg skill (to balance).
    private void distributeWithQuotas(List<Participant> list, int[] quotas, List<Team> teams) {
        if (list == null || list.isEmpty()) return;
        int teamCount = teams.size();
        int listIndex = 0;

        // We'll iterate participants in the list and try to place them respecting per-team quota.
        // To spread skill, list is expected to be sorted by skill desc already.
        for (Participant p : new ArrayList<>(list)) {
            // find candidate teams which still need quota (quota > 0), canAdd and respect game cap
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < teamCount; i++) {
                if (quotas[i] <= 0) continue;
                Team tm = teams.get(i);
                if (!tm.canAdd()) continue;
                long gameCount = tm.gameCounts().getOrDefault(p.getGame(), 0L);
                if (gameCount >= maxSameGame) continue;
                candidates.add(i);
            }
            if (candidates.isEmpty()) {
                // no team wants this type by quota / game cap - relax: pick any team with capacity and respecting game cap
                for (int i = 0; i < teamCount; i++) {
                    Team tm = teams.get(i);
                    if (!tm.canAdd()) continue;
                    long gameCount = tm.gameCounts().getOrDefault(p.getGame(), 0L);
                    if (gameCount >= maxSameGame) continue;
                    candidates.add(i);
                }
            }
            if (candidates.isEmpty()) {
                // fully relax - any team with capacity
                for (int i = 0; i < teamCount; i++) if (teams.get(i).canAdd()) candidates.add(i);
            }

            if (!candidates.isEmpty()) {
                int chosen = chooseLowestAvgTeam(candidates, teams);
                teams.get(chosen).addMember(p);
                // decrement quota if that team was in quota list and quotas[chosen] > 0
                if (quotas[chosen] > 0) quotas[chosen]--;
            } else {
                System.out.println(" ");
                // completely full (shouldn't happen) - skip (will be handled in leftovers)
            }
        }

        // remove assigned participants from input list
        Set<String> placed = teams.stream().flatMap(t -> t.getMembers().stream().map(Participant::getId)).collect(Collectors.toSet());
        list.removeIf(x -> placed.contains(x.getId()));
    }

    // return candidate team indices that can accept p while respecting game cap
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

    // choose the team index with lowest current average skill among candidate indices
    private int chooseLowestAvgTeam(List<Integer> cand, List<Team> teams) {
        if (cand == null || cand.isEmpty()) return 0;
        int best = cand.get(0);
        double bestAvg = teams.get(best).averageSkill();
        for (int idx : cand) {
            double avg = teams.get(idx).averageSkill();
            if (avg < bestAvg) { best = idx; bestAvg = avg; }
        }
        return best;
    }

    // Convert personality string to order: Leader(0)->Thinker(1)->Balanced(2) to sort leaders first
    private int personalityOrder(String pType) {
        if (pType == null) return 3;
        switch (pType.toLowerCase()) {
            case "leader": return 0;
            case "thinker": return 1;
            case "balanced": return 2;
            default: return 3;
        }
    }

    // Simple corrective routine to try to enforce L <= T <= B per team by swapping if possible
    private void enforceLTBequality(List<Team> teams) {
        boolean changed = true;
        int passes = 0;
        while (changed && passes < 5) { // limit passes to avoid heavy loops
            changed = false;
            passes++;
            for (int i = 0; i < teams.size(); i++) {
                Team ti = teams.get(i);
                Map<String, Long> ci = ti.personalityCounts();
                int Li = ci.getOrDefault("Leader", 0L).intValue();
                int Ti = ci.getOrDefault("Thinker", 0L).intValue();
                int Bi = ci.getOrDefault("Balanced", 0L).intValue();

                // if L > T try to find a team j where L_j < T_j to swap a leader from i with a thinker/balanced from j
                if (Li > Ti) {
                    for (int j = 0; j < teams.size(); j++) {
                        if (i == j) continue;
                        Team tj = teams.get(j);
                        Map<String, Long> cj = tj.personalityCounts();
                        int Lj = cj.getOrDefault("Leader", 0L).intValue();
                        int Tj = cj.getOrDefault("Thinker", 0L).intValue();
                        int Bj = cj.getOrDefault("Balanced", 0L).intValue();

                        if (Lj < Tj) {
                            // try to swap one leader from ti with one thinker from tj (or balanced if thinker not found)
                            Participant leaderFromI = findParticipantByType(ti, "Leader");
                            Participant thinkerFromJ = findParticipantByType(tj, "Thinker");
                            Participant balancedFromJ = findParticipantByType(tj, "Balanced");

                            if (leaderFromI != null && thinkerFromJ != null) {
                                // perform swap if it doesn't violate game cap
                                if (canSwap(ti, tj, leaderFromI, thinkerFromJ)) {
                                    ti.getMembers().remove(leaderFromI); tj.getMembers().remove(thinkerFromJ);
                                    ti.getMembers().add(thinkerFromJ); tj.getMembers().add(leaderFromI);
                                    changed = true; break;
                                }
                            } else if (leaderFromI != null && balancedFromJ != null) {
                                if (canSwap(ti, tj, leaderFromI, balancedFromJ)) {
                                    ti.getMembers().remove(leaderFromI); tj.getMembers().remove(balancedFromJ);
                                    ti.getMembers().add(balancedFromJ); tj.getMembers().add(leaderFromI);
                                    changed = true; break;
                                }
                            }
                        }
                    }
                }

                // ensure T <= B similarly
                if (!changed) {
                    ci = ti.personalityCounts();
                    Li = ci.getOrDefault("Leader", 0L).intValue();
                    Ti = ci.getOrDefault("Thinker", 0L).intValue();
                    Bi = ci.getOrDefault("Balanced", 0L).intValue();

                    if (Ti > Bi) {
                        for (int j = 0; j < teams.size(); j++) {
                            if (i == j) continue;
                            Team tj = teams.get(j);
                            Map<String, Long> cj = tj.personalityCounts();
                            int Tj = cj.getOrDefault("Thinker", 0L).intValue();
                            int Bj = cj.getOrDefault("Balanced", 0L).intValue();
                            if (Tj < Bj) {
                                Participant thinkerFromI = findParticipantByType(ti, "Thinker");
                                Participant balancedFromJ = findParticipantByType(tj, "Balanced");
                                if (thinkerFromI != null && balancedFromJ != null) {
                                    if (canSwap(ti, tj, thinkerFromI, balancedFromJ)) {
                                        ti.getMembers().remove(thinkerFromI); tj.getMembers().remove(balancedFromJ);
                                        ti.getMembers().add(balancedFromJ); tj.getMembers().add(thinkerFromI);
                                        changed = true; break;
                                    }
                                }
                            }
                        }
                    }
                }
            } // end for teams
        } // end while
    }

    // find participant of given personalityType in team (returns first match)
    private Participant findParticipantByType(Team t, String type) {
        for (Participant p : t.getMembers()) {
            if (p.getPersonalityType().equalsIgnoreCase(type)) return p;
        }
        return null;
    }

    // check that swapping a and b between tA and tB doesn't violate game cap in either resulting team
    private boolean canSwap(Team tA, Team tB, Participant a, Participant b) {
        // after swap, counts for game in tA: (current count - (a.game==?) + (b.game==?))
        Map<String, Long> aGames = tA.gameCounts();
        Map<String, Long> bGames = tB.gameCounts();

        long aCountForBGame = aGames.getOrDefault(b.getGame(), 0L);
        if (!a.getGame().equalsIgnoreCase(b.getGame())) { // if same game, counts unchanged
            aCountForBGame += 1;
        }
        if (aCountForBGame > maxSameGame) return false;

        long bCountForAGame = bGames.getOrDefault(a.getGame(), 0L);
        if (!a.getGame().equalsIgnoreCase(b.getGame())) {
            bCountForAGame += 1;
        }
        if (bCountForAGame > maxSameGame) return false;

        return true;
    }

    // helper: filter participants by personality from the full list
    private List<Participant> filterByType(String type) {
        List<Participant> r = new ArrayList<>();
        for (Participant p : participants) {
            if (p.getPersonalityType().equalsIgnoreCase(type)) r.add(p);
        }
        return r;
    }

}