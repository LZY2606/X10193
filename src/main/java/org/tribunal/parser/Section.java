package org.tribunal.parser;

record Section(int index, long nameOffset, int type, long flags, long address, long offset, long size,
               int link, int info, long alignment, long entrySize) {
}
