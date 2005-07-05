/*
 * Copyright 2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 
package org.apache.commons.logging.pathable;

import java.net.URL;
import java.util.Enumeration;
import java.util.ArrayList;
import java.net.URLClassLoader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.PathableTestSuite;
import org.apache.commons.logging.PathableClassLoader;

/**
 * Tests for the PathableTestSuite and PathableClassLoader functionality.
 * <p>
 * These tests assume:
 * <ul>
 * <li>junit is in system classpath
 * <li>nothing else is in system classpath
 * </ul>
 */

public class PathableTestCase extends TestCase {
    
    /**
     * Set up a custom classloader hierarchy for this test case.
     * The hierarchy is:
     * <ul>
     * <li> contextloader: parent-first.
     * <li> childloader: parent-first, used to load test case.
     * <li> parentloader: parent-first, parent is the bootclassloader.
     * </ul>
     */
    public static Test suite() throws Exception {
        // make the parent a direct child of the bootloader to hide all
        // other classes in the system classpath
        PathableClassLoader parent = new PathableClassLoader(null);
        
        // make the junit classes from the system classpath visible, though,
        // as junit won't be able to call this class at all without this..
        parent.useSystemLoader("junit.");
        
        // make the commons-logging.jar classes visible via the parent
        parent.addLogicalLib("commons-logging");
        
        // create a child classloader to load the test case through
        PathableClassLoader child = new PathableClassLoader(parent);
        
        // obviously, the child classloader needs to have the test classes
        // in its path!
        child.addLogicalLib("testclasses");
        child.addLogicalLib("commons-logging-adapters");
        
        // create a third classloader to be the context classloader.
        PathableClassLoader context = new PathableClassLoader(child);

        // reload this class via the child classloader
        Class testClass = child.loadClass(PathableTestCase.class.getName());
        
        // and return our custom TestSuite class
        return new PathableTestSuite(testClass, context);
    }
    
    /**
     * Test that the classloader hierarchy is as expected, and that
     * calling loadClass() on various classloaders works as expected.
     * Note that for this test case, parent-first classloading is
     * in effect.
     */
    public void testPaths() throws Exception {
        // the context classloader is not expected to be null
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        assertNotNull("Context classloader is null", contextLoader);
        assertEquals("Context classloader has unexpected type",
                PathableClassLoader.class.getName(),
                contextLoader.getClass().getName());
        
        // the classloader that loaded this class is obviously not null
        ClassLoader thisLoader = this.getClass().getClassLoader();
        assertNotNull("thisLoader is null", thisLoader);
        assertEquals("thisLoader has unexpected type",
                PathableClassLoader.class.getName(),
                thisLoader.getClass().getName());
        
        // the suite method specified that the context classloader's parent
        // is the loader that loaded this test case.
        assertSame("Context classloader is not child of thisLoader",
                thisLoader, contextLoader.getParent());

        // thisLoader's parent should be available
        ClassLoader parentLoader = thisLoader.getParent();
        assertNotNull("Parent classloader is null", parentLoader);
        assertEquals("Parent classloader has unexpected type",
                PathableClassLoader.class.getName(),
                parentLoader.getClass().getName());
        
        // parent should have a parent of null
        assertNull("Parent classloader has non-null parent", parentLoader.getParent());

        // getSystemClassloader is not a PathableClassLoader; it's of a
        // built-in type. This also verifies that system classloader is none of
        // (context, child, parent).
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        assertNotNull("System classloader is null", systemLoader);
        assertFalse("System classloader has unexpected type",
                PathableClassLoader.class.getName().equals(
                        systemLoader.getClass().getName()));

        // junit classes should be visible; their classloader is system.
        // this will of course throw an exception if not found.
        Class junitTest = contextLoader.loadClass("junit.framework.Test");
        assertSame("Junit not loaded via systemloader",
                systemLoader, junitTest.getClassLoader());

        // jcl api classes should be visible only via the parent
        Class logClass = contextLoader.loadClass("org.apache.commons.logging.Log");
        assertSame("Log class not loaded via parent",
                logClass.getClassLoader(), parentLoader);

        // jcl adapter classes should be visible via both parent and child. However
        // as the classloaders are parent-first we should see the parent one.
        Class log4jClass = contextLoader.loadClass("org.apache.commons.logging.impl.Log4J12Logger");
        assertSame("Log4J12Logger not loaded via parent", 
                log4jClass.getClassLoader(), parentLoader);
        
        // test classes should be visible via the child only
        Class testClass = contextLoader.loadClass("org.apache.commons.logging.PathableTestSuite");
        assertSame("PathableTestSuite not loaded via child", 
                testClass.getClassLoader(), thisLoader);
        
        // test loading of class that is not available
        try {
            Class noSuchClass = contextLoader.loadClass("no.such.class");
            fail("Class no.such.class is unexpectedly available");
        } catch(ClassNotFoundException ex) {
            // ok
        }

        // String class classloader is null
        Class stringClass = contextLoader.loadClass("java.lang.String");
        assertNull("String class classloader is not null!",
                stringClass.getClassLoader());
    }
    
