package com.symbolcourt.ar;

/** archive 结构损坏；隔离该 archive 自身。 */
public class ArException extends Exception {
    public ArException(String message) {
        super(message);
    }
}
