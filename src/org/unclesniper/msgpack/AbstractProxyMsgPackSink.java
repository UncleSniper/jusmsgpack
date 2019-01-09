package org.unclesniper.msgpack;

import java.io.IOException;

public abstract class AbstractProxyMsgPackSink implements MsgPackSink {

	private MsgPackSink slave;

	public AbstractProxyMsgPackSink(MsgPackSink slave) {
		this.slave = slave;
	}

	public final MsgPackSink getRawSlave() {
		return slave;
	}

	protected final void setRawSlave(MsgPackSink slave) {
		this.slave = slave;
	}

	@Override
	public void nil() throws IOException {
		slave.nil();
	}

	@Override
	public void bool(boolean value) throws IOException {
		slave.bool(value);
	}

	@Override
	public void integer(long value, boolean signed) throws IOException {
		slave.integer(value, signed);
	}

	@Override
	public void fraction(double value) throws IOException {
		slave.fraction(value);
	}

	@Override
	public void emptyString() throws IOException {
		slave.emptyString();
	}

	@Override
	public void beginString(int totalSizeInBytes) throws IOException {
		slave.beginString(totalSizeInBytes);
	}

	@Override
	public void endString() throws IOException {
		slave.endString();
	}

	@Override
	public void emptyBinary() throws IOException {
		slave.emptyBinary();
	}

	@Override
	public int binary(byte[] bytes, int offset, int count) throws IOException {
		return slave.binary(bytes, offset, count);
	}

	@Override
	public void beginBinary(int totalSize) throws IOException {
		slave.beginBinary(totalSize);
	}

	@Override
	public int beginBinary(int totalSize, byte[] bytes, int offset, int count) throws IOException {
		return slave.beginBinary(totalSize, bytes, offset, count);
	}

	@Override
	public int continueBinary(byte[] bytes, int offset, int count) throws IOException {
		return slave.continueBinary(bytes, offset, count);
	}

	@Override
	public void endBinary() throws IOException {
		slave.endBinary();
	}

	@Override
	public int endBinary(byte[] bytes, int offset, int count) throws IOException {
		return slave.endBinary(bytes, offset, count);
	}

	@Override
	public boolean isBlockingSink() {
		return slave.isBlockingSink();
	}

	@Override
	public void emptyArray() throws IOException {
		slave.emptyArray();
	}

	@Override
	public void beginArray(int size) throws IOException {
		slave.beginArray(size);
	}

	@Override
	public void endArray() throws IOException {
		slave.endArray();
	}

	@Override
	public void emptyMap() throws IOException {
		slave.emptyMap();
	}

	@Override
	public void beginMap(int pairCount) throws IOException {
		slave.beginMap(pairCount);
	}

	@Override
	public void endMap() throws IOException {
		slave.endMap();
	}

}
