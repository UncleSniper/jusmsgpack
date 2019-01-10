package org.unclesniper.msgpack;

public class UTF8Decoder {

	public interface ErrorHandler {

		boolean illegalInitiatorByte(byte initiator, char[] output, int outoff, int outsize,
				RecoverIllegalInitiatorByte recover) throws StringEncodingException;

		boolean illegalContinuationByte(int accumulator, int processedCount, int sequenceLength,
				byte continuation, char[] output, int outoff, int outsize,
				RecoverIllegalContinuationByte recover) throws StringEncodingException;

		int unrepresentableCharacter(int character, char[] output, int outoff, int outsize,
				RecoverUnrepresentableCharacter recover) throws StringEncodingException;

		void incompleteSequence(int accumulator, int processedCount, int sequenceLength,
				char[] output, int outoff, int outsize, RecoverIncompleteSequence recover)
				throws StringEncodingException;

	}

	public static class ThrowingErrorHandler implements ErrorHandler {

		public static final ThrowingErrorHandler instance = new ThrowingErrorHandler();

		public ThrowingErrorHandler() {}

		public boolean illegalInitiatorByte(byte initiator, char[] output, int outoff, int outsize,
				RecoverIllegalInitiatorByte recover) throws StringEncodingException {
			throw new IllegalUTF8SequenceByteException(initiator, true);
		}

		public boolean illegalContinuationByte(int accumulator, int processedCount, int sequenceLength,
				byte continuation, char[] output, int outoff, int outsize,
				RecoverIllegalContinuationByte recover) throws StringEncodingException {
			throw new IllegalUTF8SequenceByteException(continuation, false);
		}

		public int unrepresentableCharacter(int character, char[] output, int outoff, int outsize,
				RecoverUnrepresentableCharacter recover) throws StringEncodingException {
			throw new UnrepresentableCharacterException(character);
		}

		public void incompleteSequence(int accumulator, int processedCount, int sequenceLength,
				char[] output, int outoff, int outsize, RecoverIncompleteSequence recover)
				throws StringEncodingException {
			throw new IncompleteUTF8SequenceException(sequenceLength - processedCount, sequenceLength);
		}

	}

	public static class ReplacingErrorHandler implements ErrorHandler {

		public static final char UNICODE_REPLACEMENT_CHARACTER = '\uFFFD';

		private char replacement;

		private boolean skipContinuationBytes;

		public ReplacingErrorHandler() {
			replacement = ReplacingErrorHandler.UNICODE_REPLACEMENT_CHARACTER;
		}

		public ReplacingErrorHandler(char replacement) {
			this.replacement = replacement;
		}

		public ReplacingErrorHandler(boolean skipContinuationBytes) {
			replacement = ReplacingErrorHandler.UNICODE_REPLACEMENT_CHARACTER;
			this.skipContinuationBytes = skipContinuationBytes;
		}

		public ReplacingErrorHandler(char replacement, boolean skipContinuationBytes) {
			this.replacement = replacement;
			this.skipContinuationBytes = skipContinuationBytes;
		}

		public char getReplacement() {
			return replacement;
		}

		public void setReplacement(char replacement) {
			this.replacement = replacement;
		}

		public boolean isSkipContinuationBytes() {
			return skipContinuationBytes;
		}

		public void setSkipContinuationBytes(boolean skipContinuationBytes) {
			this.skipContinuationBytes = skipContinuationBytes;
		}

		public boolean illegalInitiatorByte(byte initiator, char[] output, int outoff, int outsize,
				RecoverIllegalInitiatorByte recover) {
			return skipContinuationBytes;
		}

		public boolean illegalContinuationByte(int accumulator, int processedCount, int sequenceLength,
				byte continuation, char[] output, int outoff, int outsize,
				RecoverIllegalContinuationByte recover) {
			return skipContinuationBytes;
		}

		public int unrepresentableCharacter(int character, char[] output, int outoff, int outsize,
				RecoverUnrepresentableCharacter recover) {
			return (int)replacement;
		}

		public void incompleteSequence(int accumulator, int processedCount, int sequenceLength,
				char[] output, int outoff, int outsize, RecoverIncompleteSequence recover) {}

	}

