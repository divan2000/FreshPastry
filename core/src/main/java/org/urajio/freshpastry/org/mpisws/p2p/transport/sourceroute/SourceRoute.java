package org.urajio.freshpastry.org.mpisws.p2p.transport.sourceroute;

import org.urajio.freshpastry.rice.p2p.commonapi.rawserialization.OutputBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class which represets a source route to a remote IP address.
 *
 * @author Alan Mislove
 * @version $Id: SourceRoute.java 3613 2007-02-15 14:45:14Z jstewart $
 */
public abstract class SourceRoute<Identifier> {

    // Support for coalesced Ids - ensures only one copy of each Id is in memory

    // the default distance, which is used before a ping
    protected List<Identifier> path;

    protected SourceRoute(List<Identifier> path) {
        this.path = new ArrayList<>(path);

        // assertion
        for (Identifier i : this.path) {
            if (i == null) throw new IllegalArgumentException("path[" + i + "] is null");
        }
    }

    protected SourceRoute(Identifier address) {
        this.path = new ArrayList<>(1);
        path.add(address);
    }

    protected SourceRoute(Identifier local, Identifier remote) {
        this.path = new ArrayList<>(2);
        path.add(local);
        path.add(remote);
    }

    /**
     * Returns the hashCode of this source route
     *
     * @return The hashCode
     */
    public int hashCode() {
        int result = 399388937;

        for (Identifier i : path)
            result ^= i.hashCode();

        return result;
    }

    /**
     * Checks equality on source routes
     *
     * @param o The source route to compare to
     * @return The equality
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (!(o instanceof SourceRoute))
            return false;

        SourceRoute that = (SourceRoute) o;
        return this.path.equals(that.path);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("{");

        for (int i = 0; i < path.size(); i++) {
            Identifier thePath = path.get(i);
            result.append(thePath.toString());
            if (i < path.size() - 1) result.append(" -> ");
        }

        result.append("}");

        return result.toString();
    }

    /**
     * Method which returns the first "hop" of this source route
     *
     * @return The first hop of this source route
     */
    public Identifier getFirstHop() {
        return path.get(0);
    }

    /**
     * Method which returns the first "hop" of this source route
     *
     * @return The first hop of this source route
     */
    public Identifier getLastHop() {
        return path.get(path.size() - 1);
    }

    /**
     * Returns the number of hops in this source route
     *
     * @return The number of hops
     */
    public int getNumHops() {
        return path.size();
    }

    /**
     * Returns the hop at the given index
     *
     * @param i The hop index
     * @return The hop
     */
    public Identifier getHop(int i) {
        return path.get(i);
    }

    /**
     * Returns whether or not this route is direct
     *
     * @return whether or not this route is direct
     */
    public boolean isDirect() {
        return (path.size() <= 2);
    }

    /**
     * Returns whether or not this route goes through the given address
     *
     * @return whether or not this route goes through the given address
     */
    public boolean goesThrough(Identifier address) {
        return path.contains(address);
    }

    /**
     * Returns which hop in the path the identifier is.
     *
     * @param identifier the hop
     * @return -1 if not in the path
     */
    public int getHop(Identifier identifier) {
        for (int i = 0; i < path.size(); i++) {
            Identifier id = path.get(i);
            if (id.equals(identifier)) return i;
        }
        return -1;
    }

    /***************** Raw Serialization ***************************************/
    abstract public void serialize(OutputBuffer buf) throws IOException;

    abstract public int getSerializedLength();
}


