package org.urajio.freshpastry.rice.pastry.socket.nat.rendezvous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.urajio.freshpastry.org.mpisws.p2p.transport.rendezvous.ContactDirectStrategy;
import org.urajio.freshpastry.rice.Continuation;
import org.urajio.freshpastry.rice.p2p.commonapi.Cancellable;
import org.urajio.freshpastry.rice.pastry.NodeHandle;
import org.urajio.freshpastry.rice.pastry.PastryNode;
import org.urajio.freshpastry.rice.pastry.leafset.LeafSet;
import org.urajio.freshpastry.rice.pastry.pns.PNSApplication;
import org.urajio.freshpastry.rice.pastry.routing.RouteSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Does not try to connect to NATted nodes during PNS.
 * <p>
 * TODO: Cull out firewalled nodes from return values
 * TODO: Make Proximity of firewalled nodes MAX_VALUE
 * TODO: Make Rendezvous Layer check the MultiInetSocketAddress to see if they can contact each other directly
 *
 * @author Jeff Hoye
 */
public class RendezvousPNSApplication extends PNSApplication {
    private final static Logger logger = LoggerFactory.getLogger(RendezvousPNSApplication.class);

    ContactDirectStrategy<RendezvousSocketNodeHandle> contactDirectStrategy;

    public RendezvousPNSApplication(PastryNode pn, ContactDirectStrategy<RendezvousSocketNodeHandle> contactDirectStrategy) {
        super(pn);
        this.contactDirectStrategy = contactDirectStrategy;
        if (contactDirectStrategy == null) {
            throw new IllegalArgumentException("Need contactDirectStrategy was null");
        }
    }

    /**
     * This method decides who to bother trying to connect to.
     * <p>
     * If the node is not NATted, return false.
     * <p>
     * If the node is NATted:
     * c.receiveException()
     * return true
     *
     * @param handle
     * @param c
     * @return
     */
    @SuppressWarnings("unchecked")
    protected boolean ignore(NodeHandle handle, Continuation c) {
        if (useHandle(handle)) return false;
        logger.debug("PNS not using firewalled node " + handle);
        c.receiveException(new NodeIsFirewalledException(handle));
        return true;
    }

    /**
     * Separate this out to make it super easy to change the policy.
     *
     * @param handle
     * @return
     */
    protected boolean useHandle(NodeHandle handle) {
        RendezvousSocketNodeHandle rsnh = (RendezvousSocketNodeHandle) handle;
        return contactDirectStrategy.canContactDirect(rsnh);
    }

    /**
     * Don't return any non-contactDirect handles unless all of them are.
     */
    @Override
    protected List<NodeHandle> getNearHandlesHelper(List<NodeHandle> handles) {
        ArrayList<NodeHandle> contactDirect = new ArrayList<>();
        ArrayList<NodeHandle> notContactDirect = new ArrayList<>();

        for (NodeHandle foo : handles) {
            RendezvousSocketNodeHandle rsnh = (RendezvousSocketNodeHandle) foo;
            if (rsnh.canContactDirect()) {
                contactDirect.add(foo);
            } else {
                notContactDirect.add(foo);
            }
        }
        if (contactDirect.isEmpty()) return notContactDirect;
        return contactDirect;
    }


    /**
     * This is the first step, cull out the bootHandles that we can't use good.
     */
    @Override
    public Cancellable getNearHandles(Collection<NodeHandle> bootHandles,
                                      Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
        ArrayList<NodeHandle> newBootHandles = new ArrayList<>();
        logger.info("Booting off of " + bootHandles.size() + " nodes. " + bootHandles);
        for (NodeHandle handle : bootHandles) {
            if (useHandle(handle)) {
                newBootHandles.add(handle);
            } else {
                logger.warn("Can't use " + handle + " it is firewalled.");
            }
        }
        return super.getNearHandles(newBootHandles, deliverResultToMe);
    }

    @Override
    public Cancellable getLeafSet(final NodeHandle input,
                                  final Continuation<LeafSet, Exception> c) {
        if (ignore(input, c)) return null;
        return super.getLeafSet(input, new Continuation<LeafSet, Exception>() {

            public void receiveResult(LeafSet result) {
                for (NodeHandle handle : result) {
                    if (!useHandle(handle)) {
                        logger.debug("getLeafSet(" + input + ") Dropping " + handle);
                        result.remove(handle);
                    }
                }
                c.receiveResult(result);
            }

            public void receiveException(Exception exception) {
                c.receiveException(exception);
            }
        });
    }

    @Override
    public Cancellable getRouteRow(final NodeHandle input, final short row,
                                   final Continuation<RouteSet[], Exception> c) {
        if (ignore(input, c)) return null;
        return super.getRouteRow(input, row, new Continuation<RouteSet[], Exception>() {

            public void receiveResult(RouteSet[] result) {
                for (int ctr = 0; ctr < result.length; ctr++) {
                    RouteSet rs = result[ctr];
                    if (rs != null) {
                        for (NodeHandle handle : rs) {
                            if (handle != null && !useHandle(handle)) {
                                logger.debug("getRouteRow(" + input + "," + row + ") Dropping " + handle + " because it is FireWalled");
                                rs.remove(handle);
                            }
                        }
                        if (rs.isEmpty()) result[ctr] = null;
                    }
                }
                c.receiveResult(result);
            }

            public void receiveException(Exception exception) {
                c.receiveException(exception);
            }
        });
    }

    @Override
    public Cancellable getNearest(NodeHandle seed,
                                  Continuation<Collection<NodeHandle>, Exception> retToMe) {
        if (ignore(seed, retToMe)) return null;
        return super.getNearest(seed, retToMe);
    }

    @Override
    public Cancellable getProximity(NodeHandle handle,
                                    Continuation<Integer, IOException> c, int timeout) {
        if (!useHandle(handle)) {
            c.receiveResult(Integer.MAX_VALUE);
            return null;
        }
        return super.getProximity(handle, c, timeout);
    }
}
