package court.elf;

/**
 * 损坏或不受支持的 ELF/ar 数据。解析器遇到任何越界、非法长度或魔数不匹配时抛出，
 * 由上层隔离单个对象，不影响同批其它对象。
 */
public class ElfException extends RuntimeException {
    public ElfException(String message) {
        super(message);
    }

    public ElfException(String message, Throwable cause) {
        super(message, cause);
    }
}
