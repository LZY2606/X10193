package court.symbol.engine;

import court.symbol.elf.Elf;
import court.symbol.elf.ElfFile;
import court.symbol.model.InputObject;

/**
 * A unit that can provide or consume symbols: either an explicitly loaded
 * object/shared object, or a member lazily extracted from an archive.
 */
record LoadedProvider(String id,
                      String label,
                      int loadOrder,
                      boolean archiveMember,
                      String archiveId,
                      String archiveLabel,
                      InputObject source,
                      ElfFile elf,
                      boolean extracted,
                      boolean comdatEliminated,
                      java.util.Set<Integer> eliminatedSections,
                      java.util.Set<String> versionAliases) {

    LoadedProvider extracted(boolean value) {
        return new LoadedProvider(id, label, loadOrder, archiveMember, archiveId,
                archiveLabel, source, elf, value, comdatEliminated, eliminatedSections,
                versionAliases);
    }

    LoadedProvider withEliminatedSections(java.util.Set<Integer> sections) {
        boolean any = !sections.isEmpty();
        return new LoadedProvider(id, label, loadOrder, archiveMember, archiveId,
                archiveLabel, source, elf, extracted, any, sections, versionAliases);
    }

    boolean symbolAlive(Elf.Symbol sym) {
        if (comdatEliminated && eliminatedSections.contains(sym.shndx())) {
            return false;
        }
        return true;
    }
}
