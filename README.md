## Hygieia Collector to collect static code analysis data from Sonar
[![Build Status](https://travis-ci.com/Hygieia/hygieia-codequality-sonar-collector.svg?branch=master)](https://travis-ci.com/Hygieia/hygieia-codequality-sonar-collector)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Hygieia_hygieia-codequality-sonar-collector&metric=alert_status)](https://sonarcloud.io/dashboard?id=Hygieia_hygieia-codequality-sonar-collector)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/Hygieia/hygieia-codequality-sonar-collector.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Hygieia/hygieia-codequality-sonar-collector/alerts/)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Hygieia/hygieia-codequality-sonar-collector.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Hygieia/hygieia-codequality-sonar-collector/context:java)
[![Maven Central](https://img.shields.io/maven-central/v/com.capitalone.dashboard/sonar-codequality-collector.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.capitalone.dashboard%22%20AND%20a:%22sonar-codequality-collector%22)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Gitter Chat](https://badges.gitter.im/Join%20Chat.svg)](https://www.apache.org/licenses/LICENSE-2.0)
<br/>
<br/>

Configure the Sonar Collector to display and analyze information (related to code quality) on the Hygieia Dashboard, from SonarQube (formerly known as Sonar).
Hygieia uses Spring Boot to package the collector as an executable JAR file with dependencies.

# Table of Contents
* [Setup Instructions](#setup-instructions)
* [Sample Application Properties File](#sample-application-properties-file)
* [Run collector with Docker](#run-collector-with-docker)

### Setup Instructions

To configure the Sonar Collector, execute the following steps: 

*	**Step 1 - Artifact Preparation:**

	Please review the two options in Step 1 to find the best fit for you. 

	***Option 1 - Download the artifact:***

	You can download the SNAPSHOTs from the SNAPSHOT directory [here](https://oss.sonatype.org/content/repositories/snapshots/com/capitalone/dashboard/sonar-codequality-collector/) or from the maven central repository [here](https://search.maven.org/artifact/com.capitalone.dashboard/sonar-codequality-collector).  

	***Option 2 - Build locally:***

	To configure the Sonar Collector, git clone the [sonar collector repo](https://github.com/Hygieia/hygieia-codequality-sonar-collector).  Then, execute the following steps:

	To package the sonar collector source code into an executable JAR file, run the maven build from the `\hygieia-codequality-sonar-collector` directory of your source code installation:

	```bash
	mvn install
	```

	The output file `[collector name].jar` is generated in the `hygieia-codequality-sonar-collector\target` folder.

	Once you have chosen an option in Step 1, please proceed: 

*	**Step 2: Set Parameters in the Application Properties File**

	Set the configurable parameters in the `application.properties` file to connect to the Dashboard MongoDB database instance, including properties required by the Sonar Collector. To configure the parameters, refer to the [application properties](#sample-application-properties-file) section.

	For more information about the server configuration, see the Spring Boot [documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files).

*	**Step 3: Deploy the Executable File**

	To run the [collector name].jar file, change directory to 'hygieia-codequality-sonar-collector\target' and then execute the following command from the command prompt:

	```bash
	java -jar [collector name].jar --spring.config.name=sonar --spring.config.location=[path to application.properties file]
	```

### Sample Application Properties File

The sample `application.properties` file lists parameters with sample values to configure the Sonar Collector. Set the parameters based on your environment setup.

```properties
		# Database Name
		dbname=dashboarddb

		# Database HostName - default is localhost
		dbhost=10.0.1.1

		# Database Port - default is 27017
		dbport=27017

		# MongoDB replicaset
		dbreplicaset=[false if you are not using MongoDB replicaset]
		dbhostport=[host1:port1,host2:port2,host3:port3]

		# Database Username - default is blank
		dbusername=dashboarduser

		# Database Password - default is blank
		dbpassword=dbpassword

		# Collector schedule (required)
		sonar.cron=0 0/5 * * * *

		# Sonar server(s) (required) - Can provide multiple
		sonar.servers[0]=http://sonar.company.com
		
		# Sonar version, match array index to the server. If not set, will default to version prior to 6.3.
		sonar.versions[0]=6.31
		
		# Sonar Metrics - Required. 
		#Sonar versions lesser than 6.3
		
		# Sonar tokens to connect to authenticated url 
		sonar.tokens[0]=<api token>
		sonar.metrics[0]=ncloc,line_coverage,violations,critical_violations,major_violations,blocker_violations,violations_density,sqale_index,test_success_density,test_failures,test_errors,tests
		
		# For Sonar version 6.3 and above
		sonar.metrics[0]=ncloc,violations,new_vulnerabilities,critical_violations,major_violations,blocker_violations,tests,test_success_density,test_errors,test_failures,coverage,line_coverage,sqale_index,alert_status,quality_gate_details
		
		# Sonar login credentials
		# Format: username1,username2,username3,etc.
		sonar.usernames= 
		# Format: password1,password2,password3,etc.
                sonar.passwords=

```

## Run collector with Docker

You can install Hygieia by using a docker image from docker hub. This section gives detailed instructions on how to download and run with Docker. 

*	**Step 1: Download**

	Navigate to the docker hub location of your collector [here](https://hub.docker.com/u/hygieiadoc) and download the latest image (most recent version is preferred).  Tags can also be used, if needed.

*	**Step 2: Run with Docker**

	```Docker run -e SKIP_PROPERTIES_BUILDER=true -v properties_location:/hygieia/config image_name```
	
	- <code>-e SKIP_PROPERTIES_BUILDER=true</code>  <br />
	indicates whether you want to supply a properties file for the java application. If false/omitted, the script will build a properties file with default values
	- <code>-v properties_location:/hygieia/config</code> <br />
	if you want to use your own properties file that located outside of docker container, supply the path here. 
		- Example: <code>-v /Home/User/Document/application.properties:/hygieia/config</code>

