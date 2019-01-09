package org.unclesniper.msgpack;

import java.io.IOException;

public class StringEncoderMsgPackSink implements MsgPackCharSink {

	public static final int DEFAULT_BUFFER_SIZE = 512;

	private MsgPackByteSink slave;

	private byte[] byteBuffer;

	private int bufferOffset;

	private int bufferFill;

	private final UTF8Encoder encoder = new UTF8Encoder();

	private final UTF8Encoder lengthEncoder = new UTF8Encoder();

	private long announcedSize = -1l;

	private long receivedSize;

	private int skipChars;

	public StringEncoderMsgPackSink(MsgPackByteSink slave, int bufferSize) {
		this.slave = slave;
		byteBuffer = new byte[bufferSize > 0 ? bufferSize : StringEncoderMsgPackSink.DEFAULT_BUFFER_SIZE];
	}

	public MsgPackByteSink getSlave() {
		return slave;
	}

	public void setSlave(MsgPackByteSink sink) {
		this.slave = slave;
	}

	public int getBufferSize() {
		return byteBuffer.length;
	}

	public UTF8Encoder getEncoder() {
		return encoder;
	}

	@Override
	public boolean isBlockingSink() {
		return slave.isBlockingSink();
	}

	private void requireNoString() {
		if(announcedSize >= 0l)
			throw new IllegalStateException("Out-of-sequence event received: Still within string structure");
	}

	private void requireString() {
		if(announcedSize < 0l)
			throw new IllegalStateException("Out-of-sequence event received: Not within string structure");
	}

	@Override
	public void nil() throws IOException {
		requireNoString();
		slave.nil();
	}

	@Override
	public void bool(boolean value) throws IOException {
		requireNoString();
		slave.bool(value);
	}

	@Override
	public void integer(long value, boolean signed) throws IOException {
		requireNoString();
		slave.integer(value, signed);
	}

	@Override
	public void fraction(double value) throws IOException {
		requireNoString();
		slave.fraction(value);
	}

	@Override
	public void emptyString() throws IOException {
		requireNoString();
		slave.emptyString();
	}

	private int fillBuffer(char[] chars, int offset, int count) throws StringEncodingException {
		int consumed = 0;
		while(consumed < count && bufferFill < byteBuffer.length) {
			consumed += encoder.encode(chars, offset + consumed, count - consumed,
					byteBuffer, bufferFill, byteBuffer.length - bufferFill);
			bufferFill += encoder.getOutCount();
		}
		while(bufferFill < byteBuffer.length && !encoder.isClean()) {
			encoder.encode(null, 0, 0, byteBuffer, bufferFill, byteBuffer.length - bufferFill);
			bufferFill += encoder.getOutCount();
		}
		return consumed;
	}

	private static long strlen(UTF8Encoder encoder, char[] chars, int offset, int count)
			throws StringEncodingException {
		int consumed = 0;
		long length = 0l;
		while(consumed < count) {
			consumed += encoder.encode(chars, offset + consumed, count - consumed, null, 0, 0);
			length += (long)encoder.getOutCount();
		}
		return length;
	}

	@Override
	public int string(char[] chars, int offset, int count) throws IOException {
		requireNoString();
		if(slave.isBlockingSink())
			return stringBlocking(chars, offset, count);
		else
			return stringNonBlocking(chars, offset, count);
	}

	private int stringBlocking(char[] chars, int offset, int count) throws IOException {
		bufferFill = 0;
		encoder.reset();
		int consumed = fillBuffer(chars, offset, count);
		bufferOffset = bufferFill;
		if(consumed == count && encoder.isClean()) {
			int written = slave.string(byteBuffer, 0, bufferFill);
			if(written > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, written);
			if(written < bufferFill)
				throw new SynchronicityViolationException(bufferFill, written);
			return count;
		}
		encoder.copyStateInto(lengthEncoder);
		long tsize = (long)bufferFill + StringEncoderMsgPackSink.strlen(lengthEncoder,
				chars, offset + consumed, count - consumed);
		if(tsize > 0xFFFFFFFFl)
			throw new IllegalArgumentException("String byte length exceeds uint32 address space: " + tsize);
		receivedSize = (long)bufferFill;
		if(receivedSize > tsize)
			throw new SequenceSizeMismatchException(tsize, receivedSize, true);
		int written = slave.beginString((int)tsize, byteBuffer, 0, bufferFill);
		if(written > bufferFill)
			throw new TooManyElementsWrittenException(bufferFill, written);
		if(written < bufferFill)
			throw new SynchronicityViolationException(bufferFill, written);
		while(consumed < count || !encoder.isClean()) {
			bufferFill = 0;
			consumed += fillBuffer(chars, offset + consumed, count - consumed);
			bufferOffset = bufferFill;
			if(bufferFill == 0)
				continue;
			receivedSize += (long)bufferFill;
			if(receivedSize > tsize)
				throw new SequenceSizeMismatchException(tsize, receivedSize, true);
			written = slave.continueString(byteBuffer, 0, bufferFill);
			if(written > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, written);
			if(written < bufferFill)
				throw new SynchronicityViolationException(bufferFill, written);
		}
		if(receivedSize < tsize)
			throw new SequenceSizeMismatchException(tsize, receivedSize, false);
		slave.endString();
		return count;
	}

