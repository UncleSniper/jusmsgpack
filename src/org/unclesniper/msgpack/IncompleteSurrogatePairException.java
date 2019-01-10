package org.unclesniper.msgpack;

public class IncompleteSurrogatePairException extends StringEncodingException {

	private final int highSurrogate;

	public IncompleteSurrogatePairException(int highSurrogate) {
		super("Incomplete UTF-16 surrogate pair started by high surrogate 0x" + Integer.toHexString(highSurrogate));
		this.highSurrogate = highSurrogate;
	}

	public int getHighSurrogate() {
		return highSurrogate;
	}

}
