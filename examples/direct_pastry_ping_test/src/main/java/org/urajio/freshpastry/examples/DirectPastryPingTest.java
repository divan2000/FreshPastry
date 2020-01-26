package org.urajio.freshpastry.examples;

import org.urajio.freshpastry.rice.environment.Environment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * DirectPastryPingTest
 * <p>
 * A performance test suite for pastry.
 *
 * @author Rongmei Zhang
 * @version $Id$
 */

public class DirectPastryPingTest {

    public DirectPastryPingTest() {
    }

    private static boolean parseInput(String in, Environment environment) {
        StringTokenizer tokened = new StringTokenizer(in);
        if (!tokened.hasMoreTokens()) {
            return false;
        }

        String token = tokened.nextToken();
        int n = -1;
        int k = -1;
        SinglePingTest spt;
        int i;

        if (token.startsWith("q")) { //quit
            return true;
        } else if (token.startsWith("s")) { //standalone
            Vector trlist = new Vector();

            //      k = 200000;

            for (i = 0; i < 8; i++) {
                n = k = (i + 1) * 1000;
                PingTestRecord tr = new PingTestRecord(n, k, environment.getParameters().getInt("pastry_rtBaseBitLength"));
                spt = new SinglePingTest(tr, environment);
                spt.test();
                System.out.println(tr.getNodeNumber() + "\t" + tr.getAveHops() + "\t"
                        + tr.getAveDistance());
            }
        }
        return false;
    }

    public static void main(String[] args) {
        boolean quit = false;
        Environment env = Environment.directEnvironment();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String command = null;

        System.out.println("Usage: s - run standalone test");
        System.out.println("       q - quit");

        while (!quit) {
            try {
                command = input.readLine();
            } catch (Exception e) {
                System.out.println(e);
            }
            quit = parseInput(command, env);
        }
    }
}

