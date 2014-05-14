// Copyright © 2013-2014 Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.retrolambda.test;

import org.junit.Test;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

public class LambdaTest {

    @Test
    public void empty_lambda() {
        Runnable lambda = () -> {
        };

        lambda.run();
    }

    @Test
    public void lambda_returning_a_value() throws Exception {
        Callable<String> lambda = () -> "some value";

        assertThat(lambda.call(), is("some value"));
    }

    private interface Function1<IN, OUT> {
        OUT apply(IN value);
    }

    @Test
    public void lambda_taking_parameters() {
        Function1<String, Integer> lambda = (String s) -> s.getBytes().length;

        assertThat(lambda.apply("foo"), is(3));
    }

    @Test
    public void lambda_in_project_main_sources() throws Exception {
        assertThat(InMainSources.callLambda(), is(42));
    }

    private int instanceVar = 0;

    @Test
    public void lambda_using_instance_variables() {
        Runnable lambda = () -> {
            instanceVar = 42;
        };
        lambda.run();

        assertThat(instanceVar, is(42));
    }

    @Test
    public void lambda_using_local_variables() {
        int[] localVar = new int[1];
        Runnable lambda = () -> {
            localVar[0] = 42;
        };
        lambda.run();

        assertThat(localVar[0], is(42));
    }

    @Test
    public void lambda_using_local_variables_of_primitive_types() throws Exception {
        boolean bool = true;
        byte b = 2;
        short s = 3;
        int i = 4;
        long l = 5;
        float f = 6;
        double d = 7;
        char c = 8;
        Callable<Integer> lambda = () -> (int) ((bool ? 1 : 0) + b + s + i + l + f + d + c);

        assertThat(lambda.call(), is(36));
    }

    @Test
    public void method_references_to_virtual_methods() throws Exception {
        String foo = "foo";
        Callable<String> ref = foo::toUpperCase;

        assertThat(ref.call(), is("FOO"));
    }

    @Test
    public void method_references_to_interface_methods() throws Exception {
        List<String> foos = Arrays.asList("foo");
        Callable<Integer> ref = foos::size;

        assertThat(ref.call(), is(1));
    }

    @Test
    public void method_references_to_static_methods() throws Exception {
        long expected = System.currentTimeMillis();
        Callable<Long> ref = System::currentTimeMillis;

        assertThat(ref.call(), is(greaterThanOrEqualTo(expected)));
    }

    @Test
    public void method_references_to_constructors() throws Exception {
        Callable<List<String>> ref = ArrayList<String>::new;

        assertThat(ref.call(), is(instanceOf(ArrayList.class)));
    }

    @Test
    public void method_references_to_private_methods() throws Exception {
        Callable<String> ref1 = this::privateInstanceMethod;
        Callable<String> ref2 = LambdaTest::privateClassMethod;

        assertThat(ref1.call(), is("foo"));
        assertThat(ref2.call(), is("foo"));
    }

    private String privateInstanceMethod() {
        return "foo";
    }

    private static String privateClassMethod() {
        return "foo";
    }

    /**
     * We must make private lambda implementation methods package-private,
     * so that the lambda class may call them, but we should not make any
     * more methods non-private than is absolutely necessary.
     */
    @Test
    public void will_not_change_the_visibility_of_unrelated_methods() throws Exception {
        assertThat(unrelatedPrivateMethod(), is("foo"));

        Method method = getClass().getDeclaredMethod("unrelatedPrivateMethod");
        int modifiers = method.getModifiers();

        assertTrue("expected " + method.getName() + " to be private, but modifiers were: " + Modifier.toString(modifiers),
                Modifier.isPrivate(modifiers));
    }

    private String unrelatedPrivateMethod() {
        return "foo";
    }
}
