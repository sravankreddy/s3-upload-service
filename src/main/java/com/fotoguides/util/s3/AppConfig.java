package com.fotoguides.util.s3;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
* <p>
* Configuration options for the service
* </p>
* <p>
* The configuration info is loaded from a configuration file and updated from the command line options.  
* </p>
*/
final class AppConfig {
	
	private static AppConfig instance;
	
	private static final boolean RUN_AS_DAEMON = false;
	private static final boolean DELETE_AFTER_UPLOAD = true;	

	private static final int CORE_POOL_SIZE = 1;
	private static final int MAXIMUM_POOL_SIZE = 3;
	private static final int QUEUE_CAPACITY = 3;
	private static final long KEEP_ALIVE_TIME = 1;
	private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MINUTES;
	
	private static final int CONNECTION_TIMEOUT = 50000;
	private static final int SOCKET_TIMEOUT = 120000;
	
	
	// configuration fields with default values, can be set on the command line
	private boolean runAsDaemon = RUN_AS_DAEMON;
	
	
	// configuration fields with default values, can be set in the configuration file or on the command line
	private boolean deleteAfterUpload = DELETE_AFTER_UPLOAD;
	
	private int corePoolSize = CORE_POOL_SIZE;
	private int maximumPoolSize = MAXIMUM_POOL_SIZE;
	private int queueCapacity = QUEUE_CAPACITY;
	
	private int connectionTimeout = CONNECTION_TIMEOUT;	
	private int socketTimeout = SOCKET_TIMEOUT;
	
	
	// configuration fields with default values, can't be set on the command line or in the configuration file
	private long keepAliveTime = KEEP_ALIVE_TIME;
	private TimeUnit keepAliveTimeUnit = KEEP_ALIVE_TIME_UNIT;

	
	// required to be set in the configuration file, there is no default value
	private List<PathUploadItem> pathUploadItems;


	/**
	 * Instantiates a new app config.
	 * 
	 * this constructor must be public to allow for json deserialization
	 */
	public AppConfig() {}
	

	
	/**
	 * Load.
	 *
	 * @return true, if successful
	 * @throws JsonParseException 
	 * @throws JsonMappingException 
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	static boolean load() throws JsonParseException, JsonMappingException, IOException {
		// setup the Jackson ObjectMapper to read the application's Json configuration file
		ObjectMapper mapper = new ObjectMapper();

		// allow comments in the Json configuration file
		mapper.configure(Feature.ALLOW_COMMENTS, true);

		// first look for external config file
		Path configFilePath = Paths.get("config.json");
		
		if (Files.exists(configFilePath, LinkOption.NOFOLLOW_LINKS)) {
			instance = mapper.readValue(configFilePath.toFile(), AppConfig.class);
		}
		else {
			// next, look for config file in class path
			URL configFileUrl = AppConfig.class.getResource("/config.json");
			
			if (configFileUrl != null) {
				instance = mapper.readValue(configFileUrl, AppConfig.class);
			}
			else {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Gets the single instance of AppConfig.
	 *
	 * @return single instance of AppConfig
	 */
	static AppConfig getInstance() {
		return instance;
	}

	
	/**
	 * Checks  whether or not to delete files from the local path after they have been successfully uploaded to 
	 * 	Amazon S3. If this is set to false, uploaded files are not deleted and are instead moved to the "uploaded" 
	 * 	subfolder of the local path folder.
	 *
	 * @return true, if the application will delete files are they are successfully uploaded
	 */
	boolean isDeleteAfterUpload() {
		return deleteAfterUpload;
	}

	/**
	 * Sets the application to either delete files are they are successfully uploaded or to move them to the "uploaded"
	 * 	subfolder.
	 *
	 * @param deleteAfterUpload whether or not the application should delete files are they are successfully uploaded
	 */
	void setDeleteAfterUpload(boolean deleteAfterUpload) {
		this.deleteAfterUpload = deleteAfterUpload;
	}
	
	/**
	 * Checks if the application should run as a daemon (background process).
	 *
	 * @return true, if the application is configured to run as a daemon
	 */
	boolean isRunAsDaemon() {
		return runAsDaemon;
	}

	/**
	 * Sets the application to run as a daemon (background process). Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param runAsDaemon whether or not the application should run as a daemon.
	 */
	void setRunAsDaemon(boolean runAsDaemon) {
		this.runAsDaemon = runAsDaemon;
	}

	
	/**
	 * Gets the base number of worker threads to use in uploading files.
	 *
	 * @return the base number of worker threads to use in uploading files
	 */
	int getCorePoolSize() {
		return corePoolSize;
	}

	/**
	 * Sets the base number of worker threads to use in uploading files. Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param corePoolSize the base number of worker threads to use in uploading files
	 */
	void setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	/**
	 * Gets the maximum number of worker threads to use in uploading files.
	 *
	 * @return the maximum number of worker threads to use in uploading files
	 */
	int getMaximumPoolSize() {
		return maximumPoolSize;
	}

	/**
	 * Sets the maximum number of worker threads to use in uploading files. Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param maximumPoolSize the maximum number of worker threads to use in uploading files
	 */
	void setMaximumPoolSize(int maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}

	/**
	 * Gets the maximum number of upload tasks to allow in the task queue.
	 *
	 * @return the maximum number of upload tasks to allow in the task queue
	 */
	int getQueueCapacity() {
		return queueCapacity;
	}

	/**
	 * Sets the maximum number of upload tasks to allow in the task queue. Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param queueCapacity the maximum number of upload tasks to allow in the task queue
	 */
	void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}

	/**
	 * When the number of threads is greater than the corePoolSize, this is the maximum time that excess idle threads 
	 * 	will wait for new tasks before terminating.
	 *
	 * @return the keep alive time
	 */
	long getKeepAliveTime() {
		return keepAliveTime;
	}

	/**
	 * Gets the time unit for the keepAliveTime argument.
	 *
	 * @return the keep alive time unit
	 */
	TimeUnit getKeepAliveTimeUnit() {
		return keepAliveTimeUnit;
	}
	
	/**
	 * Gets the timeout for creating a new connection in milliseconds.
	 *
	 * @return the timeout for creating a new connection in milliseconds
	 */
	int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * Sets the timeout for creating a new connection in milliseconds. Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param connectionTimeout the timeout for creating a new connection in milliseconds
	 */
	void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	/**
	 * Gets the timeout for reading from a connected socket in milliseconds.
	 *
	 * @return the timeout for reading from a connected socket in milliseconds
	 */
	int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * Sets the timeout for reading from a connected socket in milliseconds. Changing this value will have no effect 
	 * 	after application startup.
	 *
	 * @param socketTimeout the timeout for reading from a connected socket in milliseconds
	 */
	void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}
	

	/**
	 * Gets the path upload items. Each path upload item represents a local folder to monitor and to upload files from.
	 *
	 * @return the path upload items
	 */
	List<PathUploadItem> getPathUploadItems() {
		return pathUploadItems;
	}

	/**
	 * Sets the path upload items. This value should not be changed after application startup.
	 *
	 * @param pathUploadItems the new path upload items
	 */
	void setPathUploadItems(List<PathUploadItem> pathUploadItems) {
		this.pathUploadItems = pathUploadItems;
	}

}