    /**
     * Test that the various flavours of ClassLoader.getResource work as expected.
     */
    public void testResource() {
        URL resource;
        
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader childLoader = contextLoader.getParent();
        
        // getResource where it doesn't exist
        resource = childLoader.getResource("nosuchfile");
        assertNull("Non-null URL returned for invalid resource name", resource);

        // getResource where it is accessable only to parent classloader
        resource = childLoader.getResource("org/apache/commons/logging/Log.class");
        assertNotNull("Unable to locate Log.class resource", resource);
        
        // getResource where it is accessable only to child classloader
        resource = childLoader.getResource("org/apache/commons/logging/PathableTestSuite.class");
        assertNotNull("Unable to locate PathableTestSuite.class resource", resource);

        // getResource where it is accessable to both classloaders. The one visible
        // to the parent should be returned. The URL returned will be of form
        //  jar:file:/x/y.jar!path/to/resource. The filename part should include the jarname
        // of form commons-logging-nnnn.jar, not commons-logging-adapters-nnnn.jar
        resource = childLoader.getResource("org/apache/commons/logging/impl/Log4J12Logger.class");
        assertNotNull("Unable to locate Log4J12Logger.class resource", resource);
        assertTrue("Incorrect source for Log4J12Logger class",
                resource.toString().indexOf("/commons-logging-1.") > 0);
    }
    
    /**
     * Test that the various flavours of ClassLoader.getResources work as expected.
     */
    public void testResources() throws Exception {
        Enumeration resources;
        URL[] urls;
        
        // verify the classloader hierarchy
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader childLoader = contextLoader.getParent();
        ClassLoader parentLoader = childLoader.getParent();
        ClassLoader bootLoader = parentLoader.getParent();
        assertNull("Unexpected classloader hierarchy", bootLoader);
        
        // getResources where no instances exist
        resources = childLoader.getResources("nosuchfile");
        urls = toURLArray(resources);
        assertEquals("Non-null URL returned for invalid resource name", 0, urls.length);
        
        // getResources where the resource only exists in the parent
        resources = childLoader.getResources("org/apache/commons/logging/Log.class");
        urls = toURLArray(resources);
        assertEquals("Unexpected number of Log.class resources found", 1, urls.length);
        
        // getResources where the resource only exists in the child
        resources = childLoader.getResources("org/apache/commons/logging/PathableTestSuite.class");
        urls = toURLArray(resources);
        assertEquals("Unexpected number of PathableTestSuite.class resources found", 1, urls.length);
        
        // getResources where the resource exists in both.
        // resources should be returned in order (parent-resource, child-resource)
        resources = childLoader.getResources("org/apache/commons/logging/impl/Log4J12Logger.class");
        urls = toURLArray(resources);
        assertEquals("Unexpected number of Log4J12Logger.class resources found", 2, urls.length);
        assertTrue("Incorrect source for Log4J12Logger class",
                urls[0].toString().indexOf("/commons-logging-1.") > 0);
        assertTrue("Incorrect source for Log4J12Logger class",
                urls[1].toString().indexOf("/commons-logging-adapters-1.") > 0);
        
    }

    /**
     * Utility method to convert an enumeration-of-URLs into an array of URLs.
     */
    private static URL[] toURLArray(Enumeration e) {
        ArrayList l = new ArrayList();
        while (e.hasMoreElements()) {
            URL u = (URL) e.nextElement();
            l.add(u);
        }
        URL[] tmp = new URL[l.size()];
        return (URL[]) l.toArray(tmp);
    }

    /**
     * Test that getResourceAsStream works.
     */
    public void testResourceAsStream() throws Exception {
        java.io.InputStream is;
        
        // verify the classloader hierarchy
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader childLoader = contextLoader.getParent();
        ClassLoader parentLoader = childLoader.getParent();
        ClassLoader bootLoader = parentLoader.getParent();
        assertNull("Unexpected classloader hierarchy", bootLoader);
        
        // getResourceAsStream where no instances exist
        is = childLoader.getResourceAsStream("nosuchfile");
        assertNull("Invalid resource returned non-null stream", is);
        
        // getResourceAsStream where resource does exist
        is = childLoader.getResourceAsStream("org/apache/commons/logging/Log.class");
        assertNotNull("Null returned for valid resource", is);
        is.close();
        
        // It would be nice to test parent-first ordering here, but that would require
        // having a resource with the same name in both the parent and child loaders,
        // but with different contents. That's a little tricky to set up so we'll
        // skip that for now.
    }
    
    /**
     * Verify that the context classloader is a custom one, then reset it to
     * a non-custom one.
     */
    private static void checkAndSetContext() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        assertEquals("ContextLoader is of unexpected type", 
                contextLoader.getClass().getName(), 
                PathableClassLoader.class.getName());
        
        URL[] noUrls = new URL[0];
        Thread.currentThread().setContextClassLoader(new URLClassLoader(noUrls));
    }
    
    /**
     * Verify that when a test method modifies the context classloader it is
     * reset before the next test is run.
     * <p>
     * This method works in conjunction with testResetContext2. There is no
     * way of knowing which test method junit will run first, but it doesn't
     * matter; whichever one of them runs first will modify the contextClassloader.
     * If the PathableTestSuite isn't resetting the contextClassLoader then whichever
     * of them runs second will fail. Of course if other methods are run in-between
     * then those methods might also fail...
     */
    public void testResetContext1() {
        checkAndSetContext();
    }

    /**
     * See testResetContext1.
     */
    public void testResetContext2() {
        checkAndSetContext();
    }
}