	private int stringNonBlocking(char[] chars, int offset, int count) throws IOException {
		bufferFill = 0;
		encoder.reset();
		int consumed = fillBuffer(chars, offset, count);
		if(consumed == count && encoder.isClean()) {
			bufferOffset = slave.string(byteBuffer, 0, bufferFill);
			if(bufferOffset > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, bufferOffset);
			if(bufferOffset == bufferFill)
				return count;
			announcedSize = receivedSize = (long)bufferFill;
			skipChars = 1;
			return count - 1;
		}
		encoder.copyStateInto(lengthEncoder);
		long tsize = (long)bufferFill + StringEncoderMsgPackSink.strlen(lengthEncoder,
				chars, offset + consumed, count - consumed);
		if(tsize > 0xFFFFFFFFl)
			throw new IllegalArgumentException("String byte length exceeds uint32 address space: " + tsize);
		receivedSize = (long)bufferFill;
		if(receivedSize > tsize)
			throw new SequenceSizeMismatchException(tsize, receivedSize, false);
		bufferOffset = slave.beginString((int)tsize, byteBuffer, 0, bufferFill);
		if(bufferOffset > bufferFill)
			throw new TooManyElementsWrittenException(bufferFill, bufferOffset);
		announcedSize = tsize;
		if(consumed == count) {
			skipChars = 1;
			--consumed;
		}
		return consumed;
	}

	@Override
	public void beginString(int totalSizeInBytes) throws IOException {
		requireNoString();
		slave.beginString(totalSizeInBytes);
		announcedSize = (long)totalSizeInBytes & 0xFFFFFFFFl;
		receivedSize = 0l;
	}

	@Override
	public int beginString(int totalSizeInBytes, char[] chars, int offset, int count) throws IOException {
		requireNoString();
		if(slave.isBlockingSink())
			return beginStringBlocking(totalSizeInBytes, chars, offset, count);
		else
			return beginStringNonBlocking(totalSizeInBytes, chars, offset, count);
	}

	private int beginStringBlocking(int totalSizeInBytes, char[] chars, int offset, int count) throws IOException {
		encoder.reset();
		long tsize = (long)totalSizeInBytes & 0xFFFFFFFFl;
		receivedSize = 0l;
		int consumed = 0;
		while(consumed < count || !encoder.isClean()) {
			bufferFill = 0;
			consumed += fillBuffer(chars, offset + consumed, count - consumed);
			if(bufferFill == 0)
				continue;
			bufferOffset = bufferFill;
			receivedSize += (long)bufferFill;
			if(receivedSize > tsize)
				throw new SequenceSizeMismatchException(tsize, receivedSize, true);
			int written;
			if(announcedSize < 0l) {
				written = slave.beginString(totalSizeInBytes, byteBuffer, 0, bufferFill);
				announcedSize = tsize;
			}
			else
				written = slave.continueString(byteBuffer, 0, bufferFill);
			if(written > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, written);
			if(written < bufferFill)
				throw new SynchronicityViolationException(bufferFill, written);
		}
		return count;
	}

	private int beginStringNonBlocking(int totalSizeInBytes, char[] chars, int offset, int count) throws IOException {
		encoder.reset();
		int consumed = fillBuffer(chars, offset, count);
		long tsize = (long)totalSizeInBytes & 0xFFFFFFFFl;
		receivedSize = (long)bufferFill;
		if(receivedSize > tsize)
			throw new SequenceSizeMismatchException(tsize, receivedSize, true);
		bufferOffset = slave.beginString(totalSizeInBytes, byteBuffer, 0, bufferFill);
		if(bufferOffset > bufferFill)
			throw new TooManyElementsWrittenException(bufferFill, bufferOffset);
		announcedSize = tsize;
		if(consumed == count && (bufferOffset < bufferFill || !encoder.isClean())) {
			skipChars = 1;
			--consumed;
		}
		return consumed;
	}

