package com.fotoguides.util.s3;

/**
 * <p>
 * Provides live application statistics via JMX
 * </p>.
 */
public final class AppManagement implements AppManagementMBean {

	private static final AppManagement instance = new AppManagement();
	
	private AppManagement() {
		
	}
	
	/**
	 * Gets the single instance of AppManagement.
	 *
	 * @return single instance of AppManagement
	 */
	static AppManagement getInstance() {
		return instance;
	}
	
	/**
	 * Gets the total number of files uploaded since the service started.
	 *
	 * @return the total number of files uploaded
	 */
	@Override
	public long getFilesUploaded() {
		return ((UploadService)UploadServiceApp.uploadService).totalFilesUploaded.get();		
	}

	/**
	 * Gets the total number of bytes uploaded since the service started.
	 *
	 * @return the total number of bytes uploaded
	 */
	@Override
	public long getBytesUploaded() {
		return ((UploadService)UploadServiceApp.uploadService).totalBytesTransferred.get();
	}

	/**
	 * Gets the total number of file uploads that failed since the service started.
	 *
	 * @return the total number of file uploads that failed
	 */
	@Override
	public long getFilesFailed() {
		return ((UploadService)UploadServiceApp.uploadService).totalFilesFailed.get();		
	}

	/**
	 * Gets the number of active file uploads.
	 *
	 * @return the number of active file uploads
	 */
	@Override
	public int getActiveTaskCount(){
		return ((UploadService)UploadServiceApp.uploadService).getActiveTaskCount();
	}

	/**
	 * Gets the queue size of files waiting to begin uploading.
	 *
	 * @return the queue size of files waiting to begin uploading
	 */
	@Override
	public int getQueueSize(){
		return ((UploadService)UploadServiceApp.uploadService).getQueueSize();
	}

}
