package org.unclesniper.msgpack;

import java.nio.ByteBuffer;
import java.io.IOException;

public class MsgPackEncoder implements MsgPackByteSink {

	private enum Structure {
		STRING,
		BINARY,
		ARRAY,
		MAP
	}

	private static class Level {

		final Level parent;

		final Structure structure;

		final long announcedSize;

		long receivedSize;

		Level(Level parent, Structure structure, long announcedSize, long receivedSize) {
			this.parent = parent;
			this.structure = structure;
			this.announcedSize = announcedSize;
			this.receivedSize = receivedSize;
		}

	}

	private static final int BUFFER_SIZE = 32;  // >= 9

	private final byte[] buffer = new byte[MsgPackEncoder.BUFFER_SIZE];

	private MsgPackWriter writer;

	private Level stack;

	public MsgPackEncoder(MsgPackWriter writer) {
		this.writer = writer;
	}

	public MsgPackWriter getWriter() {
		return writer;
	}

	public void setWriter(MsgPackWriter writer) {
		this.writer = writer;
	}

	@Override
	public boolean isBlockingSink() {
		return writer.isBlockingWriter();
	}

	private void putShort(int offset, short value) {
		buffer[offset] = (byte)(value >>> 8);
		buffer[offset + 1] = (byte)(value & 0xFF);
	}

	private void putInt(int offset, int value) {
		buffer[offset] = (byte)(value >>> 24);
		buffer[offset + 1] = (byte)((value >>> 16) & 0xFF);
		buffer[offset + 2] = (byte)((value >>> 8) & 0xFF);
		buffer[offset + 3] = (byte)(value & 0xFF);
	}

	private void putLong(int offset, long value) {
		buffer[offset] = (byte)(value >>> 56);
		buffer[offset] = (byte)((value >>> 48) & 0xFFl);
		buffer[offset] = (byte)((value >>> 40) & 0xFFl);
		buffer[offset] = (byte)((value >>> 32) & 0xFFl);
		buffer[offset] = (byte)((value >>> 24) & 0xFFl);
		buffer[offset] = (byte)((value >>> 16) & 0xFFl);
		buffer[offset] = (byte)((value >>> 8) & 0xFFl);
		buffer[offset] = (byte)(value & 0xFFl);
	}

	private void requireClean() {
		if(stack == null)
			return;
		switch(stack.structure) {
			case STRING:
			case BINARY:
				throw new IllegalStateException("Out-of-sequence event received: Still within "
						+ stack.structure.name().toLowerCase() + " structure");
		}
	}

	private void requireString() {
		if(stack == null || stack.structure != Structure.STRING)
			throw new IllegalStateException("Out-of-sequence event received: Not within string structure");
	}

	private void requireBinary() {
		if(stack == null || stack.structure != Structure.BINARY)
			throw new IllegalStateException("Out-of-sequence event received: Not within binary structure");
	}

