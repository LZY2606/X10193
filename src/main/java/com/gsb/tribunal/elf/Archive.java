package com.gsb.tribunal.elf;

import java.util.List;

public record Archive(String name, List<Member> members) {
    public record Member(String name, byte[] data) {}
}
