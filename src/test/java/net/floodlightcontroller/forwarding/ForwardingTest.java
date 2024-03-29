/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.forwarding;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyShort;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.forwarding.Forwarding;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

public class ForwardingTest extends FloodlightTestCase {
    protected MockFloodlightProvider mockFloodlightProvider;
    protected FloodlightContext cntx;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected Forwarding forwarding;
    protected FlowReconcileManager flowReconcileMgr;
    protected ITopologyService topology;
    protected MockThreadPoolService threadPool;
    protected IOFSwitch sw1, sw2;
    protected IDevice srcDevice, dstDevice1, dstDevice2;
    protected OFPacketIn packetIn;
    protected OFPacketOut packetOut;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected int expected_wildcards;
    protected Date currentDate;
    
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Mock context
        cntx = new FloodlightContext();
        mockFloodlightProvider = getMockFloodlightProvider();
        forwarding = getForwarding();
        threadPool = new MockThreadPoolService();
        deviceManager = new MockDeviceManager();
        flowReconcileMgr = new FlowReconcileManager();
        routingEngine = createMock(IRoutingService.class);
        topology = createMock(ITopologyService.class);
        

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, 
                       mockFloodlightProvider);
        fmc.addService(IThreadPoolService.class, threadPool);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(ICounterStoreService.class, new CounterStore());
        fmc.addService(IDeviceService.class, deviceManager);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);

        threadPool.init(fmc);
        forwarding.init(fmc);
        deviceManager.init(fmc);
        flowReconcileMgr.init(fmc);
        threadPool.startUp(fmc);
        deviceManager.startUp(fmc);
        forwarding.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        
        // Mock switches
        sw1 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();

        sw2 = EasyMock.createNiceMock(IOFSwitch.class);  
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();

        //fastWilcards mocked as this constant
        int fastWildcards = 
                OFMatch.OFPFW_IN_PORT | 
                OFMatch.OFPFW_NW_PROTO | 
                OFMatch.OFPFW_TP_SRC | 
                OFMatch.OFPFW_TP_DST | 
                OFMatch.OFPFW_NW_SRC_ALL | 
                OFMatch.OFPFW_NW_DST_ALL |
                OFMatch.OFPFW_NW_TOS;

        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn((Integer)fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

        expect(sw2.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn((Integer)fastWildcards).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

        // Load the switch map
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        mockFloodlightProvider.setSwitches(switches);

        // Build test packet
        testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));

        // Build src and dest devices
        byte[] dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
        byte[] dataLayerDest = 
                ((Ethernet)testPacket).getDestinationMACAddress();
        int networkSource =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getSourceAddress();
        int networkDest = 
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getDestinationAddress();

        currentDate = new Date();
        srcDevice = 
                deviceManager.learnEntity(Ethernet.toLong(dataLayerSource), 
                                          null, networkSource,
                                          1L, 1);
        dstDevice1 = 
                deviceManager.learnEntity(Ethernet.toLong(dataLayerDest), 
                                          null, networkDest,
                                          2L, 3);
        
        // Mock Packet-in
        testPacketSerialized = testPacket.serialize();
        packetIn = 
                ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().
                        getMessage(OFType.PACKET_IN))
                        .setBufferId(-1)
                        .setInPort((short) 1)
                        .setPacketData(testPacketSerialized)
                        .setReason(OFPacketInReason.NO_MATCH)
                        .setTotalLength((short) testPacketSerialized.length);

        // Mock Packet-out
        packetOut = 
                (OFPacketOut) mockFloodlightProvider.getOFMessageFactory().
                    getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(this.packetIn.getBufferId())
            .setInPort(this.packetIn.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput((short) 3, (short) 0));
        packetOut.setActions(poactions)
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setPacketData(testPacketSerialized)
            .setLengthU(OFPacketOut.MINIMUM_LENGTH+
                        packetOut.getActionsLength()+
                        testPacketSerialized.length);

        expected_wildcards = fastWildcards;
        expected_wildcards &= ~OFMatch.OFPFW_IN_PORT & 
                              ~OFMatch.OFPFW_DL_VLAN &
                              ~OFMatch.OFPFW_DL_SRC & 
                              ~OFMatch.OFPFW_DL_DST;
        expected_wildcards &= ~OFMatch.OFPFW_NW_SRC_MASK & 
                              ~OFMatch.OFPFW_NW_DST_MASK;

        // Add the packet to the context store
        IFloodlightProviderService.bcStore.
            put(cntx, 
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
                (Ethernet)testPacket);
        IDeviceService.fcStore.
            put(cntx, 
                IDeviceService.CONTEXT_SRC_DEVICE,
                srcDevice);
    }

    private Forwarding getForwarding() {
        return new Forwarding();
    }

    @Test
    public void testForwardMultiSwitchPath() throws Exception {
        
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<OFMessage> wc2 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<FloodlightContext> bc1 = 
                new Capture<FloodlightContext>(CaptureType.ALL);
        Capture<FloodlightContext> bc2 = 
                new Capture<FloodlightContext>(CaptureType.ALL);

        // Set destination as sw2 and Mock route
        IDeviceService.fcStore.
            put(cntx, 
                IDeviceService.CONTEXT_DST_DEVICE, 
                dstDevice1);

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<Link>());
        route.getPath().add(new Link(1L, (short)3, 2L, (short)1));
        expect(routingEngine.getRoute(1L, 2L)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = 
                (OFFlowMod) mockFloodlightProvider.getOFMessageFactory().
                    getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
            .setMatch(match.clone()
                    .setWildcards(expected_wildcards))
            .setActions(actions)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(2L << 52)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);

        sw1.write(capture(wc1), capture(bc1));
        expectLastCall().anyTimes(); 
        sw2.write(capture(wc2), capture(bc2));
        expectLastCall().anyTimes(); 

        expect(topology.isIncomingBroadcastAllowed(anyLong(), anyShort())).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);
        
        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.
        
        List<OFMessage> msglist = wc1.getValues();
        
        for (OFMessage m: msglist) {
            if (m instanceof OFFlowMod) 
                assertTrue(m.equals(fm1));
            else if (m instanceof OFPacketOut)
                assertTrue(m.equals(packetOut)); 
        }
        
        OFMessage m = wc2.getValue();
        assert (m instanceof OFFlowMod);
        assertTrue(m.equals(fm2));
    }

    @Test
    public void testForwardSingleSwitchPath() throws Exception {        
        // Set destination as local and Mock route
        byte[] dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
        byte[] dataLayerDest = 
                ((Ethernet)testPacket).getDestinationMACAddress();
        int networkSource =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getSourceAddress();
        int networkDest = 
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getDestinationAddress();
        deviceManager.startUp(null);
        
        srcDevice = 
                deviceManager.learnEntity(Ethernet.toLong(dataLayerSource), 
                                          null, networkSource,
                                          1L, 1);
        dstDevice2 = 
                deviceManager.learnEntity(Ethernet.toLong(dataLayerDest), 
                                          null, networkDest,
                                          1L, 3);
        
        IDeviceService.fcStore.
            put(cntx, 
                IDeviceService.CONTEXT_DST_DEVICE, 
                dstDevice2);
        expect(routingEngine.getRoute(1L, 1L)).andReturn(null).atLeastOnce();
        
        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = 
                (OFFlowMod) mockFloodlightProvider.getOFMessageFactory().
                    getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
            .setMatch(match.clone()
                    .setWildcards(expected_wildcards))
            .setActions(actions)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(2L << 52)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH +
                        OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);
        
        expect(topology.isIncomingBroadcastAllowed(anyLong(), anyShort())).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);
    }

    @Test
    public void testForwardNoPath() throws Exception {

        // Set no destination attachment point or route
        // expect no Flow-mod or packet out
                
        // Reset mocks, trigger the packet in, and validate results
        expect(topology.isIncomingBroadcastAllowed(1L, (short)1)).andReturn(true).anyTimes();
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);
    }

}
