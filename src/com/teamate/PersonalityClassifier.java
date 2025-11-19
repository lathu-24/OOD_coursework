package com.teamate;

public class PersonalityClassifier {
    public static String classify(int score) {
        if (score >= 90) return "Leader";
        if (score >= 70) return "Balanced";
        return "Thinker";
    }
}