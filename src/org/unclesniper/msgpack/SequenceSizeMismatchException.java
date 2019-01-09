package org.unclesniper.msgpack;

import java.io.IOException;

public class SequenceSizeMismatchException extends IOException {

	private final long announcedSize;

	private final long providedSize;

	private final boolean orMore;

	public SequenceSizeMismatchException(long announcedSize, long providedSize, boolean orMore) {
		super("Sequence size mismatch: " + announcedSize + " elements were announced, but "
				+ (orMore ? ">= " : "") + providedSize + " where provided");
		this.announcedSize = announcedSize;
		this.providedSize = providedSize;
		this.orMore = orMore;
	}

	public long getAnnouncedSize() {
		return announcedSize;
	}

	public long getProvidedSize() {
		return providedSize;
	}

	public boolean isOrMore() {
		return orMore;
	}

}
