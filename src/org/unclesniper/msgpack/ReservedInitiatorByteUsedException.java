package org.unclesniper.msgpack;

public class ReservedInitiatorByteUsedException extends MsgPackWireFormatException {

	public ReservedInitiatorByteUsedException() {
		super("Reserved initiator byte 0xC1 found");
	}

}