	@Override
	public int continueString(char[] chars, int offset, int count) throws IOException {
		requireString();
		if(slave.isBlockingSink())
			return continueStringBlocking(chars, offset, count);
		else
			return continueStringNonBlocking(chars, offset, count);
	}

	private int continueStringBlocking(char[] chars, int offset, int count) throws IOException {
		if(bufferFill > bufferOffset) {
			int rest = bufferFill - bufferOffset;
			int written = slave.continueString(byteBuffer, bufferOffset, rest);
			bufferOffset += written;
			if(written > rest)
				throw new TooManyElementsWrittenException(rest, written);
			if(written < rest)
				throw new SynchronicityViolationException(rest, written);
		}
		if(count <= 0)
			return 0;
		offset += skipChars;
		int trueCount = count - skipChars;
		skipChars = 0;
		int consumed = 0;
		while(consumed < trueCount || !encoder.isClean()) {
			bufferOffset = bufferFill = 0;
			consumed += fillBuffer(chars, offset + consumed, trueCount - consumed);
			if(bufferFill == 0)
				continue;
			bufferOffset = bufferFill;
			receivedSize += (long)bufferFill;
			if(receivedSize > announcedSize)
				throw new SequenceSizeMismatchException(announcedSize, receivedSize, true);
			int written = slave.continueString(byteBuffer, 0, bufferFill);
			if(written > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, written);
			if(written < bufferFill)
				throw new SynchronicityViolationException(bufferFill, written);
		}
		return count;
	}

	private int continueStringNonBlocking(char[] chars, int offset, int count) throws IOException {
		if(bufferFill > bufferOffset) {
			int rest = bufferFill - bufferOffset;
			int written = slave.continueString(byteBuffer, bufferOffset, rest);
			bufferOffset += written;
			if(written > rest)
				throw new TooManyElementsWrittenException(rest, written);
			return 0;
		}
		int consumed = 0;
		int trueCount = count;
		if(count > 0) {
			offset += skipChars;
			trueCount -= skipChars;
			consumed += skipChars;
			skipChars = 0;
		}
		bufferOffset = bufferFill = 0;
		consumed += fillBuffer(chars, offset, trueCount);
		receivedSize += (long)bufferFill;
		if(receivedSize > announcedSize)
			throw new SequenceSizeMismatchException(announcedSize, receivedSize, true);
		bufferOffset = slave.continueString(byteBuffer, 0, bufferFill);
		if(bufferOffset > bufferFill)
			throw new TooManyElementsWrittenException(bufferFill, bufferOffset);
		if(consumed == count && consumed > 0 && (bufferOffset < bufferFill || !encoder.isClean())) {
			skipChars = 1;
			--consumed;
		}
		return consumed;
	}

	@Override
	public void endString() throws IOException {
		requireString();
		if(skipChars > 0 || bufferFill > bufferOffset)
			throw new IllegalStateException("Out-of-sequence event received: Characters yet to be re-offered");
		if(receivedSize < announcedSize)
			throw new SequenceSizeMismatchException(announcedSize, receivedSize, false);
		slave.endString();
		announcedSize = -1l;
	}

	@Override
	public int endString(char[] chars, int offset, int count) throws IOException {
		requireString();
		if(slave.isBlockingSink())
			return endStringBlocking(chars, offset, count);
		else
			return endStringNonBlocking(chars, offset, count);
	}

