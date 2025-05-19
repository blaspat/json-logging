JSON Logging 
===========================

## Overview
A JSON Logging library with an AOP mechanism. Useful if you want to ingest your logs into ELK or another logging system that supports JSON format.

## Key Features:
* Logs API request and response
* Logs error stack traces
  
Support **Java 8 or later**

## Updates
* **1.0.0**
  * Initial release


## Maven

    <dependency>
        <groupId>io.github.blaspat</groupId>
        <artifactId>json-logging</artifactId>
        <version>1.0.0</version>
    </dependency>


## Configuration
You can configure list of excluded path to log

    logging:
        excluded-paths: /actuator/**,/health



  
## License

This project is licensed under the [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

The copyright owner is Blasius Patrick.
