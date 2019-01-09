package org.unclesniper.msgpack;

import java.io.IOException;

public class SynchronicityViolationException extends IOException {

	private final int requiredCount;

	private final int processedCount;

	public SynchronicityViolationException(int requiredCount, int processedCount) {
		super("Synchronicity promise was broken: Supposedly blocking output only processed "
				+ processedCount + " out of " + requiredCount + " elements");
		this.requiredCount = requiredCount;
		this.processedCount = processedCount;
	}

	public int getRequiredCount() {
		return requiredCount;
	}

	public int getProcessedCount() {
		return processedCount;
	}

}
