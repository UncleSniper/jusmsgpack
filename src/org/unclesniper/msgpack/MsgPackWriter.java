package org.unclesniper.msgpack;

import java.nio.ByteBuffer;
import java.io.IOException;

public interface MsgPackWriter {

	void writeChunk(byte[] buffer, int offset, int length) throws IOException;

}
