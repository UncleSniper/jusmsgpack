package org.unclesniper.msgpack;

public class Doom extends Error {

	public Doom(String message) {
		super("Programmer fsck(8)ed up: " + message);
	}

}
