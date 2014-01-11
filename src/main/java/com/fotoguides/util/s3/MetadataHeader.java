package com.fotoguides.util.s3;

/**
 * Represents a metadata header to be added to a file that will be uploaded to S3.
 * 
 * Not all content headers are accepted by AmazonS3. See AWS documentation for specifics about this.
 */
final class MetadataHeader {

	// these fields can't be final to allow for json deserialization
	private String key;
	
	private String value;

	// this constructor must be public to allow for json deserialization
	MetadataHeader() {
	}
	
	MetadataHeader(String key, String value) {
		this.key = key;
		this.value = value;
	}

	/**
	 * Gets the key of the metadata header.
	 *
	 * @return the key of the metadata header
	 */
	public String getKey() {
		return key;
	}
	
	/**
	 * Gets the value of the metadata header.
	 *
	 * @return the value of the metadata header
	 */
	public String getValue() {
		return value;
	}

}
