package com.symbolcourt.resolve;

public class CandidateEvaluation {
    public Candidate candidate;
    public boolean accepted;
    public String eliminationReason;
    public int rank;

    public CandidateEvaluation(Candidate candidate) {
        this.candidate = candidate;
        this.accepted = true;
    }

    public static CandidateEvaluation rejected(Candidate c, String reason) {
        CandidateEvaluation e = new CandidateEvaluation(c);
        e.accepted = false;
        e.eliminationReason = reason;
        return e;
    }
}
