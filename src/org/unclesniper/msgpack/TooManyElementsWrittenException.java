package org.unclesniper.msgpack;

import java.io.IOException;

public class TooManyElementsWrittenException extends IOException {

	private final int requestedCount;

	private final int writtenCount;

	public TooManyElementsWrittenException(int requestedCount, int writtenCount) {
		super("Output object reported to have written " + writtenCount +
				" elements, but only " + requestedCount + " were requested");
		this.requestedCount = requestedCount;
		this.writtenCount = writtenCount;
	}

	public int getRequestedCount() {
		return requestedCount;
	}

	public int getWrittenCount() {
		return writtenCount;
	}

}
