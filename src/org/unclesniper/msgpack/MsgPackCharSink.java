package org.unclesniper.msgpack;

import java.io.IOException;

public interface MsgPackCharSink extends MsgPackSink {

	int string(char[] chars, int offset, int count) throws IOException;

	int beginString(int totalSizeInBytes, char[] chars, int offset, int count) throws IOException;

	int continueString(char[] chars, int offset, int count) throws IOException;

	int endString(char[] chars, int offset, int count) throws IOException;

}
