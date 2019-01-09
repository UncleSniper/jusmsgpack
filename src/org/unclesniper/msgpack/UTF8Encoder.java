package org.unclesniper.msgpack;

public class UTF8Encoder {

	public interface ErrorHandler {

		int illegalHighSurrogate(int surrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalHighSurrogate recover) throws StringEncodingException;

		int illegalLowSurrogate(int highSurrogate, int lowSurrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalLowSurrogate recover) throws StringEncodingException;

	}

	public static class ThrowingErrorHandler implements ErrorHandler {

		public static final ThrowingErrorHandler instance = new ThrowingErrorHandler();

		public ThrowingErrorHandler() {}

		public int illegalHighSurrogate(int surrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalHighSurrogate recover) throws StringEncodingException {
			throw new UnrepresentableCharacterException(surrogate);
		}

		public int illegalLowSurrogate(int highSurrogate, int lowSurrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalLowSurrogate recover) throws StringEncodingException {
			throw new UnrepresentableCharacterException(lowSurrogate);
		}

	}

	public static class ReplacingErrorHandler implements ErrorHandler {

		public static final char UNICODE_REPLACEMENT_CHARACTER = '\uFFFD';

		private char replacement;

		private SkipLowSurrogate skipLowSurrogate;

		public ReplacingErrorHandler() {
			replacement = ReplacingErrorHandler.UNICODE_REPLACEMENT_CHARACTER;
		}

		public ReplacingErrorHandler(char replacement) {
			this.replacement = replacement;
		}

		public ReplacingErrorHandler(SkipLowSurrogate skipLowSurrogate) {
			replacement = ReplacingErrorHandler.UNICODE_REPLACEMENT_CHARACTER;
			this.skipLowSurrogate = skipLowSurrogate;
		}

		public ReplacingErrorHandler(char replacement, SkipLowSurrogate skipLowSurrogate) {
			this.replacement = replacement;
			this.skipLowSurrogate = skipLowSurrogate;
		}

		public char getReplacement() {
			return replacement;
		}

		public void setReplacement(char replacement) {
			this.replacement = replacement;
		}

		public SkipLowSurrogate getSkipLowSurrogate() {
			return skipLowSurrogate;
		}

		public void setSkipLowSurrogate(SkipLowSurrogate skipLowSurrogate) {
			this.skipLowSurrogate = skipLowSurrogate;
		}

		public int illegalHighSurrogate(int surrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalHighSurrogate recover) {
			recover.skipLowSurrogate(skipLowSurrogate);
			return (int)replacement;
		}

		public int illegalLowSurrogate(int highSurrogate, int lowSurrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalLowSurrogate recover) {
			return (int)replacement;
		}

	}

	public static class SquashingErrorHandler implements ErrorHandler {

		private SkipLowSurrogate skipLowSurrogate;

		public SquashingErrorHandler() {}

		public SquashingErrorHandler(SkipLowSurrogate skipLowSurrogate) {
			this.skipLowSurrogate = skipLowSurrogate;
		}

		public SkipLowSurrogate getSkipLowSurrogate() {
			return skipLowSurrogate;
		}

		public void setSkipLowSurrogate(SkipLowSurrogate skipLowSurrogate) {
			this.skipLowSurrogate = skipLowSurrogate;
		}

		public int illegalHighSurrogate(int surrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalHighSurrogate recover) {
			recover.skipLowSurrogate(skipLowSurrogate);
			return -1;
		}

		public int illegalLowSurrogate(int highSurrogate, int lowSurrogate, byte[] output, int outoff, int outsize,
				RecoverIllegalLowSurrogate recover) {
			return -1;
		}

	}

	public interface Recover {

		void wroteOutputBytes(int count);

		void slateReplacementOutput(ReplacementOutput replacement);

	}

	public interface RecoverIllegalHighSurrogate extends Recover {

		void skipLowSurrogate(SkipLowSurrogate when);

	}

	public interface RecoverIllegalLowSurrogate extends Recover {}

	private class RecoverImpl implements RecoverIllegalHighSurrogate, RecoverIllegalLowSurrogate {

		boolean didWriteOutputBytes;

		int outsize;

		RecoverImpl() {}

		public void wroteOutputBytes(int count) {
			if(count <= 0)
				return;
			if(count > outsize - outcount)
				throw new IllegalArgumentException("Supposedly wrote " + count + " bytes, but only "
						+ (outcount - outsize) + " bytes of space are left");
			outcount += count;
			didWriteOutputBytes = true;
		}

		public void slateReplacementOutput(ReplacementOutput replacement) {
			UTF8Encoder.this.replacement = replacement;
		}

		public void skipLowSurrogate(SkipLowSurrogate when) {
			UTF8Encoder.this.skipLowSurrogate = when;
		}

	}

	public interface ReplacementOutput {

		int drain(byte[] output, int outoff, int outsize);

		ReplacementOutput copy();

	}

	public enum SkipLowSurrogate {
		BLINDLY,
		IF_SURROGATE,
		IF_LOW_SURROGATE
	}

	public static final ErrorHandler DEFAULT_ERROR_HANDLER = ThrowingErrorHandler.instance;

	private int pending;

	private int partial;

