package org.unclesniper.msgpack;

import java.util.Deque;
import java.io.IOException;
import java.util.LinkedList;

public class MsgPackDecoder {

	private enum Structure {
		ARRAY,
		MAP
	}

	private static class Level {

		final Structure structure;

		Level(Structure structure) {
			this.structure = structure;
		}

	}

	private enum State {
		CLEAN,
		INT,
		UINT,
		FLOAT,
		DOUBLE
	}

	private final Deque<Level> stack = new LinkedList<Level>();

	private Level topLevel;

	private MsgPackSink sink;

	private State state = State.CLEAN;

	private long remainingLength;

	private long accumulator;

	private boolean nextByteIsSign;

	public MsgPackDecoder(MsgPackSink sink) {
		this.sink = sink;
	}

	public MsgPackSink getSink() {
		return sink;
	}

	public void setSink(MsgPackSink sink) {
		this.sink = sink;
	}

	public void pushBytes(byte[] bytes) throws IOException {
		pushBytes(bytes, 0, bytes.length);
	}

	public void pushBytes(byte[] bytes, int offset, int length) throws IOException {
		int end = offset + length;
		for(; offset < end; ++offset) {
			byte b = bytes[offset];
			switch(state) {
				case CLEAN:
					if(b >= 0) {
						// positive fixint
						sink.integer((long)b, true);
						break;
					}
					switch(b & 0xE0) {
						case 0x80:
							// 100xxxxx => fixmap, fixarray
							//TODO
							break;
						case 0xA0:
							// 101xxxxx => fixstr
							//TODO
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
									break;
								case 0xC1:
									// (never used)
									throw new ReservedInitiatorByteUsedException();
								case 0xC2:
									// false
									sink.bool(false);
									break;
								case 0xC3:
									// true
									sink.bool(true);
									break;
								case 0xC4:
									// bin 8
									//TODO
									break;
								case 0xC5:
									// bin 16
									//TODO
									break;
								case 0xC6:
									// bin 32
									//TODO
									break;
								case 0xC7:
									// ext 8
									//TODO
									break;
								case 0xC8:
									// ext 16
									//TODO
									break;
								case 0xC9:
									// ext 32
									//TODO
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
									//TODO
									break;
								case 0xD5:
									// fixext 2
									//TODO
									break;
								case 0xD6:
									// fixext 4
									//TODO
									break;
								case 0xD7:
									// fixext 8
									//TODO
									break;
								case 0xD8:
									// fixext 16
									//TODO
									break;
								case 0xD9:
									// str 8
									//TODO
								case 0xDA:
									// str 16
									//TODO
									break;
								case 0xDB:
									// str 32
									//TODO
									break;
								case 0xDC:
									// array 16
									//TODO
									break;
								case 0xDD:
									// array 32
									//TODO
									break;
								case 0xDE:
									// map 16
									//TODO
									break;
								case 0xDF:
									// map 32
									//TODO
									break;
								default:
									throw new Doom("Bit twiddling error");
							}
							break;
						case 0xE0:
							// 111xxxxx => negative fixint
							sink.integer((long)b, true);
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
					}
					break;
				case UINT:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.integer(accumulator, false);
					}
					break;
				case FLOAT:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.fraction(Float.intBitsToFloat((int)accumulator));
					}
					break;
				case DOUBLE:
					accumulator = (accumulator << 8) | ((long)b & 0xFFl);
					if(--remainingLength == 0l) {
						state = State.CLEAN;
						sink.fraction(Double.longBitsToDouble(accumulator));
					}
					break;
				default:
					throw new Doom("Unrecognized state: " + state.name());
			}
		}
	}

}
