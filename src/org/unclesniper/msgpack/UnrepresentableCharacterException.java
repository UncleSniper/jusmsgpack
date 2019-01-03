package org.unclesniper.msgpack;

public class UnrepresentableCharacterException extends StringEncodingException {

	private final int character;

	public UnrepresentableCharacterException(int character) {
		super("Character 0x" + Integer.toHexString(character).toUpperCase() + " cannot be represented in UTF-8");
		this.character = character;
	}

	public int getCharacter() {
		return character;
	}

}