	public static class SquashingErrorHandler implements ErrorHandler {

		private boolean skipContinuationBytes;

		public SquashingErrorHandler() {}

		public SquashingErrorHandler(boolean skipContinuationBytes) {
			this.skipContinuationBytes = skipContinuationBytes;
		}

		public boolean isSkipContinuationBytes() {
			return skipContinuationBytes;
		}

		public void setSkipContinuationBytes(boolean skipContinuationBytes) {
			this.skipContinuationBytes = skipContinuationBytes;
		}

		public boolean illegalInitiatorByte(byte initiator, char[] output, int outoff, int outsize,
				RecoverIllegalInitiatorByte recover) {
			return skipContinuationBytes;
		}

		public boolean illegalContinuationByte(int accumulator, int processedCount, int sequenceLength,
				byte continuation, char[] output, int outoff, int outsize,
				RecoverIllegalContinuationByte recover) {
			return skipContinuationBytes;
		}

		public int unrepresentableCharacter(int character, char[] output, int outoff, int outsize,
				RecoverUnrepresentableCharacter recover) {
			return -1;
		}

		public void incompleteSequence(int accumulator, int processedCount, int sequenceLength,
				char[] output, int outoff, int outsize, RecoverIncompleteSequence recover) {}

	}

	public interface Recover {

		void wroteOutputCharacters(int count);

		void slateReplacementOutput(ReplacementOutput replacement);

	}

	public interface RecoverIllegalInitiatorByte extends Recover {}

	public interface RecoverIllegalContinuationByte extends Recover {}

	public interface RecoverUnrepresentableCharacter extends Recover {}

	public interface RecoverIncompleteSequence extends Recover {}

	private class RecoverImpl implements RecoverIllegalInitiatorByte, RecoverIllegalContinuationByte,
			RecoverUnrepresentableCharacter, RecoverIncompleteSequence {

		int outsize;

		RecoverImpl() {}

		public void wroteOutputCharacters(int count) {
			if(count <= 0)
				return;
			if(count > outsize - outcount)
				throw new IllegalArgumentException("Supposedly wrote " + count + " characters, but only "
						+ (outcount - outsize) + " elements of space are left");
			outcount += count;
		}

		public void slateReplacementOutput(ReplacementOutput replacement) {
			UTF8Decoder.this.replacement = replacement;
		}

	}

	public interface ReplacementOutput {

		int drain(char[] output, int outoff, int outsize);

		ReplacementOutput copy();

	}

	public static final ErrorHandler DEFAULT_ERROR_HANDLER = ThrowingErrorHandler.instance;

	private int pending;

	private int partial;

	private int partialSize;

	private int outcount;

	private ErrorHandler errorHandler;

	private ReplacementOutput replacement;

	private RecoverImpl recover;

	private boolean skipContinuationBytes;

	private int lowSurrogate;

	public UTF8Decoder() {}

