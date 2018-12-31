package org.unclesniper.msgpack;

import java.nio.ByteBuffer;
import java.io.IOException;

public class MsgPackEncoder implements MsgPackSink {

	private static final int BUFFER_SIZE = 9;

	private final ByteBuffer buffer;

	private MsgPackWriter writer;

	public MsgPackEncoder(MsgPackWriter writer, boolean direct) {
		if(direct)
			buffer = ByteBuffer.allocateDirect(MsgPackEncoder.BUFFER_SIZE);
		else
			buffer = ByteBuffer.allocate(MsgPackEncoder.BUFFER_SIZE);
		this.writer = writer;
	}

	public MsgPackWriter getWriter() {
		return writer;
	}

	public void setWriter(MsgPackWriter writer) {
		this.writer = writer;
	}

	public void nil() throws IOException {
		buffer.clear();
		buffer.put((byte)0xC0);
		buffer.flip();
		writer.writeChunk(buffer);
	}

	public void bool(boolean value) throws IOException {
		buffer.clear();
		buffer.put((byte)(value ? 0xC3 : 0xC2));
		buffer.flip();
		writer.writeChunk(buffer);
	}

	public void integer(long value, boolean signed) throws IOException {
		buffer.clear();
		if(signed) {
			if(value >= 0l && value <= 0x7Fl) {
				// positive fixint
				buffer.put((byte)value);
			}
			else if(value < 0l && value >= -0x20l) {
				// negative fixint
				buffer.put((byte)value);
			}
			else if(value >= -0x80l && value < 0x80l) {
				// int 8
				buffer.put((byte)0xD0);
				buffer.put((byte)value);
			}
			else if(value >= -0x8000l && value < 0x8000l) {
				// int 16
				buffer.put((byte)0xD1);
				buffer.putShort((short)value);
			}
			else if(value >= -0x800000l && value < 0x800000l) {
				// int 32
				buffer.put((byte)0xD2);
				buffer.putInt((int)value);
			}
			else {
				// int 64
				buffer.put((byte)0xD3);
				buffer.putLong(value);
			}
		}
		else {
			if(value < 0l) {
				// uint 64
				buffer.put((byte)0xCF);
				buffer.putLong(value);
			}
			else if(value <= 0xFFl) {
				// uint 8
				buffer.put((byte)0xCC);
				buffer.put((byte)value);
			}
			else if(value <= 0xFFFFl) {
				// uint 16
				buffer.put((byte)0xCD);
				buffer.putShort((short)value);
			}
			else if(value <= 0xFFFFFFFFl) {
				// uint 32
				buffer.put((byte)0xCE);
				buffer.putInt((int)value);
			}
			else {
				// uint 64
				buffer.put((byte)0xCF);
				buffer.putLong(value);
			}
		}
		buffer.flip();
		writer.writeChunk(buffer);
	}

}
