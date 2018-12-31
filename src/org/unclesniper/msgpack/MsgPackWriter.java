package org.unclesniper.msgpack;

import java.nio.ByteBuffer;
import java.io.IOException;

public interface MsgPackWriter {

	void writeChunk(ByteBuffer buffer) throws IOException;

}