	public UTF8Decoder(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void reset() {
		pending = 0;
		replacement = null;
		lowSurrogate = 0;
		skipContinuationBytes = false;
	}

	public boolean isClean() {
		return lowSurrogate == 0 && replacement == null;
	}

	public boolean isDrained() {
		return pending == 0 && lowSurrogate == 0 && replacement == null;
	}

	public void copyStateInto(UTF8Decoder other) {
		other.pending = pending;
		other.partial = partial;
		other.partialSize = partialSize;
		other.errorHandler = errorHandler;
		other.replacement = replacement == null ? null : replacement.copy();
		other.skipContinuationBytes = skipContinuationBytes;
		other.lowSurrogate = lowSurrogate;
	}

	public int decode(byte[] input, int inoff, int insize, char[] output, int outoff, int outsize)
			throws StringEncodingException {
		outcount = 0;
		int consumed = 0;
		/* Input:
		 *   - input
		 *   - (partial/pending)
		 * Output:
		 *   - lowSurrogate
		 *   - replacement
		 *   - transformed input
		 */
		while((lowSurrogate != 0 || replacement != null || consumed < insize)
				&& (output == null || outcount < outsize)) {
			if(lowSurrogate != 0) {
				if(output != null)
					output[outoff + outcount] = (char)lowSurrogate;
				++outcount;
				lowSurrogate = 0;
				continue;
			}
			if(replacement != null) {
				int count = replacement.drain(output, outoff + outcount, outsize - outcount);
				if(count <= 0)
					replacement = null;
				else if(count > outsize - outcount)
					throw new IllegalStateException("Replacement output reports to have written " + count
							+ " characters, but there were only " + (outsize - outcount)
							+ " elements of space left");
				else
					outcount += count;
				continue;
			}
			int c = input[inoff + consumed++] & 0xFF;
			if(skipContinuationBytes) {
				if((c & 0xC0) == 0x80)
					continue;
				skipContinuationBytes = false;
			}
			if(pending == 0) {
				if((c & 0x80) == 0) {
					if(output != null)
						output[outoff + outcount] = (char)(c & 0x7F);
					++outcount;
				}
				else if((c & 0xE0) == 0xC0) {
					partial = c & 0x1F;
					partialSize = 1;
					pending = 1;
				}
				else if((c & 0xF0) == 0xE0) {
					partial = c & 0x0F;
					partialSize = 1;
					pending = 2;
				}
				else if((c & 0xF8) == 0xF0) {
					partial = c & 0x07;
					partialSize = 1;
					pending = 3;
				}
				else {
					if(recover == null)
						recover = new RecoverImpl();
					recover.outsize = outsize;
					skipContinuationBytes = (errorHandler == null ? UTF8Decoder.DEFAULT_ERROR_HANDLER : errorHandler)
							.illegalInitiatorByte((byte)c, output, outoff + outcount, outsize - outcount, recover);
				}
			}
			else if((c & 0xC0) != 0x80) {
				int sequenceLength = partialSize + pending;
				pending = 0;
				if(recover == null)
					recover = new RecoverImpl();
				recover.outsize = outsize;
				skipContinuationBytes = (errorHandler == null ? UTF8Decoder.DEFAULT_ERROR_HANDLER : errorHandler)
						.illegalContinuationByte(partial, partialSize, sequenceLength, (byte)c,
								output, outoff + outcount, outsize - outcount, recover);
			}
			else {
				partial = (partial << 6) | (c & 0x3F);
				++partialSize;
				if(--pending == 0) {
					while(partial > 0x0010FFFF)
						partial = (errorHandler == null ? UTF8Decoder.DEFAULT_ERROR_HANDLER : errorHandler)
								.unrepresentableCharacter(partial,
										output, outoff + outcount, outsize - outcount, recover);
					if(partial < 0)
						continue;
					if(partial > 0xFFFF) {
						partial -= 0x10000;
						if(output != null)
							output[outoff + outcount] = (char)((partial >> 10) | 0xD800);
						++outcount;
						lowSurrogate = (partial & 0x03FF) | 0xDC00;
					}
					else {
						if(output != null)
							output[outoff + outcount] = (char)partial;
						++outcount;
					}
				}
			}
		}
		return consumed;
	}

	public int getOutCount() {
		return outcount;
	}

	public int drain(char[] output, int outoff, int outsize) throws StringEncodingException {
		outcount = 0;
		if(output != null && outsize <= 0)
			return 0;
		while((pending > 0 || lowSurrogate != 0 || replacement != null) && (output == null || outcount < outsize)) {
			if(pending > 0) {
				int sequenceLength = partialSize + pending;
				pending = 0;
				if(recover == null)
					recover = new RecoverImpl();
				recover.outsize = outsize;
				(errorHandler == null ? UTF8Decoder.DEFAULT_ERROR_HANDLER : errorHandler)
						.incompleteSequence(partial, partialSize, sequenceLength,
								output, outoff + outcount, outsize - outcount, recover);
			}
			else if(lowSurrogate != 0) {
				if(output != null)
					output[outoff + outcount] = (char)lowSurrogate;
				++outcount;
				lowSurrogate = 0;
			}
			else {
				int count = replacement.drain(output, outoff + outcount, outsize - outcount);
				if(count <= 0)
					replacement = null;
				else if(count > outsize - outcount)
					throw new IllegalStateException("Replacement output reports to have written " + count
							+ " characters, but there were only " + (outsize - outcount)
							+ " elements of space left");
				else
					outcount += count;
			}
		}
		return outcount;
	}

}
