package org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous;

/**
 * Keeps track of tags, ephemeral identifiers, timestamps, highIdentifiers
 *
 * @author Jeff Hoye
 */
public interface EphemeralDB<Identifier, HighIdentifier> {
    long NO_TAG = Long.MIN_VALUE;

    /**
     * Get the existing tag, or make a new one if needed
     *
     * @param addr
     * @return
     */
    long getTagForEphemeral(Identifier addr);

    /**
     * Return a current valid Identifier for the tag, otherwise, return the default identifier
     * <p>
     * only a getter
     *
     * @param tag
     * @param i   the default identifier
     * @return
     */
    Identifier getEphemeral(long tag, Identifier i);

    /**
     * Tell the DB that the high identifier points to this tag
     *
     * @param high
     * @param tag
     */
    void mapHighToTag(HighIdentifier high, long tag);

    /**
     * Get the valid Identifier for this highIdentifier
     *
     * @param high
     * @return null if there isn't a valid one
     */
    Identifier getEphemeral(HighIdentifier high);
}
