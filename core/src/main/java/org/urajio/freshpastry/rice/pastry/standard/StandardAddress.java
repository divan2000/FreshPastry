package org.urajio.freshpastry.rice.pastry.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.rice.environment.Environment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Constructs an address for a specific class and instance name.
 *
 * @author Alan Mislove
 * @version $Id$
 */
public class StandardAddress {
    private final static Logger logger = LoggerFactory.getLogger(StandardAddress.class);

    //serial ver for backward compatibility
    private static final long serialVersionUID = 1564239935633411277L;

    @SuppressWarnings("unchecked")
    public static int getAddress(Class c, String instance, Environment env) {
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            logger.error("No SHA support!");
        }

        byte[] digest = new byte[4];
        md.update(c.toString().getBytes());

        digest[0] = md.digest()[0];
        digest[1] = md.digest()[1];

        if ((instance == null) || (instance.equals(""))) {
            // digest 2,3 == 0
        } else {
            md.reset();
            md.update(instance.getBytes());

            digest[2] = md.digest()[0];
            digest[3] = md.digest()[1];
        }

        int myCode = shift(digest[0], 24) | shift(digest[1], 16) |
                shift(digest[2], 8) | shift(digest[3], 0);

        return myCode;
    }

    /**
     * Returns the short prefix to look for.
     *
     * @param c
     * @param env
     * @return
     */
    @SuppressWarnings("unchecked")
    public static short getAddress(Class c, Environment env) {
        int myCode = getAddress(c, null, env);

        return (short) unshift(myCode, 16);
    }

    private static int shift(int n, int s) {
        return (n & 0xff) << s;
    }

    private static int unshift(int n, int s) {
        return n >> s;
    }


    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Class c = StandardAddress.class;
//    Class c = SocketPastryNode.class;
        int a = getAddress(c, null, null);
        int b = getAddress(c, "b", null);
        short a1 = getAddress(c, null);

        System.out.println(a + " " + Integer.toBinaryString(a) + " " + a1 + " " + Integer.toBinaryString(a1));
        System.out.println(b + " " + Integer.toBinaryString(b));
    }

//  public static int getAddress(Class c, String instance, Environment env) {
//    MessageDigest md = null;
//    
//    try {
//      md = MessageDigest.getInstance("SHA");
//    } catch ( NoSuchAlgorithmException e ) {
//      Logger logger = env.getLogManager().getLogger(StandardAddress.class, null);
//      if (logger.level <= Logger.SEVERE) logger.log(
//        "No SHA support!" );
//    }
//    
//    String name = c.toString() + "-" + instance;
//
//    md.update(name.getBytes());
//    byte[] digest = md.digest();
//
//    int myCode = (digest[0] << 24) + (digest[1] << 16) +
//             (digest[2] << 8) + digest[3];
//
//    return myCode;
//  }
}

