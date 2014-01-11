package com.fotoguides.util.s3;

/**
 * The Interface Service which represents an object with methods to start and to shutdown.
 */
public interface Service {
	
	/**
	 * This initiates service startup
	 */
	public void start();
	
	/**
	 * This initiates service shutdown
	 */
	public void shutdown();
	
}
