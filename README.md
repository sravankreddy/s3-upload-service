S3UploadService
======================

This Java service uploads files to Amazon S3. It can be run as a console application or as a background process. Upon startup, the service will upload all files in the configured file paths and will continue to upload all files that are added to the configured file paths while it is running. 

This service may be useful to you if:

1) You have one or more local folders into which files are dumped by another process, and you need to upload these files to an Amazon S3 bucket.

2) Your process dumps files into the root of each local folder, without creating any subfolders. This service does not support recursive processing of a folder. 

3) All the files from a local folder will be uploaded to a specific S3 bucket. There is a one to one mapping between a local folder and an Amazon S3 bucket.

4) You don't require client-side or server-side encryption for your files, as this service doesn't support that yet. Just to be clear, this doesn't impact the secure transit of files as the service uploads data to Amazon S3 using the HTTPS protocol.   


Configuration
---------------

This service manages a thread pool, which uploads files concurrently using the AmazonS3Client of the AWS Java SDK. The thread pool and upload service can be configured using the following parameters:

* corePoolSize - the base number of worker threads to use in uploading files 
* maximumPoolSize - the maximum number of worker threads to use in uploading files
* queueCapacity - the maximum number of upload tasks to allow in the task queue
* connectionTimeout - the timeout for creating a new connection in milliseconds
* socketTimeout - the timeout for reading from a connected socket in milliseconds
* deleteAfterUpload - whether or not to delete files from the local path after they have been successfully uploaded to Amazon S3. If this is set to false, uploaded files are not deleted and are instead moved to the "uploaded" subfolder of the local path folder. 

The service can also be configured to monitor one or more local upload paths, uploading all files from a path to a specific Amazon S3 bucket and folder. The parameters for each configured upload path are:

* localPath - the localPath which will be monitored for files to be uploaded
* globPattern - files in the local path matching the glob pattern will be uploaded
* s3BucketName - the name of the S3 bucket to upload files to
* s3ObjectKeyRoot - the folder in the S3 bucket to upload files to. Leave this empty to upload files to the root of the S3 bucket.
* metadataHeaders - the metadata headers to set for each file uploaded from the local path. 

The parameters for each metadata header are:

* key - the key of the metadata header, ex. "Cache-Control"
* value - the value of the metadata header, ex. "max-age=86400"

The service is configured using a JSON configuration file and also through the command line. The configuration file is a superset of JSON which also supports comments. The service can use a configuration file external to the jar file (in the same folder as the jar), or in the jar file (in the classpath). Upon startup the service will check for an external configuration file and then for the internal configuration file, using the first configuration file that it finds. The benefit of using the external configuration file is that it can be easily edited after the service has been deployed. Below is a sample of the configuration file:

```
{
    //pathUploadItems is required, one or more pathUploadItems can be specified
    "pathUploadItems": [
        {
            "localPath": "/usr/local/fileuploaddaemon",
            //on windows you must escape backslashes
            //"localPath": "C:\\services\\fileuploaddaemon",
            "globPattern": "*.{jpg,png,gif,ico}", 
            "s3BucketName": "your-unique-bucket-name",
            "s3ObjectKeyRoot": "/",
            "metadataHeaders": [
            	//not all types of headers will be accepted by Amazon S3
                {
                    "key": "Cache-Control",
                    "value": "max-age=86400"
                }
            ]
        }
    ],
    
    //options specified on the command line take precedence over options specified in the configuration file

    //corePoolSize is optional, default value: 1
    "corePoolSize": 2,
    
    //maximumPoolSize is optional, default value: 3
    "maximumPoolSize": 5,

    //queueCapacity is optional, default value: 3
    "queueCapacity": 5,

    //connectionTimeout is optional, default value: 50000
    "connectionTimeout": 30000,

    //socketTimeout is optional, default value: 120000
    "socketTimeout": 130000,

    //deleteAfterUpload is optional, default value: true
    "deleteAfterUpload": true    

}
```



Command Line Usage
---------------------

*-cp,--corepoolsize \<arg\>* - the base number of worker threads to use in uploading files (optional, default: 1)  
  
*-ct,--connectiontimeout \<arg\>* - the timeout for creating a new connection in milliseconds (optional, default: 50,000)  

*-d,--daemon* - run this application as a daemon (optional, default: do not run this application as a daemon)  
  
*-h,--help* - print this help message  
  
*-mp,--maximumpoolsize \<arg\>* - the max number of worker threads to use in uploading files (optional, default: 3)  
  
*-qc,--queuecapacity \<arg\>* - the capacity of the queue to use for uploading files (optional, default: 3)  
  
*-st,--sockettimeout \<arg\>* - the timeout for reading from a connected socket in milliseconds (optional, default: 120,000)


example command line usage:  
```'java -jar s3uploadservice.jar -cp 3 -mp 3 -ct 300000 -st 600000 -qc 8'```



Getting Started
----------------

* You must have an AWS account, and you must sign up for the S3 web service. 

* Enter your AWS credentials in the AwsCredentials.properties file, located in the class path.

* Setup at least one uploadPathItem in the config.json configuration file, located in the classpath.  

* Enter your log file path in the log4j.properties file, located in the classpath. 



Building From Source
----------------------

Once you check out the code from GitHub, you can build it with Maven using the following:  
```mvn package```

The Maven project is configured to not package the dependencies in the jar file, but instead to copy them to a subfolder. The directory structure of the packaged service is as follows:

s3uploadservice.jar  
config.json  
lib  
--dependency jars  

To package the dependency files in the jar file, edit the pom.xml file and remove the maven-dependency-plugin.



Important Notes
----------------

* The owner of the S3 bucket where files are uploaded will incur charges based on file upload activity.

* The service uploads data to Amazon S3 via SSL endpoints using the HTTPS protocol.

* The service does not support client side or server side encryption at this time.

* The service does not support multipart uploads at this time. I will look into using the AWS SDK's TransferManager which does support multipart uploads, instead of the S3Client. Amazon encourages customers to use multipart uploads for files larger than 100 MB.

* If you do not have versioning enabled for an S3 bucket, newly uploaded files that already exist in that bucket will be replaced by the uploaded file.

* You must add at least one local path to upload folders from, in the config.json file. This configuration file can be placed external to the jar file (in the same folder as the jar) or it can be placed in the jar file (in the classpath). The service gives preference to the external configuration file using it if it exists.

* The user that runs the service must have read and write permissions on the local paths that are configured. The service will read files, delete files, move files and create folders on the local paths and cannot function without these permissions.

* By default files in the configured local path will be deleted after successful upload. The service can be configured to move files after successful upload in which case, they will be moved to the "uploaded" subfolder of each local path.

* Files in the configured local path will be moved after an upload fails to the "failed" subfolder of each local path.



Other Notes
-------------

* The S3UploadService service polls the configured local folders to search for files to upload. A more efficient approach would be to employ a messaging system. Using that approach, the process that produces the files would send a message to the queue when there is a new file to upload. The upload service consumes the messages from the queue and upload the files, not needing to rely on file polling. I will eventually update this service to also support the use of a message queue system. 


* Another possible alternative to polling the configured local folders would have been to use the java.nio.file package's Watch Service API. However, using that approach would have still required working with a directory stream to handle OVERFLOW events, which indicate that watch events may have been lost. Also, on Mac OS X the Watch Service API currently uses file polling behind the scenes. After consideration, we decided to use file polling for the short term with plans to employ a message queue system in the near future.