	private int endStringBlocking(char[] chars, int offset, int count) throws IOException {
		if(bufferFill > bufferOffset) {
			int rest = bufferFill - bufferOffset;
			int written = slave.continueString(byteBuffer, bufferOffset, rest);
			bufferOffset += written;
			if(written > rest)
				throw new TooManyElementsWrittenException(rest, written);
			if(written < rest)
				throw new SynchronicityViolationException(rest, written);
		}
		if(count <= 0) {
			if(skipChars > 0)
				throw new IllegalStateException("Out-of-sequence event received: Characters yet to be re-offered");
			if(receivedSize < announcedSize)
				throw new SequenceSizeMismatchException(announcedSize, receivedSize, false);
			slave.endString();
			return 0;
		}
		offset += skipChars;
		int trueCount = count - skipChars;
		skipChars = 0;
		int consumed = 0;
		while(consumed < trueCount || !encoder.isClean()) {
			bufferOffset = bufferFill = 0;
			consumed += fillBuffer(chars, offset + consumed, trueCount - consumed);
			if(bufferFill == 0)
				continue;
			bufferOffset = bufferFill;
			receivedSize += (long)bufferFill;
			if(receivedSize > announcedSize)
				throw new SequenceSizeMismatchException(announcedSize, receivedSize, true);
			int written;
			if(consumed >= trueCount) {
				if(receivedSize < announcedSize)
					throw new SequenceSizeMismatchException(announcedSize, receivedSize, false);
				written = slave.endString(byteBuffer, 0, bufferFill);
			}
			else
				written = slave.continueString(byteBuffer, 0, bufferFill);
			if(written > bufferFill)
				throw new TooManyElementsWrittenException(bufferFill, written);
			if(written < bufferFill)
				throw new SynchronicityViolationException(bufferFill, written);
		}
		return count;
	}

	private int endStringNonBlocking(char[] chars, int offset, int count) throws IOException {
		if(bufferFill > bufferOffset) {
			if(count <= 0)
				throw new IllegalStateException("Out-of-sequence event received: Characters yet to be re-offered");
			int rest = bufferFill - bufferOffset;
			int written = slave.continueString(byteBuffer, bufferOffset, rest);
			bufferOffset += written;
			if(written > rest)
				throw new TooManyElementsWrittenException(rest, written);
			return 0;
		}
		int consumed = 0;
		int trueCount = count;
		if(skipChars > 0) {
			if(count <= 0)
				throw new IllegalStateException("Out-of-sequence event received: Characters yet to be re-offered");
			offset += skipChars;
			trueCount -= skipChars;
			consumed += skipChars;
			skipChars = 0;
		}
		bufferOffset = bufferFill = 0;
		consumed += fillBuffer(chars, offset, trueCount);
		receivedSize += (long)bufferFill;
		if(receivedSize > announcedSize)
			throw new SequenceSizeMismatchException(announcedSize, receivedSize, consumed >= count);
		if(consumed >= count) {
			if(receivedSize < announcedSize)
				throw new SequenceSizeMismatchException(announcedSize, receivedSize, false);
			bufferOffset = slave.endString(byteBuffer, 0, bufferFill);
		}
		else
			bufferOffset = slave.continueString(byteBuffer, 0, bufferFill);
		if(bufferOffset > bufferFill)
			throw new TooManyElementsWrittenException(bufferFill, bufferOffset);
		if(consumed == count && consumed > 0
				&& (consumed < count || bufferOffset < bufferFill || !encoder.isClean())) {
			skipChars = 1;
			--consumed;
		}
		return consumed;
	}

	@Override
	public void emptyBinary() throws IOException {
		requireNoString();
		slave.emptyBinary();
	}

	@Override
	public int binary(byte[] bytes, int offset, int count) throws IOException {
		requireNoString();
		return slave.binary(bytes, offset, count);
	}

	@Override
	public void beginBinary(int totalSize) throws IOException {
		requireNoString();
		slave.beginBinary(totalSize);
	}

	@Override
	public int beginBinary(int totalSize, byte[] bytes, int offset, int count) throws IOException {
		requireNoString();
		return slave.beginBinary(totalSize, bytes, offset, count);
	}

	@Override
	public int continueBinary(byte[] bytes, int offset, int count) throws IOException {
		requireNoString();
		return slave.continueBinary(bytes, offset, count);
	}

	@Override
	public void endBinary() throws IOException {
		requireNoString();
		slave.endBinary();
	}

	@Override
	public int endBinary(byte[] bytes, int offset, int count) throws IOException {
		requireNoString();
		return slave.endBinary(bytes, offset, count);
	}

	@Override
	public void emptyArray() throws IOException {
		requireNoString();
		slave.emptyArray();
	}

	@Override
	public void beginArray(int size) throws IOException {
		requireNoString();
		slave.beginArray(size);
	}

	@Override
	public void endArray() throws IOException {
		requireNoString();
		slave.endArray();
	}

	@Override
	public void emptyMap() throws IOException {
		requireNoString();
		slave.emptyMap();
	}

	@Override
	public void beginMap(int pairCount) throws IOException {
		requireNoString();
		slave.beginMap(pairCount);
	}

	@Override
	public void endMap() throws IOException {
		requireNoString();
		slave.endMap();
	}

}
