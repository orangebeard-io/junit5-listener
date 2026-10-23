package io.orangebeard.listener;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test classes that are run by {@link OrangebeardExtensionIntegrationTest} through the JUnit launcher, with
 * the extension, to see what is reported. They are not run by Maven: this class is not a test class itself, and
 * nested classes are not picked up on their own.
 */
class Samples {

    @ExtendWith(OrangebeardExtension.class)
    static class Mixed {
        @Test
        void passes() {
        }

        @Test
        void fails() {
            fail("boom");
        }

        @Test
        void aborts() {
            assumeTrue(false, "not applicable");
        }

        @Test
        @Disabled("not today")
        void disabled() {
        }

        @Nested
        class Inner {
            @Test
            void passesToo() {
            }
        }
    }

    @ExtendWith(OrangebeardExtension.class)
    static class BrokenSetup {
        @BeforeAll
        static void setUp() {
            throw new IllegalStateException("setup failed");
        }

        @Test
        void neverRuns() {
        }
    }

    @ExtendWith(OrangebeardExtension.class)
    static class ParallelA {
        @Test
        void one() {
        }

        @Test
        void two() {
        }
    }

    @ExtendWith(OrangebeardExtension.class)
    static class ParallelB {
        @Test
        void one() {
        }

        @Test
        void two() {
        }
    }

    @ExtendWith(OrangebeardExtension.class)
    static class ParallelC {
        @Test
        void one() {
        }

        @Test
        void two() {
        }
    }
}
