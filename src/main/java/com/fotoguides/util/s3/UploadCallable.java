package com.fotoguides.util.s3;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;

import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GroupGrantee;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

import org.apache.log4j.Logger;


/**
 * Represents an upload task which returns CompletedUploadInfo upon completion.
 */
final class UploadCallable implements Callable<CompletedUploadInfo> {

	private static final Logger log = Logger.getLogger(UploadCallable.class.getName());

	private static final String S3_PATH_SEPARATOR = "/";
	
	private final Path path;
	private final String filePathKey;
	
	private final String s3BucketName;
	private final String s3ObjectKeyRoot;
	private final List<MetadataHeader> metadataHeaders;
	
	/**
	 * Instantiates a new upload callable.
	 *
	 * @param s3BucketName the name of the S3 bucket to upload this file to
	 * @param s3ObjectKeyRoot the folder in the S3 bucket to upload this file to. Leave this empty to upload this file to the root of the S3 bucket
	 * @param metadataHeaders the metadata headers to set for the file to upload
	 * @param path the path object of the file to upload
	 * @param filePathKey the unique and complete path of this file
	 */
	UploadCallable(String s3BucketName, String s3ObjectKeyRoot, List<MetadataHeader> metadataHeaders, 
			Path path, String filePathKey) {
		this.s3BucketName = s3BucketName; 
		this.s3ObjectKeyRoot = s3ObjectKeyRoot;
		this.metadataHeaders = metadataHeaders;
		this.path = path;
		this.filePathKey = filePathKey;
	}
	
	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public CompletedUploadInfo call() throws Exception {		
		AccessControlList acl = new AccessControlList();
		acl.grantPermission(GroupGrantee.AllUsers, Permission.Read);
		
		// set the configured metadata headers
		ObjectMetadata metadata = new ObjectMetadata();
		for (MetadataHeader metadataHeader : metadataHeaders) {
			metadata.setHeader(metadataHeader.getKey(), metadataHeader.getValue());
		}
		
		String s3ObjectKey = getS3ObjectKey(s3ObjectKeyRoot, path);
	
		File file = path.toFile();

		try {
			PutObjectResult result = UploadService.s3Client.putObject(
					new PutObjectRequest(s3BucketName, s3ObjectKey, file).withMetadata(metadata).withAccessControlList(acl));
		} catch(Exception ex) {
			log.error(ex.getMessage());
			
			// the upload has failed, so the file must be moved to the failed folder
			moveFileToSubfolder(filePathKey, Subfolder.FAILED.toString());
			
			throw ex;
		}

		// get length of file before it is deleted
		long bytesTransferred = file.length();
		
		log.debug(String.format("transferred '%s', bytes: %d, th: %d", filePathKey, bytesTransferred, Thread.currentThread().getId()));

		if (AppConfig.getInstance().isDeleteAfterUpload()) {
			// the upload has succeeded and the application is configured to delete files after upload,
			//	so the file must be deleted
			try {		   
				Files.delete(path);										 
			} catch (IOException e) {
				log.error(String.format("Error deleteing file: %s. %s", filePathKey, e.getMessage()));
			}										   
		}
		else {
			// the upload has succeeded and the application is configured to not delete files after upload, 
			//	so the file must be moved to the uploaded folder
			moveFileToSubfolder(filePathKey, Subfolder.UPLOADED.toString());
		}
				
		return new CompletedUploadInfo(filePathKey, bytesTransferred);
	}
	
	/**
	 * Gets the S3 object key based on the supplied s3ObjectKeyRoot and path object for the file.
	 *
	 * @param s3ObjectKeyRoot the folder in the S3 bucket to upload this file to. Leave this empty to upload this file to the root of the S3 bucket
	 * @param path the path object of the file to upload
	 * @return the S3 object key
	 */
	private static String getS3ObjectKey(String s3ObjectKeyRoot, Path path) {
		String s3ObjectKey;
		
		if (s3ObjectKeyRoot.length() == 0 || s3ObjectKeyRoot.equals(S3_PATH_SEPARATOR)) {
			s3ObjectKey = path.getFileName().toString();
		}
		else if (s3ObjectKeyRoot.endsWith(S3_PATH_SEPARATOR)) {
			s3ObjectKey = s3ObjectKeyRoot + path.getFileName().toString();			   
		}
		else {
			s3ObjectKey = s3ObjectKeyRoot + S3_PATH_SEPARATOR + path.getFileName().toString();
		}

		return s3ObjectKey;
	}
	
	/**
	 * Move file to subfolder.
	 *
	 * @param filePathKey the unique and complete path of this file
	 * @param subfolder the subfolder to move this file to
	 */
	private static void moveFileToSubfolder(String filePathKey, String subfolder) {
		Path filePath = Paths.get(filePathKey);

		try {
			Path newPath = Paths.get(filePath.getParent().toString(), 
					subfolder, 
					filePath.getFileName().toString());
			
			// move the file to the subfolder. If a file with the same name already exists
			//	in the subfolder then replace the existing file.
			Files.move(filePath, newPath, StandardCopyOption.REPLACE_EXISTING);
			
		} catch (IOException e) {
			log.error(String.format("Error moving file: %s to %s folder. %s", filePathKey, subfolder, e.getMessage()));
		}											
	}
	
}
