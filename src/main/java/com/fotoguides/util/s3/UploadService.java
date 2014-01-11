package com.fotoguides.util.s3;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import org.apache.log4j.Logger;


/**
 * UploadService is responsible for managing the upload of files to AmazonS3. The class uses the AmazonS3 client
 * 	to upload files from the folders specified in the application configuration's pathUploadItems.
 * 
 * The class creates a ThreadPoolExecutor based on thread pool options in the application configuration and also
 * 	creates decorating ListeningExecutorService so it receives notifications upon upload success or failure.
 * 
 * A Semaphore is used to control the maximum number of upload tasks managed by the service at one time. Managed 
 * 	upload tasks are those actively being uploaded plus those in the queue waiting to be uploaded. The capacity of 
 * 	the UploadService is the maxPoolSize + queueCapacity which are both application configuration options. The 
 * 	UploadService blocks when it is at its set capacity, and does not search for more files to upload until it 
 * 	once again drops below that level.
 * 
 * A ConcurrentHashMap activeFileHashmap is used to track the upload tasks currently being managed by the UploadService.
 * 	This includes files currently being uploaded and upload tasks waiting in the queue. This prevents the same file 
 * 	from being processed more than one time, and allows the service to search the folders for new files while existing
 * 	files are being managed by the UploadService. 
 */
final class UploadService implements Service {

	/**
	 * The sole AmazonS3 client which is shared throughout the application.
	 */
	static AmazonS3 s3Client;

	private static final Logger log = Logger.getLogger(UploadService.class.getName());

	private static final int PAUSE_INTERVAL = 2000;
	
	/**
	 * The total number of files uploaded since the service started.
	 */
	final AtomicLong totalFilesUploaded = new AtomicLong(0);
	
	/**
	 * The total number of file uploads that failed since the service started.
	 */
	final AtomicLong totalFilesFailed = new AtomicLong(0);

	/**
	 * The total number of bytes uploaded since the service started.
	 */
	final AtomicLong totalBytesTransferred = new AtomicLong(0);

	private volatile boolean cancelled;

	private ExecutorService executorService;
	private ListeningExecutorService listeningExecutorService;

	private Semaphore semaphore;
	private int bound;

	private final ConcurrentHashMap<String, Boolean> activeFileHashmap = new ConcurrentHashMap<String, Boolean>();


	/**
	 * This initiates service startup
	 */
	@Override
	public void start() {
		log.info("Starting UploadService");

		AppConfig appConfig = AppConfig.getInstance();

		// create the semaphore, setting the cap size for the number of tasks the service will 
		//	manage (active tasks + tasks in queue)
		bound = appConfig.getMaximumPoolSize() + appConfig.getQueueCapacity();
		semaphore = new Semaphore(bound);

		// configure AWS credentials based on properties file
		AWSCredentials credentials;
		try {
			credentials = new PropertiesCredentials(UploadCallable.class.getResourceAsStream("/AwsCredentials.properties"));

			ClientConfiguration clientConfiguration = new ClientConfiguration();
			// the default protocol is HTTPS, but we'll be explicit about it just in case
			clientConfiguration.setProtocol(Protocol.HTTPS);
			clientConfiguration.setMaxConnections(appConfig.getMaximumPoolSize());
			clientConfiguration.setConnectionTimeout(appConfig.getConnectionTimeout());
			clientConfiguration.setSocketTimeout(appConfig.getSocketTimeout());

			s3Client = new AmazonS3Client(credentials, clientConfiguration);	 

		} catch (IOException e) {
			e.printStackTrace();
		}

		// create and configure the thread pool
		executorService = new ThreadPoolExecutor(
				appConfig.getCorePoolSize(),
				appConfig.getMaximumPoolSize(),
				appConfig.getKeepAliveTime(),
				appConfig.getKeepAliveTimeUnit(),
				new ArrayBlockingQueue<Runnable>(appConfig.getQueueCapacity(), true),
				new ThreadPoolExecutor.CallerRunsPolicy());

		// use Google Guava ListeningExecutorService so we know when the tasks have been completed
		listeningExecutorService = MoreExecutors.listeningDecorator(executorService);


		// process the configured PathUploadItems
		while (!cancelled) {
			processPathUploadItems();

			// completed the full iteration of upload path items, let other things happen before 
			//	checking the upload path items again. Prevents continuously searching an empty directory
			try {
				Thread.sleep(PAUSE_INTERVAL);
			} catch (InterruptedException ignored) { } 

		}

	}
	
