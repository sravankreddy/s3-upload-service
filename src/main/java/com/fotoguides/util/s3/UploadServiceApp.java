package com.fotoguides.util.s3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.apache.log4j.Logger;


/**
 * UploadServiceApp is the main class for the S3UploadService. Its main responsibilities are to:
 * 	1) load the application configuration from a configuration file
 * 	2) parse the command line arguments, making appropriate changes to the application configuration
 * 	3) configure JMX for the service
 * 	4) set up the service to run as a daemon (background process) if configuration dictates it
 * 	5) start the UploadService
 * 	6) add a shutdown hook to properly release resources upon service shutdown
 */
final class UploadServiceApp {

	/**
	 * The UploadService object that is managed by this service app
	 */
	static Service uploadService;

	private static final Logger log = Logger.getLogger(UploadServiceApp.class.getName());

	private static final String APP_NAME = "s3uploadservice";
	
	private static final String EXIT_MESSAGE = "Exiting now.";
	
	private static Thread uploadServiceThread;
		
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception the exception
	 */
	public static void main(String[] args) throws Exception {
		
		// load the application's configuration info into AppConfig
		try {
			// read application configuration from file, map it to an AppConfig object
			//AppConfig app = mapper.readValue(new File("config.json"), AppConfig.class);
			if (!AppConfig.load()) {
				log.error("Configuration file loading failed.  Reason: the configuration file 'config.json' was not found external to the jar file (in the same folder), or in the jar file (in the class path root).");
				log.info(EXIT_MESSAGE);
				return;				
			}
			
		} catch (IOException e) {
			log.error("Configuration file loading failed.  Reason: " + e.getMessage() );
			log.info(EXIT_MESSAGE);
			return;
		}
		
		
		List<PathUploadItem> pathUploadItems = AppConfig.getInstance().getPathUploadItems();
		
		if (pathUploadItems == null || pathUploadItems.size() == 0) {
			log.error("Configuration file loading failed.  Reason: at least one pathUploadItem must be configured." );
			log.info(EXIT_MESSAGE);
			return;			
		}
		
		
		// parse command line options
		if (!parseCommandLine(args)) {
			return;						 
		}
		
		
		// validate configuration
		if (AppConfig.getInstance().getCorePoolSize() > AppConfig.getInstance().getMaximumPoolSize()) {
			log.error("The service options are not valid.  Reason: the \"maximumpoolsize\" option must be greater than or equal to the \"corePoolSize\" option." );
			log.info(EXIT_MESSAGE);
			return;						
		}
				
		
		if (!processPathUploadItems(pathUploadItems)) {
			log.error("Configuration file loading failed.  Reason: at least one pathUploadItem is not configured properly. See error listed above." );			
			log.info(EXIT_MESSAGE);
			return;
		}


		// configure JMX
		// get the MBean server
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		// register the MBean
		ObjectName name = new ObjectName("com.fotoguides.util.s3.s3uploadservice:type=AppManagement");
		mbs.registerMBean(AppManagement.getInstance(), name);

		
		// if the service is configured to run as a daemon
		if (AppConfig.getInstance().isRunAsDaemon()) {
			try {
				System.out.close();
				System.err.close();
			}
			catch (Exception e) {
				log.error("The service could not be configured to run as a daemon.  Reason: " + e.getMessage());
				log.info(EXIT_MESSAGE);
				return;
			}
		}


		// setup the UploadService
		uploadService = new UploadService();
		
		uploadServiceThread = new Thread(new Runnable(){
			@Override
			public void run() {
				uploadService.start();
			}
			
		}, "Upload Service");
		
		uploadServiceThread.start();

		
		// add a shutdown hook to shutdown the UploadService appropriately upon service shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {				
				log.info("Shutting down upload service...");
				
				uploadServiceThread.interrupt();
				uploadService.shutdown();
				
				log.info(EXIT_MESSAGE);
			}
		});
				
		
	}
	
	/**
	 * Parses the command line.
	 *
	 * @param args the args
	 * @return true, if successful
	 */
	private static boolean parseCommandLine(String[] args) {
		AppConfig appConfig = AppConfig.getInstance();
		
		// options specified on the command line take precedence over options specified in the configuration file
		// create the command line parser
		CommandLineParser parser = new BasicParser();

		// create the command line options
		Options options = new Options();
		options.addOption("h", "help", false, "Print this help message");
		options.addOption("d", "daemon", false, "Run this application as a daemon (optional, default: do not run this application as a daemon)");
		options.addOption("cp", "corepoolsize", true, "The base number of worker threads to use in uploading files (optional, default: 1)");
		options.addOption("mp", "maximumpoolsize", true, "The maximum number of worker threads to use in uploading files (optional, default: 3)");
		options.addOption("qc", "queuecapacity", true, "The maximum number of upload tasks to allow in the task queue (optional, default: 3)");
		options.addOption("ct", "connectiontimeout", true, "The timeout for creating a new connection in milliseconds (optional, default: 50,000)");
		options.addOption("st", "sockettimeout", true, "The timeout for reading from a connected socket in milliseconds (optional, default: 120,000)");
		
		try {
			
			// parse the command line arguments
			CommandLine commandLine = parser.parse( options, args );

			if (commandLine.hasOption("h")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( APP_NAME, options );
				return false;
			}

			if (commandLine.hasOption("d")) {
				appConfig.setRunAsDaemon(true);
			}

			// corePoolSize is the base number of worker threads to use in uploading files - optional 
			if(commandLine.hasOption("cp")) {
				try {
					appConfig.setCorePoolSize(Integer.parseInt(commandLine.getOptionValue("cp")));
				}
				catch( NumberFormatException ex ) {
					log.error("Command line parsing failed. The \"corePoolSize\" option must be a number.");
					return false;
				}
			}

			// maximumpoolsize is the maximum number of worker threads to use in uploading files - optional 
			if(commandLine.hasOption("mp")) {
				try {
					appConfig.setMaximumPoolSize(Integer.parseInt(commandLine.getOptionValue("mp")));
				}
				catch( NumberFormatException ex ) {
					log.error("Command line parsing failed. The \"maximumpoolsize\" option must be a number.");
					return false;
				}
			}

			// queueCapacity is the capacity of the queue to use in uploading files - optional 
			if(commandLine.hasOption("qc")) {
				try {
					appConfig.setQueueCapacity(Integer.parseInt(commandLine.getOptionValue("qc")));
				}
				catch( NumberFormatException ex ) {
					log.error("Command line parsing failed. The \"queuecapacity\" option must be a number.");
					return false;
				}
			}

			// connectionTimeout is the timeout for creating a new connection in milliseconds - optional
			if(commandLine.hasOption("ct")) {
				try {
					appConfig.setConnectionTimeout(Integer.parseInt(commandLine.getOptionValue("ct")));
				}
				catch( NumberFormatException ex ) {
					log.error("Command line parsing failed. The \"connectiontimeout\" option must be a number.");
					return false;
				}
				
			}

			// socketTimeout is the timeout for reading from a connected socket in milliseconds - optional
			if(commandLine.hasOption("st")) {
				try {
					appConfig.setSocketTimeout(Integer.parseInt(commandLine.getOptionValue("st")));
				}
				catch( NumberFormatException ex ) {
					log.error("Command line parsing failed. The \"sockettimeout\" option must be a number.");
					return false;
				}
				
			}
		}
		catch( ParseException ex ) {
			log.error("Command line parsing failed.  Reason: " + ex.getMessage() );
			return false;
		}
		
		return true;
	}
	
	/**
	 * Process path upload items.
	 *
	 * @param pathUploadItems the path upload items
	 * @return true, if successful
	 */
	private static boolean processPathUploadItems(List<PathUploadItem> pathUploadItems) {
		
		for (PathUploadItem pathUploadItem : pathUploadItems) {
			Path localPath = Paths.get(pathUploadItem.getLocalPath());
	
			// if files are not deleted after upload, they must be moved to an uploaded sub-directory
			if (!AppConfig.getInstance().isDeleteAfterUpload()) {
				try {
					// create uploaded directory and its parent directories if they don't exist
					Files.createDirectories(localPath.resolve(Subfolder.UPLOADED.toString()));
				} catch (IOException e) {
					// exception not generated if folder could not be created because it already exists
					log.error(String.format("Error creating directory: %s. %s", 
							localPath.resolve(Subfolder.UPLOADED.toString()).toString(), e.getMessage()));
					
					return false;
				}				
			}

			try {
				// create failed directory and parent directories if they don't exist
				Files.createDirectories(localPath.resolve(Subfolder.FAILED.toString()));
			} catch (IOException e) {
				log.error(String.format("Error creating directory: %s. %s", 
						localPath.resolve(Subfolder.FAILED.toString()).toString(), e.getMessage()));
				
				return false;
			}			   

		}
		
		return true;
		
	}
	
}
