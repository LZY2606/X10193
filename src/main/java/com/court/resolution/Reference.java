package com.court.resolution;

import com.court.model.ElfFile;
import com.court.model.ElfSymbol;
import com.court.model.Relocation;

/** An undefined reference that must be adjudicated. */
public class Reference {
    public final ElfFile sourceFile;
    public final ElfSymbol referencedSymbol;
    public final String requiredVersion;
    public final boolean nonDefaultVersion;
    public final Relocation relocation;
    public final boolean weakReference;

    public Reference(ElfFile sourceFile, ElfSymbol referencedSymbol,
                     String requiredVersion, boolean nonDefaultVersion,
                     Relocation relocation, boolean weakReference) {
        this.sourceFile = sourceFile;
        this.referencedSymbol = referencedSymbol;
        this.requiredVersion = requiredVersion;
        this.nonDefaultVersion = nonDefaultVersion;
        this.relocation = relocation;
        this.weakReference = weakReference;
    }

    public String name() { return referencedSymbol.getName(); }
}
