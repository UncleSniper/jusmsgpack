package org.unclesniper.msgpack;

import java.nio.ByteBuffer;
import java.io.IOException;

public class MsgPackEncoder implements MsgPackSink {

	private static final int BUFFER_SIZE = 9;

	private final byte[] buffer = new byte[MsgPackEncoder.BUFFER_SIZE];

	private MsgPackWriter writer;

	public MsgPackEncoder(MsgPackWriter writer) {
		this.writer = writer;
	}

	public MsgPackWriter getWriter() {
		return writer;
	}

	public void setWriter(MsgPackWriter writer) {
		this.writer = writer;
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

	public void nil() throws IOException {
		buffer[0] = (byte)0xC0;
		writer.writeChunk(buffer, 0, 1);
	}

	public void bool(boolean value) throws IOException {
		buffer[0] = (byte)(value ? 0xC3 : 0xC2);
		writer.writeChunk(buffer, 0, 1);
	}

	public void integer(long value, boolean signed) throws IOException {
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
	}

	public void fraction(double value) throws IOException {
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
	}

}
