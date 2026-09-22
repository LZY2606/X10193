package court.symbol.elf;

class ByteReader {
    private final byte[] data;
    private final boolean littleEndian;

    ByteReader(byte[] data, boolean littleEndian) {
        this.data = data;
        this.littleEndian = littleEndian;
    }

    int u8(long offset) {
        require(offset, 1);
        return data[(int) offset] & 0xff;
    }

    int u16(long offset) {
        require(offset, 2);
        int a = data[(int) offset] & 0xff;
        int b = data[(int) offset + 1] & 0xff;
        return littleEndian ? a | b << 8 : a << 8 | b;
    }

    long u32(long offset) {
        require(offset, 4);
        long result = 0;
        if (littleEndian) {
            for (int i = 3; i >= 0; i--) {
                result = result << 8 | data[(int) offset + i] & 0xffL;
            }
        } else {
            for (int i = 0; i < 4; i++) {
                result = result << 8 | data[(int) offset + i] & 0xffL;
            }
        }
        return result;
    }

    long u64(long offset) {
        require(offset, 8);
        long result = 0;
        if (littleEndian) {
            for (int i = 7; i >= 0; i--) {
                result = result << 8 | data[(int) offset + i] & 0xffL;
            }
        } else {
            for (int i = 0; i < 8; i++) {
                result = result << 8 | data[(int) offset + i] & 0xffL;
            }
        }
        return result;
    }

    String cString(long offset, long maxLength) {
        if (offset < 0 || offset > data.length || maxLength < 0 || offset > data.length - maxLength) {
            throw new ElfReadException("字符串表越界");
        }
        int end = (int) offset;
        int limit = (int) Math.min((long) data.length, offset + maxLength);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, (int) offset, end - (int) offset);
    }

    byte[] slice(long offset, long length) {
        require(offset, length);
        byte[] result = new byte[(int) length];
        System.arraycopy(data, (int) offset, result, 0, result.length);
        return result;
    }

    private void require(long offset, long length) {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new ElfReadException("二进制数据越界 offset=" + offset + " length=" + length);
        }
    }
}