	/**
	 * This initiates service shutdown
	 */
	@Override
	public void shutdown() {
		cancelled = true;

		if (s3Client instanceof AmazonS3Client) {
			log.info("Must wait for S3Client to shutdown...");
			((AmazonS3Client)s3Client).shutdown();
		}

		executorService.shutdown();
	}

	/**
	 * Processes the configured pathUploadItems, uploading files in the specified folders. 
	 * 
	 * The pathUploadItems are iterated and for each pathUploadItem, a directory stream is opened for that local path.
	 * 	The directory stream is iterated and files are submitted to the thread pool for uploading.
	 */
	private void processPathUploadItems() {
		for (PathUploadItem pathUploadItem : AppConfig.getInstance().getPathUploadItems()) {

			int index = 0;

			Path folderPath = Paths.get(pathUploadItem.getLocalPath());			 

			try (DirectoryStream<Path> directoryStream = getDirectoryStream(folderPath, pathUploadItem.getGlobPattern())) {

				for (Path path : directoryStream) {

					if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ||
							Files.isHidden(path) ||
							!Files.isReadable(path)) {

						continue;
					}

					index++;

					final String filePathKey = path.toAbsolutePath().toString();						

					if (activeFileHashmap.putIfAbsent(filePathKey, false) == null) {
						// if the filePathKey was absent and was added
						log.debug("Added filePathKey: " + filePathKey);

						try {
							semaphore.acquire();

							ListenableFuture<CompletedUploadInfo> futureTask = listeningExecutorService.submit(
									new UploadCallable(pathUploadItem.getS3BucketName(), 
											pathUploadItem.getS3ObjectKeyRoot(), 
											pathUploadItem.getMetadataHeaders(),
											path,
											filePathKey));

							Futures.addCallback(futureTask, new FutureCallback<CompletedUploadInfo>(){
								@Override
								public void onFailure(Throwable arg0) {
									log.error("onFailure for file: " + filePathKey);

									activeFileHashmap.remove(filePathKey);

									// log.debug(String.format("activeFileHashmap.size(): %d",activeFileHashmap.size()));

									totalFilesFailed.incrementAndGet();

									semaphore.release();
								}

								@Override
								public void onSuccess(CompletedUploadInfo completedUploadInfo) {
									log.info("onSuccess for file: " + completedUploadInfo.getFilePathKey());

									activeFileHashmap.remove(completedUploadInfo.getFilePathKey());

									// log.debug(String.format("activeFileHashmap.size(): %d",activeFileHashmap.size()));

									totalFilesUploaded.incrementAndGet();
									totalBytesTransferred.addAndGet(completedUploadInfo.getBytesTransferred());

									semaphore.release();
								}

							});

							ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)executorService;
							int activeCount = threadPoolExecutor.getActiveCount();
							int queueSize = threadPoolExecutor.getQueue().size();

							log.debug(String.format("Submitted '%s', file#: %d, in queue: %d, active: %d", 
									path.getFileName().toString(), index, queueSize, activeCount));

						} catch (InterruptedException ignored) {
							cancelled = true;
						}

					}


					if (cancelled) break;

				}

			} catch (DirectoryIteratorException e) {
				log.error("Directory stream iteration DirectoryIteratorException: " + e.getMessage());	 

				if (e.getCause() != null) {
					log.error("Directory stream iteration DirectoryIteratorException/IOException: " + e.getCause().getMessage());
				}

			} catch (IOException e) {
				log.error("Directory stream iteration IOException: " + e.getMessage());
			}

			//			  log.debug("Completed path : directoryStream iteration.");

			if (cancelled) break;

		}
		
	}
	
	
	/**
	 * Gets the directory stream.
	 *
	 * @param folderPath the local path which will be monitored for files to be uploaded
	 * @param globPattern the glob pattern which is used to match the files in the local path to upload
	 * @return the directory stream
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static DirectoryStream<Path> getDirectoryStream(Path folderPath, String globPattern) throws IOException {

		if (globPattern == null || globPattern.isEmpty()) {
			return Files.newDirectoryStream(folderPath);				
		}
		else {
			return Files.newDirectoryStream(folderPath, globPattern);
		}
	}


	/**
	 * Gets the number of active file uploads.
	 *
	 * @return the number of active file uploads
	 */
	int getActiveTaskCount() {
		ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)executorService;
		return threadPoolExecutor.getActiveCount();
	}

	/**
	 * Gets the queue size of files waiting to begin uploading.
	 *
	 * @return the queue size of files waiting to begin uploading
	 */
	int getQueueSize() {
		ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)executorService;
		return threadPoolExecutor.getQueue().size();		
	}

}
