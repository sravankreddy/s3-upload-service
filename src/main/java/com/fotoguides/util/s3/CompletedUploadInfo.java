package com.fotoguides.util.s3;

/**
* <p>
* Provides information about a completed upload task.
* </p>
*/
final class CompletedUploadInfo {

	private final String filePathKey;	
	private final long bytesTransferred;
	
	/**
	 * Instantiates a new completed upload info.
	 *
	 * @param filePathKey the unique path of this file that was uploaded
	 * @param bytesTransferred the bytes transferred in uploading this file
	 */
	public CompletedUploadInfo(String filePathKey, long bytesTransferred) {
		this.filePathKey = filePathKey;
		this.bytesTransferred = bytesTransferred;
	}
	
	/**
	 * Gets the unique path of this file that was uploaded.
	 *
	 * @return the the unique path of a file that was uploaded
	 */
	public String getFilePathKey() {
		return filePathKey;
	}
	
	/**
	 * Gets the bytes transferred in uploading this file.
	 *
	 * @return the bytes transferred in uploading this file
	 */
	public long getBytesTransferred() {
		return bytesTransferred;
	}
	
	
}
