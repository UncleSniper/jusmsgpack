package org.unclesniper.msgpack;

import java.io.IOException;

public interface MsgPackByteSink extends MsgPackSink {

	int string(byte[] bytes, int offset, int count) throws IOException;

	int beginString(int totalSize, byte[] bytes, int offset, int count) throws IOException;

	int continueString(byte[] bytes, int offset, int count) throws IOException;

	int endString(byte[] bytes, int offset, int count) throws IOException;

}
