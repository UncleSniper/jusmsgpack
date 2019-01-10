package org.unclesniper.msgpack;

import java.io.IOException;

public class MsgPackDecoder {

	private enum Structure {
		ARRAY,
		MAP
	}

	private static class Level {

		final Level parent;

		final Structure structure;

		long remainingLength;

		Level(Level parent, Structure structure, long remainingLength) {
			this.parent = parent;
			this.structure = structure;
			this.remainingLength = remainingLength;
		}

	}

	private enum State {
		CLEAN,
		INT,
		UINT,
		FLOAT,
		DOUBLE,
		STRING,
		STRING_LENGTH,
		BINARY,
		BINARY_LENGTH,
		ARRAY_LENGTH,
		MAP_LENGTH,
		EXTENSION,
		EXTENSION_LENGTH,
		EXTENSION_TYPE
	}

	private Level stack;

	private MsgPackByteSink sink;

	private State state = State.CLEAN;

	private long remainingLength;

	private long accumulator;

	private boolean nextByteIsSign;

	private boolean needsPushDown;

	public MsgPackDecoder(MsgPackByteSink sink) {
		this.sink = sink;
	}

	public MsgPackByteSink getSink() {
		return sink;
	}

	public void setSink(MsgPackByteSink sink) {
		this.sink = sink;
	}

	public int pushBytes(byte[] bytes) throws IOException {
		return pushBytes(bytes, 0, bytes.length);
	}

	private boolean enterString(long length) throws IOException {
		if(length == 0l) {
			sink.emptyString();
			return false;
		}
		remainingLength = length;
		state = State.STRING;
		sink.beginString((int)length);
		return true;
	}

	private boolean enterBinary() throws IOException {
		if(remainingLength == 0l) {
			sink.emptyBinary();
			return false;
		}
		state = State.BINARY;
		sink.beginBinary((int)remainingLength);
		return true;
	}

	private boolean enterExtension(byte type) throws IOException {
		if(remainingLength == 0l) {
			sink.emptyExtension(type);
			return false;
		}
		state = State.EXTENSION;
		sink.beginExtension(type, (int)remainingLength);
		return true;
	}

	private boolean enterArray(long size) throws IOException {
		state = State.CLEAN;
		if(size == 0l) {
			sink.emptyArray();
			return false;
		}
		stack = new Level(stack, Structure.ARRAY, size);
		sink.beginArray((int)size);
		return true;
	}

	private boolean enterMap(long pairCount) throws IOException {
		state = State.CLEAN;
		if(pairCount == 0l) {
			sink.emptyMap();
			return false;
		}
		stack = new Level(stack, Structure.MAP, pairCount * 2l);
		sink.beginMap((int)pairCount);
		return true;
	}

	private boolean pushDown(boolean nonBlocking, boolean issued) throws IOException {
		while(stack != null) {
			if(stack.remainingLength > 1l) {
				--stack.remainingLength;
				return nonBlocking;
			}
			if(issued && nonBlocking) {
				needsPushDown = true;
				return true;
			}
			switch(stack.structure) {
				case ARRAY:
					stack = stack.parent;
					sink.endArray();
					break;
				case MAP:
					stack = stack.parent;
					sink.endMap();
					break;
				default:
					throw new Doom("Unrecognized structure: " + stack.structure.name());
			}
			if(nonBlocking) {
				needsPushDown = stack != null;
				return true;
			}
		}
		return nonBlocking;
	}

