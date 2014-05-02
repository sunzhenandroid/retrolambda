// Copyright © 2013-2014 Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.retrolambda.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.*;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.*;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

abstract class ProcessClassesMojo extends AbstractMojo {

    private static final String VERSION_DEPENDENCY = "2.0";
    private static final String GROUP_ID_DEPENDENCY = "org.apache.maven.plugins";
    private static final String ARTIFACT_ID_DEPENDENCY = "maven-dependency-plugin";
    private static final String VERSION_ANTRUN = "1.7";

    private static final String GROUP_ID_ANTRUN = "org.apache.maven.plugins";
    private static final String ARTIFACT_ID_ANTRUN = "maven-antrun-plugin";

    private static final String RETROLAMBDA_JAR = "retrolambda.jar";

    private final Map<String, Integer> targetBytecodeVersions = new HashMap<String, Integer>();

    @Component
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private MavenProject project;

    /**
     * The location of the Java 8 JDK (not JRE).
     */
    @Parameter(required = false, property = "java8home", defaultValue = "${env.JAVA8_HOME}")
    private String java8home;

    /**
     * The Java version targeted by the bytecode processing. Possible values are
     * 1.5, 1.6, 1.7 and 1.8. After processing the classes will be compatible
     * with the target JVM provided the known limitations are considered. See <a
     * href="https://github.com/orfjackal/retrolambda">project documentation</a>
     * for more details.
     */
    @Parameter(required = false, property = "retrolambdaTarget", defaultValue = "1.7")
    private String target;

    /**
     * The directory containing the main (non-test) compiled classes. These
     * classes will be overwritten with bytecode changes to obtain compatibility
     * with target Java runtime.
     */
    @Parameter(required = false, property = "retrolambdaMainClassesDir", defaultValue = "${project.build.outputDirectory}")
    private String mainClassesDir;

    /**
     * The directory containing the compiled test classes. These classes will be
     * overwritten with bytecode changes to obtain compatibility with target
     * Java runtime.
     */
    @Parameter(required = false, property = "retrolambdaTestClassesDir", defaultValue = "${project.build.testOutputDirectory}")
    private String testClassesDir;

    private final ClassesType classesType;

    ProcessClassesMojo(ClassesType classesType) {
        this.classesType = classesType;
        targetBytecodeVersions.put("1.5", 49);
        targetBytecodeVersions.put("1.6", 50);
        targetBytecodeVersions.put("1.7", 51);
        targetBytecodeVersions.put("1.8", 52);
    }

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        log.info("starting execution");
        validateJava8home();
        validateTarget();
        String retrolambdaVersion = getRetrolambdaVersion();
        executeMojo(
                plugin(groupId(GROUP_ID_DEPENDENCY),
                        artifactId(ARTIFACT_ID_DEPENDENCY),
                        version(VERSION_DEPENDENCY)),
                goal("copy"),
                configuration(element("artifactItems",
                        element("artifactItem",
                                element(name("groupId"), "net.orfjackal.retrolambda"),
                                element(name("artifactId"), "retrolambda"),
                                element(name("version"), retrolambdaVersion),
                                element(name("overWrite"), "true"),
                                element(name("outputDirectory"), project.getBuild().getDirectory()),
                                element(name("destFileName"), RETROLAMBDA_JAR)))),
                executionEnvironment(project, session, pluginManager));
        log.info("copied retrolambda.jar to build directory");
        log.info("processing classes");
        if (classesType == ClassesType.MAIN) {
            processClasses(mainClassesDir, "maven.compile.classpath");
        } else {
            processClasses(testClassesDir, "maven.test.classpath");
        }
        log.info("processed classes");
    }

    private void validateTarget() throws MojoExecutionException {
        if (!targetBytecodeVersions.containsKey(target)) {
            throw new MojoExecutionException(
                    "Unrecognized target '" + target + "'. Possible values are 1.5, 1.6, 1.7, 1.8 representing those versions of Java.");
        }
    }

    private void validateJava8home() throws MojoExecutionException {
        File jdk = new File(java8home);
        if (!jdk.exists() || !jdk.isDirectory()) {
            throw new MojoExecutionException("Must set configuration element java8home or environment variable JAVA8_HOME to a valid JDK 8 location");
        }
    }

    private void processClasses(String input, String classpathId)
            throws MojoExecutionException {

        executeMojo(
                plugin(groupId(GROUP_ID_ANTRUN),
                        artifactId(ARTIFACT_ID_ANTRUN), version(VERSION_ANTRUN)),
                goal("run"),
                configuration(element(
                        "target",
                        element("property",
                                attributes(attribute("name", "the_classpath"),
                                        attribute("refid", classpathId))),
                        element("exec",
                                attributes(
                                        attribute("executable", java8home + "/bin/java"),
                                        attribute("failonerror", "true")),
                                element("arg", attribute("value", "-Dretrolambda.bytecodeVersion=" + targetBytecodeVersions.get(target))),
                                element("arg", attribute("value", "-Dretrolambda.inputDir=" + input)),
                                element("arg", attribute("value", "-Dretrolambda.classpath=${the_classpath}")),
                                element("arg", attribute("value", "-javaagent:" + project.getBuild().getDirectory() + "/" + RETROLAMBDA_JAR)),
                                element("arg", attribute("value", "-jar")),
                                element("arg", attribute("value", project.getBuild().getDirectory() + "/" + RETROLAMBDA_JAR))))),
                executionEnvironment(project, session, pluginManager));
    }

    private static String getRetrolambdaVersion() {
        InputStream is = ProcessClassesMojo.class.getResourceAsStream("/retrolambda.properties");
        Properties p = new Properties();
        try {
            p.load(is);
            return p.getProperty("retrolambda.version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}