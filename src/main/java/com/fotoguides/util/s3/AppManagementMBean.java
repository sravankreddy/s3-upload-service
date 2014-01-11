package com.fotoguides.util.s3;

/**
 * <p>
 * The MBean interface providing live application statistics
 * </p>.
 */
public interface AppManagementMBean {
	
	/**
	 * Gets the total number of files uploaded since the service started.
	 *
	 * @return the total number of files uploaded
	 */
	public long getFilesUploaded();

	/**
	 * Gets the total number of bytes uploaded since the service started.
	 *
	 * @return the total number of bytes uploaded
	 */
	public long getBytesUploaded();

	/**
	 * Gets the total number of file uploads that failed since the service started.
	 *
	 * @return the total number of file uploads that failed
	 */
	public long getFilesFailed();

	/**
	 * Gets the number of active file uploads.
	 *
	 * @return the number of active file uploads
	 */
	public int getActiveTaskCount();
	
	/**
	 * Gets the queue size of files waiting to begin uploading.
	 *
	 * @return the queue size of files waiting to begin uploading
	 */
	public int getQueueSize();

}
