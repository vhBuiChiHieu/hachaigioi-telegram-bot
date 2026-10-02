package com.acme.moviebot.catalog.internal.domain;

import java.util.Arrays;
import java.util.List;

public final class FuzzySearchMatcher {

    public static final double MIN_SCORE = 0.70;
    private static final double MIN_TOKEN_SCORE = 0.65;

    private FuzzySearchMatcher() {
    }

    public static double score(String query, String candidate) {
        String normalizedQuery = SearchNormalizer.normalize(query);
        String normalizedCandidate = SearchNormalizer.normalize(candidate);
        if (normalizedQuery.isBlank() || normalizedCandidate.isBlank()) {
            return 0;
        }
        if (normalizedCandidate.contains(normalizedQuery)) {
            return 1;
        }

        List<String> queryTokens = Arrays.stream(normalizedQuery.split(" ")).distinct().toList();
        List<String> candidateTokens = Arrays.stream(normalizedCandidate.split(" ")).distinct().toList();
        double matchedScore = 0;
        int matchedTokens = 0;

        for (String queryToken : queryTokens) {
            double bestTokenScore = candidateTokens.stream()
                    .mapToDouble(candidateToken -> tokenScore(queryToken, candidateToken))
                    .max()
                    .orElse(0);
            if (bestTokenScore >= MIN_TOKEN_SCORE) {
                matchedScore += bestTokenScore;
                matchedTokens++;
            }
        }

        if (matchedTokens == 0) {
            return 0;
        }
        double coverage = matchedTokens / (double) queryTokens.size();
        return (matchedScore / matchedTokens) * (0.65 + 0.35 * coverage);
    }

    private static double tokenScore(String queryToken, String candidateToken) {
        if (queryToken.equals(candidateToken)) {
            return 1;
        }
        if (queryToken.contains(candidateToken) || candidateToken.contains(queryToken)) {
            return Math.min(queryToken.length(), candidateToken.length())
                    / (double) Math.max(queryToken.length(), candidateToken.length());
        }
        if (Math.min(queryToken.length(), candidateToken.length()) < 3) {
            return 0;
        }
        int distance = levenshteinDistance(queryToken, candidateToken);
        return 1 - distance / (double) Math.max(queryToken.length(), candidateToken.length());
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitutionCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost);
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
