package org.unclesniper.msgpack;

import java.io.IOException;

public interface MsgPackSink {

	void nil() throws IOException;

	void bool(boolean value) throws IOException;

	void integer(long value, boolean signed) throws IOException;

	void fraction(double value) throws IOException;

	void emptyString() throws IOException;

	void beginString(int totalSizeInBytes) throws IOException;

	void endString() throws IOException;

	void emptyBinary() throws IOException;

	int binary(byte[] bytes, int offset, int count) throws IOException;

	void beginBinary(int totalSize) throws IOException;

	int beginBinary(int totalSize, byte[] bytes, int offset, int count) throws IOException;

	int continueBinary(byte[] bytes, int offset, int count) throws IOException;

	void endBinary() throws IOException;

	int endBinary(byte[] bytes, int offset, int count) throws IOException;

	void emptyArray() throws IOException;

	void beginArray(int size) throws IOException;

	void endArray() throws IOException;

	void emptyMap() throws IOException;

	void beginMap(int pairCount) throws IOException;

	void endMap() throws IOException;

	boolean isBlockingSink();

}
