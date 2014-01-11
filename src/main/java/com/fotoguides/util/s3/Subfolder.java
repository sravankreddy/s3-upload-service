package com.fotoguides.util.s3;

/**
 * Represents a subfolder that is used to move processed files into
 */
enum Subfolder {
	
	/** The "uploaded" subfolder. */
	UPLOADED("uploaded"), 
	
	/** The "failed" subfolder. */
	FAILED("failed");
	
	/** The value. */
	private String value;
	
	private Subfolder(String value) {
		this.value = value;
	}
	
	/**
	 * Gets the value of the enum which is the folder name.
	 *
	 * @return the value of the enum which is the folder name
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * Returns the value of the enum which is the folder name.
	 *
	 * @return the value of the enum which is the folder name
	 */
	@Override 
	public String toString() {
		return value;
	}
	
	/**
	 * Returns the subfolder enum which matches the value parameter supplied
	 *
	 * @param value the string value of the subfolder
	 * @return the subfolder enum which matches the value parameter supplied
	 */
	static Subfolder fromValue(String value) {
		
		if (value != null && !value.isEmpty()) {		
			for (Subfolder subfolderType : values()) {
				if (subfolderType.equals(value)) {
					return subfolderType;
				}
			}
		}
		
		return null;
	}
	
}
