package com.fotoguides.util.s3;

import java.util.List;

/**
 * Provides information about a folder to upload files from.
 */
final class PathUploadItem {

	// these fields can't be final to allow for json deserialization
	private String localPath;	
	private String globPattern;
	private String s3BucketName;
	private String s3ObjectKeyRoot;
	private List<MetadataHeader> metadataHeaders;
	
	// this constructor must be public to allow for json deserialization
	PathUploadItem() {
	}
	
	/**
	 * Instantiates a new path upload item.
	 *
	 * @param localPath the local path which will be monitored for files to be uploaded
	 * @param globPattern the glob pattern which is used to match the files in the local path to upload
	 * @param s3BucketName the name of the S3 bucket to upload files to
	 * @param s3ObjectKeyRoot the folder in the S3 bucket to upload files to. Leave this empty to upload 
	 * 			files to the root of the S3 bucket.
	 */
	PathUploadItem(String localPath, String globPattern, String s3BucketName, String s3ObjectKeyRoot) {
		this.localPath = localPath;
		this.globPattern = globPattern;
		this.s3BucketName = s3BucketName;
		this.s3ObjectKeyRoot = s3ObjectKeyRoot;
	}
	
	/**
	 * Gets the local path which will be monitored for files to be uploaded.
	 *
	 * @return the local path which will be monitored for files to be uploaded
	 */
	public String getLocalPath() {
		return localPath;
	}
	
	/**
	 * Gets the glob pattern which is used to match the files in the local path to upload.
	 *
	 * @return the glob pattern which is used to match the files in the local path to upload
	 */
	public String getGlobPattern() {
		return globPattern;
	}
	
	/**
	 * Gets the name of the S3 bucket to upload files to.
	 *
	 * @return the name of the S3 bucket to upload files to
	 */
	public String getS3BucketName() {
		return s3BucketName;
	}
	
	/**
	 * Gets the folder in the S3 bucket to upload files to. If this is an empty string or null, files
	 *	will be uploaded to the root of the S3 bucket.
	 *
	 * @return the folder in the S3 bucket to upload files to. If this is an empty string or null, files
	 *	will be uploaded to the root of the S3 bucket
	 */
	public String getS3ObjectKeyRoot() {
		return s3ObjectKeyRoot;
	}
	
	/**
	 * Gets the metadata headers to set for each file uploaded from the local path.
	 *
	 * @return the metadata headers to set for each file uploaded from the local path
	 */
	public List<MetadataHeader> getMetadataHeaders() {
		return metadataHeaders;
	}
	
	
}
