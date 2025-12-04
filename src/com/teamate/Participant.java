package com.teamate;

public class Participant {
    private String id, name, email, game, role, personalityType;
    private int skill, personalityScore;

    public Participant(String id, String name, String email, String game, String role, int skill, int personalityScore, String personalityType) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.game = game;
        this.role = role;
        this.skill = skill;
        this.personalityScore = personalityScore;
        this.personalityType = personalityType;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getGame() { return game; }
    public String getRole() { return role; }
    public int getSkill() { return skill; }
    public int getPersonalityScore() { return personalityScore; }
    public String getPersonalityType() { return personalityType; }

    @Override
    public String toString() {
        return id + ":" + name + " (" + game + "," + role + ",skill=" + skill + "," + personalityType + ")";
    }
}