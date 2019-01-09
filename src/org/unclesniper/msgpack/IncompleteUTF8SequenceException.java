package org.unclesniper.msgpack;

public class IncompleteUTF8SequenceException extends StringEncodingException {

	private final int missingByteCount;

	private final int sequenceLength;

	public IncompleteUTF8SequenceException(int missingByteCount, int sequenceLength) {
		super("Incomplete UTF-8 byte sequence: Missing " + missingByteCount
				+ " to complete " + sequenceLength + " byte sequence");
		this.missingByteCount = missingByteCount;
		this.sequenceLength = sequenceLength;
	}

	public int getMissingByteCount() {
		return missingByteCount;
	}

	public int getSequenceLength() {
		return sequenceLength;
	}

}
