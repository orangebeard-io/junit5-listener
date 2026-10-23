<h1 align="center">
  <a href="https://github.com/orangebeard-io/junit5-listener">
    <img src="https://raw.githubusercontent.com/orangebeard-io/junit5-listener/master/.github/logo.png" alt="Orangebeard.io JUnit5 Listener" height="200">
  </a>
  <br>Orangebeard.io JUnit5 Listener<br>
</h1>

<h4 align="center">Orangebeard listener for the Java <a href="https://junit.org/junit5/" target="_blank" rel="noopener">JUnit</a> test framework.</h4>

<p align="center">
  <a href="https://repo.maven.apache.org/maven2/io/orangebeard/junit5-listener/">
    <img src="https://img.shields.io/maven-central/v/io.orangebeard/junit5-listener?style=flat-square"
      alt="MVN Version" />
  </a>
  <a href="https://github.com/orangebeard-io/junit5-listener/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/orangebeard-io/junit5-listener/master.yml?branch=master&style=flat-square"
      alt="Build Status" />
  </a>
  <a href="https://github.com/orangebeard-io/junit5-listener/blob/master/LICENSE.txt">
    <img src="https://img.shields.io/github/license/orangebeard-io/junit5-listener?style=flat-square"
      alt="License" />
  </a>
</p>

<div align="center">
  <h4>
    <a href="https://orangebeard.io">Orangebeard</a> |
    <a href="#installation">Installation</a> |
    <a href="#configuration">Configuration</a>
  </h4>
</div>

## Requirements

- Java 17 or newer
- JUnit 5 (5.10 or newer) **or JUnit 6** - the listener is built against JUnit 6 and its test suite passes on both
- An Orangebeard instance and an access token for your project

## What is reported

- Every test class becomes a (nested) suite named after its package and class; `@Nested` classes become sub-suites.
- Every test method becomes a test with its result: passed, failed, stopped (aborted/assumption failed) or skipped (disabled, with the reason as a log).
- For failed and aborted tests, the exception message and stack trace are added to the test as logs.
- A failing `@BeforeAll` or `@AfterAll` method is reported as a failed `Class setup (@BeforeAll)` / `Class teardown (@AfterAll)` test in the class' suite, so a class that did not run does not go unnoticed.
- The test run is finished as soon as JUnit has finished the test plan (with a shutdown hook as fallback).
- Reporting is asynchronous: your tests are not slowed down by the connection to Orangebeard, and tests keep running if Orangebeard cannot be reached.
- Parallel test execution is supported.

## Installation

### Install the mvn package

Add the dependency to your pom:
```xml
<dependency>
    <groupId>io.orangebeard</groupId>
    <artifactId>junit5-listener</artifactId>
    <version>version</version>
    <scope>test</scope>
</dependency>
```

## Configuration

For general usage of the extension, add or modify the surefire plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <properties>
            <configurationParameters>
                junit.jupiter.extensions.autodetection.enabled = true
            </configurationParameters>
        </properties>
    </configuration>
</plugin>
```

Alternatively (or for use with your IDE), add `junit-platform.properties` containing:
```properties
junit.jupiter.extensions.autodetection.enabled = true
```

To also use the execution listener, so ReportEntries can be captured and reported, add
`src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener` and set its content to:
`io.orangebeard.listener.OrangebeardExecutionListener`

You can also choose to extend individual classes with the extension class:

```java
@ExtendWith(OrangebeardExtension.class)
public class YourTestClass {
  ...
}
```

Configure the connection with Orangebeard, preferably with environment variables or system properties (see below), so that
the access token does not end up in your repository. The properties can also be put in a file named `orangebeard.properties`
in the test resources folder. Only do that for properties that are not secret, or keep that file out of version control.
The following properties are supported:

```properties
orangebeard.endpoint=<ORANGEBEARD-ENDPOINT>
orangebeard.accessToken=<XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX>
orangebeard.project=<PROJECT_NAME>
orangebeard.testset=<TESTSET_NAME>

# optional
orangebeard.description=<DESCRIPTION>
orangebeard.attributes=key:value; value;
```

### Environment variables

The properties above can be set as environment variables as well. Environment variables will override property values. In the environment variables, it is allowed to replace the dot by an underscore.
for example: ```orangebeard_endpoint``` as an environment variable will work as well.

### Security notes

- Keep the access token out of version control: provide it with an environment variable or CI secret.
- Use an `https` endpoint. The token is sent with every request.
- Exception messages, stack traces and report entries of your tests are sent to Orangebeard as they are. Make sure
  tests do not put secrets (passwords, tokens) in assertion messages or output.
