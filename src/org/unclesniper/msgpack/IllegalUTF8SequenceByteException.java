package org.unclesniper.msgpack;

public class IllegalUTF8SequenceByteException extends StringEncodingException {

	private final byte offendingByte;

	private final boolean initiator;

	public IllegalUTF8SequenceByteException(byte offendingByte, boolean initiator) {
		super("Illegal UTF-8 " + (initiator ? "initiator" : "continuation") + " byte: 0x"
				+ Integer.toHexString(offendingByte & 0xFF).toUpperCase());
		this.offendingByte = offendingByte;
		this.initiator = initiator;
	}

	public byte getOffendingByte() {
		return offendingByte;
	}

	public boolean isInitiator() {
		return initiator;
	}

}