	public int pushBytes(byte[] bytes, int offset, int length) throws IOException {
		boolean nonBlocking = !sink.isBlockingSink();
		if(needsPushDown) {
			needsPushDown = false;
			if(pushDown(nonBlocking, false))
				return 0;
		}
		int chunk, written;
		int end = offset + length;
		int i = offset;
	  perByte:
		for(; i < end; ++i) {
			byte b = bytes[i];
			switch(state) {
				case CLEAN:
					if(b >= 0) {
						// positive fixint
						sink.integer((long)b, true);
						if(pushDown(nonBlocking, true))
							break perByte;
						break;
					}
					switch(b & 0xE0) {
						case 0x80:
							// 100xxxxx => fixmap, fixarray
							if((b & 0x10) == 0) {
								// fixmap
								enterMap((long)(b & 0xF));
							}
							else {
								// fixarray
								enterArray((long)(b & 0xF));
							}
							if(nonBlocking)
								break perByte;
							break;
						case 0xA0:
							// 101xxxxx => fixstr
							if(enterString((long)(b & 0x1F)) ? nonBlocking : pushDown(nonBlocking, true))
								break perByte;
							break;
						case 0xC0:
							// 110xxxxx => nil, false, true, bin 8, bin 16, bin 32, ext 8, ext 16, ext 32,
							//             float 32, float 64, uint 8, uint 16, uint 32, uint 64,
							//             int 8, int 16, int 32, int 64, fixext 1, fixext 2, fixext 4,
							//             fixext 8, fixext 16, str 8, str 16, str 32, array 16, array 32,
							//             map 16, map 32
							switch(b & 0xFF) {
								case 0xC0:
									// nil
									sink.nil();
									if(pushDown(nonBlocking, true))
										break perByte;
									break;
								case 0xC1:
									// (never used)
									throw new ReservedInitiatorByteUsedException();
								case 0xC2:
									// false
									sink.bool(false);
									if(pushDown(nonBlocking, true))
										break perByte;
									break;
								case 0xC3:
									// true
									sink.bool(true);
									if(pushDown(nonBlocking, true))
										break perByte;
									break;
								case 0xC4:
									// bin 8
									accumulator = 0l;
									remainingLength = 1l;
									state = State.BINARY_LENGTH;
									break;
								case 0xC5:
									// bin 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.BINARY_LENGTH;
									break;
								case 0xC6:
									// bin 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.BINARY_LENGTH;
									break;
								case 0xC7:
									// ext 8
									accumulator = 0l;
									remainingLength = 1l;
									state = State.EXTENSION_LENGTH;
									break;
								case 0xC8:
									// ext 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.EXTENSION_LENGTH;
									break;
								case 0xC9:
									// ext 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.EXTENSION_LENGTH;
									break;
								case 0xCA:
									// float 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.FLOAT;
									break;
								case 0xCB:
									// float 64
									accumulator = 0l;
									remainingLength = 8l;
									state = State.DOUBLE;
									break;
								case 0xCC:
									// uint 8
									accumulator = 0l;
									remainingLength = 1l;
									state = State.UINT;
									break;
								case 0xCD:
									// uint 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.UINT;
									break;
								case 0xCE:
									// uint 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.UINT;
									break;
								case 0xCF:
									// uint 64
									accumulator = 0l;
									remainingLength = 8l;
									state = State.UINT;
									break;
								case 0xD0:
									// int 8
									nextByteIsSign = true;
									remainingLength = 1l;
									state = State.INT;
									break;
								case 0xD1:
									// int 16
									nextByteIsSign = true;
									remainingLength = 2l;
									state = State.INT;
									break;
								case 0xD2:
									// int 32
									nextByteIsSign = true;
									remainingLength = 4l;
									state = State.INT;
									break;
								case 0xD3:
									// int 64
									nextByteIsSign = true;
									remainingLength = 8l;
									state = State.INT;
									break;
								case 0xD4:
									// fixext 1
									remainingLength = 1l;
									state = State.EXTENSION_TYPE;
									break;
								case 0xD5:
									// fixext 2
									remainingLength = 2l;
									state = State.EXTENSION_TYPE;
									break;
								case 0xD6:
									// fixext 4
									remainingLength = 4l;
									state = State.EXTENSION_TYPE;
									break;
								case 0xD7:
									// fixext 8
									remainingLength = 8l;
									state = State.EXTENSION_TYPE;
									break;
								case 0xD8:
									// fixext 16
									remainingLength = 16l;
									state = State.EXTENSION_TYPE;
									break;
								case 0xD9:
									// str 8
									accumulator = 0l;
									remainingLength = 1l;
									state = State.STRING_LENGTH;
									break;
								case 0xDA:
									// str 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.STRING_LENGTH;
									break;
								case 0xDB:
									// str 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.STRING_LENGTH;
									break;
								case 0xDC:
									// array 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.ARRAY_LENGTH;
									break;
								case 0xDD:
									// array 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.ARRAY_LENGTH;
									break;
								case 0xDE:
									// map 16
									accumulator = 0l;
									remainingLength = 2l;
									state = State.MAP_LENGTH;
									break;
								case 0xDF:
									// map 32
									accumulator = 0l;
									remainingLength = 4l;
									state = State.MAP_LENGTH;
									break;
								default:
									throw new Doom("Bit twiddling error");
							}
							break;
						case 0xE0:
							// 111xxxxx => negative fixint
							sink.integer((long)b, true);
							if(pushDown(nonBlocking, true))
								break perByte;
							break;
						default:
							throw new Doom("Bit twiddling error");
					}
					break;
				case INT:
					if(nextByteIsSign) {
						accumulator = b < 0 ? ~0l : 0l;
						nextByteIsSign = false;
					}
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.integer(accumulator, true);
						if(pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case UINT:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.integer(accumulator, false);
						if(pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case STRING_LENGTH:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						remainingLength = accumulator;
						if(enterString(accumulator)) {
							if(nonBlocking)
								break perByte;
						}
						else {
							state = State.CLEAN;
							if(pushDown(nonBlocking, true))
								break perByte;
						}
					}
					break;
				case BINARY_LENGTH:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						remainingLength = accumulator;
						if(enterBinary()) {
							if(nonBlocking)
								break perByte;
						}
						else {
							state = State.CLEAN;
							if(pushDown(nonBlocking, true))
								break perByte;
						}
					}
					break;
				case EXTENSION_LENGTH:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						remainingLength = accumulator;
						state = State.EXTENSION_TYPE;
					}
					break;
				case EXTENSION_TYPE:
					if(enterExtension(b)) {
						if(nonBlocking)
							break perByte;
					}
					else {
						state = State.CLEAN;
						if(pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case ARRAY_LENGTH:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						if(enterArray(accumulator) ? nonBlocking : pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case MAP_LENGTH:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						if(enterMap(accumulator) ? nonBlocking : pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case FLOAT:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.fraction(Float.intBitsToFloat((int)accumulator));
						if(pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case DOUBLE:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.fraction(Double.longBitsToDouble(accumulator));
						if(pushDown(nonBlocking, true))
							break perByte;
					}
					break;
				case STRING:
				case BINARY:
					chunk = end - i;
					if((long)chunk >= remainingLength) {
						chunk = (int)remainingLength;
						if(state == State.STRING)
							written = sink.endString(bytes, i, chunk);
						else
							written = sink.endBinary(bytes, i, chunk);
					}
					else {
						if(state == State.STRING)
							written = sink.continueString(bytes, i, chunk);
						else
							written = sink.continueBinary(bytes, i, chunk);
					}
					if(written > chunk)
						throw new TooManyElementsWrittenException(chunk, written);
					remainingLength -= (long)written;
					i += chunk - 1;
					if(remainingLength == 0l)
						state = State.CLEAN;
					if((remainingLength == 0l ? pushDown(nonBlocking, true) : nonBlocking) || i >= end)
						break perByte;
					break;
				default:
					throw new Doom("Unrecognized state: " + state.name());
			}
		}
		return (i >= end ? i : i + 1) - offset;
	}

}