	private int outcount;

	private int highSurrogate;

	private ErrorHandler errorHandler;

	private ReplacementOutput replacement;

	private RecoverImpl recover;

	private SkipLowSurrogate skipLowSurrogate;

	public UTF8Encoder() {}

	public UTF8Encoder(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void reset() {
		pending = highSurrogate = 0;
		replacement = null;
		skipLowSurrogate = null;
	}

	public boolean isClean() {
		return replacement == null && pending == 0;
	}

	public void copyStateInto(UTF8Encoder other) {
		other.pending = pending;
		other.partial = partial;
		other.highSurrogate = highSurrogate;
		other.errorHandler = errorHandler;
		other.replacement = replacement == null ? null : replacement.copy();
		other.skipLowSurrogate = skipLowSurrogate;
	}

	public int encode(char[] input, int inoff, int insize, byte[] output, int outoff, int outsize)
			throws StringEncodingException {
		outcount = 0;
		int consumed = 0;
		while((replacement != null || pending > 0 || consumed < insize) && outcount < outsize) {
			if(replacement != null) {
				int count = replacement.drain(output, outoff + outcount, outsize - outcount);
				if(count <= 0)
					replacement = null;
				else if(count > outsize - outcount)
					throw new IllegalStateException("Replacement output reports to have written " + count
							+ " bytes, but there were only " + (outsize - outcount) + " bytes of space left");
				else
					outcount += count;
			}
			else if(pending > 0) {
				if(output != null)
					output[outoff + outcount] = (byte)((partial & 0x3F) | 0x80);
				++outcount;
				partial >>>= 6;
				--pending;
			}
			else {
				int c = (int)input[inoff + consumed++];
				if(skipLowSurrogate != null) {
					switch(skipLowSurrogate) {
						case BLINDLY:
							c = -1;
							break;
						case IF_SURROGATE:
							if((c & 0xF800) == 0xD800)
								c = -1;
							break;
						case IF_LOW_SURROGATE:
							if((c & 0xFC00) == 0xDC00)
								c = -1;
							break;
						default:
							throw new Doom("Unrecognized SkipLowSurrogate: " + skipLowSurrogate.name());
					}
					skipLowSurrogate = null;
					if(c < 0)
						continue;
				}
				/* Straight:
				 *   0x0000           - 0xD7FF
				 *   0000000000000000   1101011111111111
				 *   0xE000           - 0xFFFF
				 *   1110000000000000   1111111111111111
				 * High:
				 *   0xD800           - 0xDBFF
				 *   1101100000000000   1101101111111111
				 * Low:
				 *   0xDC00           - 0xDFFF
				 *   1101110000000000   1101111111111111
				 */
				if(highSurrogate == 0) {
					while((c & 0xF800) == 0xD800) {
						if((c & 0x0400) == 0) {
							if(skipLowSurrogate == null)
								highSurrogate = c;
							c = -1;
							break;
						}
						if(recover == null)
							recover = new RecoverImpl();
						else
							recover.didWriteOutputBytes = false;
						recover.outsize = outsize;
						c = (errorHandler == null ? UTF8Encoder.DEFAULT_ERROR_HANDLER : errorHandler)
								.illegalHighSurrogate(c, output, outoff + outcount, outsize - outcount, recover);
						if(recover.didWriteOutputBytes || replacement != null)
							c = -1;
						if(c < 0)
							break;
						if(c > 0x0010FFFF)
							throw new UnrepresentableCharacterException(c);
					}
				}
				else {
					while((c & 0xF800) == 0xD800) {
						if((c & 0x0400) != 0) {
							c = (((highSurrogate & 0x03FF) << 10) | (c & 0x03FF)) + 0x10000;
							break;
						}
						if(recover == null)
							recover = new RecoverImpl();
						else
							recover.didWriteOutputBytes = false;
						recover.outsize = outsize;
						c = (errorHandler == null ? UTF8Encoder.DEFAULT_ERROR_HANDLER : errorHandler)
								.illegalLowSurrogate(highSurrogate, c,
										output, outoff + outcount, outsize - outcount, recover);
						if(recover.didWriteOutputBytes || replacement != null)
							c = -1;
						if(c < 0)
							break;
						if(c > 0x0010FFFF)
							throw new UnrepresentableCharacterException(c);
					}
					highSurrogate = 0;
				}
				if(c < 0)
					continue;
				if(c < 0x80) {
					if(output != null)
						output[outoff + outcount] = (byte)c;
					++outcount;
				}
				else if(c < 0x800) {
					partial = c & 0x3F;
					pending = 1;
					if(output != null)
						output[outoff + outcount] = (byte)((c >> 6) | 0xC0);
					++outcount;
				}
				else if(c < 0x00010000) {
					partial = c & 0x0FFF;
					pending = 2;
					if(output != null)
						output[outoff + outcount] = (byte)((c >> 12) | 0xE0);
					++outcount;
				}
				else {
					partial = c & 0x0003FFFF;
					pending = 3;
					if(output != null)
						output[outoff + outcount] = (byte)((c >> 18) | 0xFC);
					++outcount;
				}
			}
		}
		return consumed;
	}

	public int getOutCount() {
		return outcount;
	}

}
