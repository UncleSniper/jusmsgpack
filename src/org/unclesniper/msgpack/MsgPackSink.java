package org.unclesniper.msgpack;

import java.io.IOException;

public interface MsgPackSink {

	void nil() throws IOException;

	void bool(boolean value) throws IOException;

	void integer(long value, boolean signed) throws IOException;

	void fraction(double value) throws IOException;

}
