package com.teamate;

import java.util.*;
import java.util.stream.*;

public class Team {

    private List<Participant> members = new ArrayList<>();
    private int targetSize;

    public Team(int targetSize) {
        this.targetSize = targetSize;
    }

    public boolean canAdd() {
        return members.size() < targetSize;
    }

    public boolean isFull() {
        return members.size() >= targetSize;
    }

    public boolean addMember(Participant p) {
        if (!canAdd()) return false;
        members.add(p);
        return true;
    }

    public int getTargetSize() {
        return targetSize;
    }

    public int size() {
        return members.size();
    }

    public List<Participant> getMembers() {
        return members;
    }

    public double averageSkill() {
        if (members.isEmpty()) return 0.0;
        return members.stream().mapToInt(Participant::getSkill).average().orElse(0.0);
    }

    public Map<String, Long> gameCounts() {
        return members.stream().collect(Collectors.groupingBy(Participant::getGame, Collectors.counting()));
    }

    public Set<String> roles() {
        return members.stream().map(Participant::getRole).collect(Collectors.toSet());
    }

    public Map<String, Long> personalityCounts() {
        return members.stream().collect(Collectors.groupingBy(Participant::getPersonalityType, Collectors.counting()));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Participant p : members)
            sb.append(p.getId()).append("(").append(p.getName()).append("), ");
        if (sb.length()>=2) sb.setLength(sb.length()-2);
        return sb.toString();
    }
}