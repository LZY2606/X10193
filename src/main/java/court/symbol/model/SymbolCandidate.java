package court.symbol.model;

import court.symbol.elf.Elf;

/**
 * One concrete provider of a name: a symbol in one loaded object or extracted
 * archive member. Same name in two files is two candidates.
 */
public record SymbolCandidate(String providerId,
                              String providerLabel,
                              int loadOrder,
                              boolean archiveMember,
                              String memberName,
                              Elf.Symbol symbol) {

    public CandidateKey key() {
        return new CandidateKey(providerId, symbol.index());
    }

    public String name() {
        return symbol.name();
    }
}
