package socs.network.util;

public class MismatchedLinkException extends Exception { 
    public MismatchedLinkException(String errorMessage) {
        super(errorMessage);
    }
}