package net.floodlightcontroller.cachemanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheManager implements IFloodlightModule, ICacheManagerService,
		IOFMessageListener {
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApi;
	protected IDeviceService deviceManager;
	protected IRoutingService routingService;
	protected IStaticFlowEntryPusherService staticFlowPusher;
	protected static Logger logger;

	private String CLIENT_IP = "192.168.122.23";
	private String CLIENT_MAC = "08:00:27:15:E2:AF";
	private List<String> CACHE_IP = Arrays.asList("192.168.122.21",
			"192.168.122.24");
	private String PROXY_IP = "192.168.122.22";
	private String PROXY_MAC = "08:00:27:3d:3f:84";
	private String FORK_SWITCH = "00:00:d4:ae:52:6e:3c:06";
	private String INGRESS_SWITCH = "00:00:d4:ae:52:6e:32:58";

	private Short[] PROXY_PORTS = { 6001 };

	static int counter;

	public String getName() {
		return "CacheManager";
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ICacheManagerService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(ICacheManagerService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(IStaticFlowEntryPusherService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(CacheManager.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		staticFlowPusher = context
				.getServiceImpl(IStaticFlowEntryPusherService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		restApi.addRestletRoutable(new CacheManagerWebRoutable());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		BasePacket bpkt = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		PacketParser parsed = new PacketParser(bpkt);

		if (parsed.isTCP()) {
			logger.debug("******CacheManager got a packet*******" + "Src "
					+ parsed.getSourceIP() + ":" + parsed.getSourcePort()
					+ "DSt " + parsed.getDestinationIP() + ":"
					+ parsed.getDestinationPort() + "From " + sw.getStringId());

			String src_ip = parsed.getSourceIP();
			// if the client is sending TCP packets, write proxy flow
			// CLIENT_IP should actually be a subnet for clients
			// this should check if client_ip belongs to CLIENT_IP
			if (isClient(src_ip)) {
				IOFSwitch source = floodlightProvider.getSwitches().get(
						getSourceSwitch(Ethernet.toMACAddress(CLIENT_MAC)));

				this.writeProxyFlow(source, cntx, src_ip,
						parsed.getDestinationPort(), parsed.getDestinationIP());

				OFPacketIn pi = (OFPacketIn) msg;
				
				
				logger.debug("writepacket");
				OFPacketOut po = (OFPacketOut) floodlightProvider
						.getOFMessageFactory().getMessage(OFType.PACKET_OUT);

				po.setBufferId(pi.getBufferId()).setInPort(pi.getInPort());
				OFActionOutput action3 = new OFActionOutput()
						.setPort((short) OFPort.OFPP_FLOOD.getValue());
				po.setActions(Collections.singletonList((OFAction) action3));
				po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

				if (pi.getBufferId() == 0xffffffff) {
					byte[] packetData = pi.getPacketData();
					po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
							+ po.getActionsLength() + packetData.length));
					po.setPacketData(packetData);
				} else {
					po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
							+ po.getActionsLength()));
				}
				
				try {
					sw.write(po, cntx);
					sw.flush();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}

			// if the server is sending TCP packets, write fork flow
			// the check for ingress switch prevents flows being overwritten as
			// the packet goes to downstream switches
			if (isServer(src_ip)
					&& sw.getStringId().equalsIgnoreCase(INGRESS_SWITCH)
					&& parsed.isHTTP()) {
				logger.debug("Server responding");
				logger.debug(sw.getStringId());
				counter++;
				logger.debug("************************************************"
						+ Integer.toString(counter));
				// Cache selected = selectCache();
				/*
				 * IOFSwitch cacheSwitch = floodlightProvider.getSwitches()
				 * .get(HexString.toLong(FORK_SWITCH)); // IOFSwitch
				 * ingressSwitch = // floodlightProvider.getSwitches().get( //
				 * HexString.toLong(INGRESS_SW)); // IOFSwitch proxySwitch = //
				 * floodlightProvider.getSwitches().get( //
				 * getSourceSwitch(Ethernet.toMACAddress(PROXY_MAC))); //
				 * logger.debug(cacheSwitch.getStringId()); //
				 * logger.debug(ingressSwitch.getStringId()); //
				 * logger.debug(proxySwitch.getStringId()); // Route
				 * server_to_cache = routingService.getRoute( //
				 * ingressSwitch.getId(), cacheSwitch.getId()); // Route
				 * server_to_proxy = routingService.getRoute( //
				 * ingressSwitch.getId(), proxySwitch.getId()); //
				 * assert(server_to_cache != null); // assert(server_to_proxy !=
				 * null); // logger.debug(server_to_cache.toString()); //
				 * logger.debug(server_to_proxy.toString()); // IOFSwitch ahaa =
				 * getForkingSwitch(server_to_cache, // server_to_proxy); //
				 * logger.debug(ahaa.getStringId()); // writeForkFlow(ahaa,
				 * parsed.getSourceIP(), PROXY_IP, // selected); // //
				 * writeForkFlow(cacheSwitch, parsed.getSourceIP(), // PROXY_IP,
				 * // // selected); }
				 */
			}
		}
		return Command.CONTINUE;
	}

	// returns dpid and ingress port for the forking switch
	private SwitchPort getForkingSwitch(Route one, Route two) {
		List<Link> common = intersection(one.getPath(), two.getPath());
		Link lastLink = common.get(common.size() - 1);
		return new SwitchPort(lastLink.getDst(), lastLink.getDstPort());
	}

	private List<Link> intersection(List<Link> one, List<Link> two) {
		List<Link> temp = new ArrayList<Link>();
		for (Link l : one) {
			if (two.contains(l)) {
				temp.add(l);
			}
		}
		return temp;
	}

	private boolean isCache(String ip) {
		if (CACHE_IP.contains(ip))
			return true;
		else
			return false;
	}

	private boolean isProxy(String ip) {
		if (ip.equalsIgnoreCase(PROXY_IP))
			return true;
		else
			return false;
	}

	private boolean isClient(String ip) {
		if (ip.equalsIgnoreCase(CLIENT_IP))
			return true;
		else
			return false;
	}

	private boolean isServer(String ip) {
		if (!isCache(ip) && !isProxy(ip) && !isClient(ip))
			return true;
		else
			return false;
	}

	private void writeForkFlow(IOFSwitch sw, String server_ip, String proxy_ip,
			Cache selected) {
		OFMatch match = new OFMatch();
		// match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(IPv4.toIPv4Address(proxy_ip));
		match.setNetworkSource(IPv4.toIPv4Address(server_ip));
		match.setWildcards(~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_SRC_MASK | OFMatch.OFPFW_NW_DST_MASK));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue()));
		actions.add(new OFActionNetworkLayerDestination(IPv4
				.toIPv4Address(selected.getIP())));
		actions.add(new OFActionDataLayerDestination(Ethernet
				.toMACAddress(selected.getMAC())));
		actions.add(new OFActionTransportLayerDestination(selected.getPort()));
		actions.add(new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue()));

		OFFlowMod mod = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		mod.setMatch(match)
				.setCookie(4321)
				.setCommand(OFFlowMod.OFPFC_ADD)
				.setIdleTimeout((short) 5)
				.setHardTimeout((short) 5)
				.setPriority((short) 32768)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE)
				.setFlags((short) (1 << 0))
				.setActions(actions)
				.setLength(
						(short) (OFFlowMod.MINIMUM_LENGTH
								+ 2
								* OFActionOutput.MINIMUM_LENGTH
								+ OFActionNetworkLayerDestination.MINIMUM_LENGTH
								+ OFActionDataLayerDestination.MINIMUM_LENGTH + OFActionTransportLayerDestination.MINIMUM_LENGTH));

		try {
			sw.write(mod, null);
			sw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// TODO: ports and proxy info still hard coded
	private void writeProxyFlow(IOFSwitch sw,
			FloodlightContext cntx, String client_ip, short transport_dst,
			String server_ip) {
		assert (sw != null);
		logger.debug("writeToProxyFlow");

		short proxy_port = 6001;// this.selectAndDelete(PROXY_PORTS);
		OFMatch match1 = new OFMatch();
		match1.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match1.setDataLayerType(Ethernet.TYPE_IPv4);
		match1.setNetworkSource(IPv4.toIPv4Address(client_ip));
		match1.setTransportDestination(transport_dst);
		match1.setWildcards(~(OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_DL_TYPE
				| OFMatch.OFPFW_NW_SRC_MASK | OFMatch.OFPFW_TP_DST));

		List<OFAction> actions1 = new ArrayList<OFAction>();
		actions1.add(new OFActionNetworkLayerDestination(IPv4
				.toIPv4Address(PROXY_IP)));
		actions1.add(new OFActionDataLayerDestination(Ethernet
				.toMACAddress(PROXY_MAC)));
		actions1.add(new OFActionTransportLayerDestination(proxy_port));
		actions1.add(new OFActionOutput().setPort((short) 2));

		OFFlowMod mod1 = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		mod1.setMatch(match1)
				.setCookie(4321)
				.setCommand(OFFlowMod.OFPFC_ADD)
				.setIdleTimeout((short) 0)
				.setHardTimeout((short) 0)
				.setPriority((short) 32768)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE)
				.setFlags((short) (1 << 0))
				.setActions(actions1)
				.setLength(
						(short) (OFFlowMod.MINIMUM_LENGTH
								+ OFActionOutput.MINIMUM_LENGTH
								+ OFActionNetworkLayerDestination.MINIMUM_LENGTH
								+ OFActionDataLayerDestination.MINIMUM_LENGTH + OFActionTransportLayerDestination.MINIMUM_LENGTH));

		logger.debug("writeFromProxyFlow");
		OFMatch match2 = new OFMatch();
		match2.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match2.setDataLayerType(Ethernet.TYPE_IPv4);
		match2.setNetworkSource(IPv4.toIPv4Address(PROXY_IP));
		match2.setNetworkDestination(IPv4.toIPv4Address(client_ip));
		match2.setWildcards(~(OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_DL_TYPE
				| OFMatch.OFPFW_NW_SRC_MASK | OFMatch.OFPFW_NW_DST_MASK));

		List<OFAction> actions2 = new ArrayList<OFAction>();
		actions2.add(new OFActionNetworkLayerSource(IPv4
				.toIPv4Address(server_ip)));
		actions2.add(new OFActionTransportLayerSource(transport_dst));
		actions2.add(new OFActionOutput().setPort((short) 4));

		OFFlowMod mod2 = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		mod2.setMatch(match2)
				.setCookie(4321)
				.setCommand(OFFlowMod.OFPFC_ADD)
				.setIdleTimeout((short) 0)
				.setHardTimeout((short) 0)
				.setPriority((short) 32768)
				.setBufferId(OFPacketOut.BUFFER_ID_NONE)
				.setFlags((short) (1 << 0))
				.setActions(actions2)
				.setLength(
						(short) (OFFlowMod.MINIMUM_LENGTH
								+ OFActionOutput.MINIMUM_LENGTH
								+ OFActionNetworkLayerSource.MINIMUM_LENGTH + OFActionTransportLayerSource.MINIMUM_LENGTH));

		List<OFMessage> msglist = new ArrayList<OFMessage>();
		msglist.add(mod1);
		msglist.add(mod2);

		try {
			sw.write(msglist, cntx);
			sw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// given a MAC, returns the DPID of the switch to which its connected, 0
	// otherwise
	private long getSourceSwitch(byte[] mac) {
		Long ml = Ethernet.toLong(mac);
		Device device = (Device) deviceManager.findDevice(ml, null, null, null,
				null);
		if (device != null) {
			SwitchPort[] attchmentPoints = device.getAttachmentPoints();
			return attchmentPoints[0].getSwitchDPID();
		} else
			return 0;
	}

	// gets the forking switch between (src and one) and (src and two)
	/*
	 * private IOFSwitch getForkingSwitch(byte[] src, byte[] one, byte[] two) {
	 * Device srcDevice = (Device) deviceManager.findDevice(
	 * Ethernet.toLong(src), null, null, null, null); }
	 */

	// does not delete!
	protected short selectAndDelete(Short[] array) {
		Random generator = new Random();
		short temp = (short) generator.nextInt(array.length);
		return temp;
	}

	protected void pushPacket(IOFSwitch sw, OFMatch match, OFPacketIn pi,
			short outport, FloodlightContext cntx) {

		if (pi == null) {
			return;
		} else if (pi.getInPort() == outport) {
			logger.warn(
					"Packet out not sent as the outport matches inport. {}", pi);
			return;
		}

		// The assumption here is (sw) is the switch that generated the
		// packet-in. If the input port is the same as output port, then
		// the packet-out should be ignored.
		if (pi.getInPort() == outport) {
			if (logger.isDebugEnabled()) {
				logger.debug("Attempting to do packet-out to the same "
						+ "interface as packet-in. Dropping packet. "
						+ " SrcSwitch={}, match = {}, pi={}", new Object[] {
						sw, match, pi });
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("PacketOut srcSwitch={} match={} pi={}", new Object[] {
					sw, match, pi });
		}

		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);

		// set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outport, (short) 0xffff));

		po.setActions(actions).setActionsLength(
				(short) OFActionOutput.MINIMUM_LENGTH);
		short poLength = (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

		po.setBufferId(pi.getBufferId());

		po.setInPort(pi.getInPort());

		// If the buffer id is none or the switch doesn's support buffering
		// we send the data with the packet out
		if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
			byte[] packetData = pi.getPacketData();
			poLength += packetData.length;
			po.setPacketData(packetData);
		}

		po.setLength(poLength);

		try {
			sw.write(po, cntx);
		} catch (IOException e) {
			logger.error("Failure writing packet out", e);
		}
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		if ((name.equalsIgnoreCase("devicemanager") && (type
				.equals(OFType.PACKET_IN))))
			return true;
		else
			return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}
}