	private void advanceStructure() throws SequenceSizeMismatchException {
		if(stack == null)
			return;
		switch(stack.structure) {
			case ARRAY:
			case MAP:
				if(++stack.receivedSize > stack.announcedSize)
					throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, true);
				break;
		}
	}

	@Override
	public void nil() throws IOException {
		requireClean();
		buffer[0] = (byte)0xC0;
		writer.writeChunk(buffer, 0, 1);
		advanceStructure();
	}

	@Override
	public void bool(boolean value) throws IOException {
		requireClean();
		buffer[0] = (byte)(value ? 0xC3 : 0xC2);
		writer.writeChunk(buffer, 0, 1);
		advanceStructure();
	}

	@Override
	public void integer(long value, boolean signed) throws IOException {
		requireClean();
		if(signed) {
			if(value >= 0l && value <= 0x7Fl) {
				// positive fixint
				buffer[0] = (byte)value;
				writer.writeChunk(buffer, 0, 1);
			}
			else if(value < 0l && value >= -0x20l) {
				// negative fixint
				buffer[0] = (byte)value;
				writer.writeChunk(buffer, 0, 1);
			}
			else if(value >= -0x80l && value < 0x80l) {
				// int 8
				buffer[0] = (byte)0xD0;
				buffer[1] = (byte)value;
				writer.writeChunk(buffer, 0, 2);
			}
			else if(value >= -0x8000l && value < 0x8000l) {
				// int 16
				buffer[0] = (byte)0xD1;
				putShort(1, (short)value);
				writer.writeChunk(buffer, 0, 3);
			}
			else if(value >= -0x800000l && value < 0x800000l) {
				// int 32
				buffer[0] = (byte)0xD2;
				putInt(1, (int)value);
				writer.writeChunk(buffer, 0, 5);
			}
			else {
				// int 64
				buffer[0] = (byte)0xD3;
				putLong(1, value);
				writer.writeChunk(buffer, 0, 9);
			}
		}
		else {
			if(value < 0l) {
				// uint 64
				buffer[0] = (byte)0xCF;
				putLong(1, value);
				writer.writeChunk(buffer, 0, 9);
			}
			else if(value <= 0xFFl) {
				// uint 8
				buffer[0] = (byte)0xCC;
				buffer[1] = (byte)value;
				writer.writeChunk(buffer, 0, 2);
			}
			else if(value <= 0xFFFFl) {
				// uint 16
				buffer[0] = (byte)0xCD;
				putShort(1, (short)value);
				writer.writeChunk(buffer, 0, 3);
			}
			else if(value <= 0xFFFFFFFFl) {
				// uint 32
				buffer[0] = (byte)0xCE;
				putInt(1, (int)value);
				writer.writeChunk(buffer, 0, 5);
			}
			else {
				// uint 64
				buffer[0] = (byte)0xCF;
				putLong(1, value);
				writer.writeChunk(buffer, 0, 9);
			}
		}
		advanceStructure();
	}

	@Override
	public void fraction(double value) throws IOException {
		requireClean();
		float fvalue = (float)value;
		if((double)fvalue == value) {
			// float 32
			buffer[0] = (byte)0xCA;
			putInt(1, Float.floatToIntBits(fvalue));
			writer.writeChunk(buffer, 0, 5);
		}
		else {
			// float 64
			buffer[0] = (byte)0xCB;
			putLong(1, Double.doubleToLongBits(value));
			writer.writeChunk(buffer, 0, 9);
		}
		advanceStructure();
	}

	@Override
	public void emptyString() throws IOException {
		requireClean();
		buffer[0] = (byte)0xA0;
		writer.writeChunk(buffer, 0, 1);
		advanceStructure();
	}

	private int stringHeader(int totalSizeInBytes) {
		long tsize = (long)totalSizeInBytes & 0xFFFFFFFFl;
		if(tsize < 32l) {
			buffer[0] = (byte)(0xA0 | totalSizeInBytes);
			return 1;
		}
		if(tsize < 0x100l) {
			buffer[0] = (byte)0xD9;
			buffer[1] = (byte)totalSizeInBytes;
			return 2;
		}
		if(tsize < 0x10000l) {
			buffer[0] = (byte)0xDA;
			putShort(1, (short)totalSizeInBytes);
			return 3;
		}
		buffer[0] = (byte)0xDB;
		putInt(1, totalSizeInBytes);
		return 5;
	}

	@Override
	public int string(byte[] bytes, int offset, int count) throws IOException {
		requireClean();
		if(count < 0)
			count = 0;
		int hsize = stringHeader(count);
		if(count == 0) {
			writer.writeChunk(buffer, 0, 1);
			advanceStructure();
			return 0;
		}
		if(writer.isBlockingWriter()) {
			writer.writeChunk(buffer, 0, hsize);
			writer.writeChunk(bytes, offset, count);
			advanceStructure();
			return count;
		}
		int all = hsize + count;
		if(all >= hsize) {
			boolean full;
			int chunk;
			if(all > buffer.length) {
				all = buffer.length;
				chunk = all - hsize;
				full = false;
			}
			else {
				chunk = count;
				full = true;
			}
			int end = offset + chunk;
			for(; offset < end; ++offset)
				buffer[hsize + offset] = bytes[offset];
			writer.writeChunk(buffer, 0, all);
			if(full)
				advanceStructure();
			else
				stack = new Level(stack, Structure.STRING, (long)count, (long)chunk);
			return chunk;
		}
		writer.writeChunk(buffer, 0, hsize);
		stack = new Level(stack, Structure.STRING, (long)count, 0l);
		return 0;
	}

	@Override
	public void beginString(int totalSizeInBytes) throws IOException {
		requireClean();
		writer.writeChunk(buffer, 0, stringHeader(totalSizeInBytes));
		stack = new Level(stack, Structure.STRING, (long)totalSizeInBytes & 0xFFFFFFFFl, 0l);
	}

	@Override
	public int beginString(int totalSize, byte[] bytes, int offset, int count) throws IOException {
		requireClean();
		if(count < 0)
			count = 0;
		long tsize = (long)totalSize & 0xFFFFFFFFl;
		if((long)count > tsize)
			throw new SequenceSizeMismatchException(tsize, (long)count, true);
		int hsize = stringHeader(totalSize);
		int written;
		if(writer.isBlockingWriter()) {
			writer.writeChunk(buffer, 0, hsize);
			writer.writeChunk(bytes, offset, count);
			written = count;
		}
		else {
			int all = hsize + count;
			if(all >= hsize) {
				if(all > buffer.length) {
					all = buffer.length;
					count = all - hsize;
				}
				int end = offset + count;
				for(; offset < end; ++offset)
					buffer[hsize + offset] = bytes[offset];
				writer.writeChunk(buffer, 0, all);
				written = count;
			}
			else {
				writer.writeChunk(buffer, 0, hsize);
				written = 0;
			}
		}
		stack = new Level(stack, Structure.STRING, tsize, (long)written);
		return written;
	}

	@Override
	public int continueString(byte[] bytes, int offset, int count) throws IOException {
		requireString();
		if(count < 0)
			count = 0;
		if(count == 0)
			return 0;
		stack.receivedSize += (long)count;
		if(stack.receivedSize > stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, true);
		writer.writeChunk(bytes, offset, count);
		return count;
	}

	@Override
	public void endString() throws IOException {
		requireString();
		if(stack.receivedSize < stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
	}

	@Override
	public int endString(byte[] bytes, int offset, int count) throws IOException {
		requireString();
		if(count < 0)
			count = 0;
		if(count > 0) {
			stack.receivedSize += (long)count;
			if(stack.receivedSize > stack.announcedSize)
				throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, true);
			writer.writeChunk(bytes, offset, count);
		}
		if(stack.receivedSize < stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
		return count;
	}

	@Override
	public void emptyBinary() throws IOException {
		requireClean();
		buffer[0] = (byte)0xC4;
		buffer[1] = (byte)0;
		writer.writeChunk(buffer, 0, 2);
		advanceStructure();
	}

	private int binaryHeader(int totalSize) {
		long tsize = (long)totalSize & 0xFFFFFFFFl;
		if(tsize < 0x100l) {
			buffer[0] = (byte)0xC4;
			buffer[1] = (byte)totalSize;
			return 2;
		}
		if(tsize < 0x10000l) {
			buffer[0] = (byte)0xC5;
			putShort(1, (short)totalSize);
			return 3;
		}
		buffer[0] = (byte)0xC6;
		putInt(1, totalSize);
		return 5;
	}

	@Override
	public int binary(byte[] bytes, int offset, int count) throws IOException {
		requireClean();
		if(count < 0)
			count = 0;
		int hsize = binaryHeader(count);
		if(count == 0) {
			writer.writeChunk(buffer, 0, 2);
			advanceStructure();
			return 0;
		}
		if(writer.isBlockingWriter()) {
			writer.writeChunk(buffer, 0, hsize);
			writer.writeChunk(bytes, offset, count);
			advanceStructure();
			return count;
		}
		int all = hsize + count;
		if(all >= hsize) {
			boolean full;
			int chunk;
			if(all > buffer.length) {
				all = buffer.length;
				chunk = all - hsize;
				full = false;
			}
			else {
				chunk = count;
				full = true;
			}
			int end = offset + chunk;
			for(; offset < end; ++offset)
				buffer[hsize + offset] = bytes[offset];
			writer.writeChunk(buffer, 0, all);
			if(full)
				advanceStructure();
			else
				stack = new Level(stack, Structure.BINARY, (long)count, (long)chunk);
			return count;
		}
		writer.writeChunk(buffer, 0, hsize);
		stack = new Level(stack, Structure.BINARY, (long)count, 0l);
		return 0;
	}

	@Override
	public void beginBinary(int totalSize) throws IOException {
		requireClean();
		writer.writeChunk(buffer, 0, binaryHeader(totalSize));
		stack = new Level(stack, Structure.BINARY, (long)totalSize & 0xFFFFFFFFl, 0l);
	}

	@Override
	public int beginBinary(int totalSize, byte[] bytes, int offset, int count) throws IOException {
		requireClean();
		if(count < 0)
			count = 0;
		long tsize = (long)totalSize & 0xFFFFFFFFl;
		if((long)count > tsize)
			throw new SequenceSizeMismatchException(tsize, (long)count, true);
		int hsize = binaryHeader(totalSize);
		int written;
		if(writer.isBlockingWriter()) {
			writer.writeChunk(buffer, 0, hsize);
			writer.writeChunk(bytes, offset, count);
			written = count;
		}
		else {
			int all = hsize + count;
			if(all >= hsize) {
				if(all > buffer.length) {
					all = buffer.length;
					count = all - hsize;
				}
				int end = offset + count;
				for(; offset < end; ++offset)
					buffer[hsize + offset] = bytes[offset];
				writer.writeChunk(buffer, 0, all);
				written = count;
			}
			else {
				writer.writeChunk(buffer, 0, hsize);
				written = 0;
			}
		}
		stack = new Level(stack, Structure.BINARY, tsize, (long)written);
		return written;
	}

	@Override
	public int continueBinary(byte[] bytes, int offset, int count) throws IOException {
		requireBinary();
		if(count < 0)
			count = 0;
		if(count == 0)
			return 0;
		stack.receivedSize += (long)count;
		if(stack.receivedSize > stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, true);
		writer.writeChunk(bytes, offset, count);
		return count;
	}

	@Override
	public void endBinary() throws IOException {
		requireBinary();
		if(stack.receivedSize < stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
	}

	@Override
	public int endBinary(byte[] bytes, int offset, int count) throws IOException {
		requireBinary();
		if(count < 0)
			count = 0;
		if(count > 0) {
			stack.receivedSize += (long)count;
			if(stack.receivedSize > stack.announcedSize)
				throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, true);
			writer.writeChunk(bytes, offset, count);
		}
		if(stack.receivedSize < stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
		return count;
	}

	@Override
	public void emptyArray() throws IOException {
		requireClean();
		buffer[0] = (byte)0x90;
		writer.writeChunk(buffer, 0, 1);
	}

	@Override
	public void beginArray(int size) throws IOException {
		requireClean();
		long tsize = (long)size & 0xFFFFFFFFl;
		if(tsize < 0xFl) {
			buffer[0] = (byte)(0x90 | size);
			writer.writeChunk(buffer, 0, 1);
		}
		else if(tsize < 0x10000l) {
			buffer[0] = (byte)0xDC;
			putShort(1, (short)size);
			writer.writeChunk(buffer, 0, 3);
		}
		else {
			buffer[0] = (byte)0xDD;
			putInt(1, size);
			writer.writeChunk(buffer, 0, 5);
		}
		stack = new Level(stack, Structure.ARRAY, tsize, 0l);
	}

	@Override
	public void endArray() throws IOException {
		requireClean();
		if(stack == null || stack.structure != Structure.ARRAY)
			throw new IllegalStateException("Out-of-sequence event received: Not within array structure");
		if(stack.receivedSize != stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
	}

	@Override
	public void emptyMap() throws IOException {
		requireClean();
		buffer[0] = (byte)0x80;
		writer.writeChunk(buffer, 0, 1);
	}

	@Override
	public void beginMap(int pairCount) throws IOException {
		requireClean();
		long pcount = (long)pairCount & 0xFFFFFFFFl;
		if(pcount < 0xFl) {
			buffer[0] = (byte)(0x80 | pairCount);
			writer.writeChunk(buffer, 0, 1);
		}
		else if(pcount < 0x10000l) {
			buffer[0] = (byte)0xDE;
			putShort(1, (short)pairCount);
			writer.writeChunk(buffer, 0, 3);
		}
		else {
			buffer[0] = (byte)0xDF;
			putInt(1, pairCount);
			writer.writeChunk(buffer, 0, 5);
		}
		stack = new Level(stack, Structure.ARRAY, pcount * 2l, 0l);
	}

	@Override
	public void endMap() throws IOException {
		requireClean();
		if(stack == null || stack.structure != Structure.MAP)
			throw new IllegalStateException("Out-of-sequence event received: Not within map structure");
		if(stack.receivedSize != stack.announcedSize)
			throw new SequenceSizeMismatchException(stack.announcedSize, stack.receivedSize, false);
		stack = stack.parent;
		advanceStructure();
	}

}
