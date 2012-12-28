package net.floodlightcontroller.cachemanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import redis.clients.jedis.Jedis;

public class CacheManagerResource extends ServerResource {
	private ArrayList<Cache> caches = new ArrayList<Cache>() {
		{
			add(new Cache("192.168.122.21", "08:00:27:17:84:D7",
					(short) 7001));
			add(new Cache("192.168.122.24", "08:00:27:D3:17:EF",
					(short) 7001));
		}
	};
	
	//Jedis conn = new Jedis("localhost");
	
	@Get("json")
	public String process() {
		String data = (String) getRequestAttributes().get("request");
		String[] tokens = data.split(","); // assuming action,ip,file

		try {
			if (tokens[0].equalsIgnoreCase("retreive"))
				return retrieve(tokens[1], tokens[2], tokens[3]);
			else if (tokens[0].equalsIgnoreCase("save"))
				return save(tokens[1], tokens[2]);
			else if (tokens[0].equalsIgnoreCase("get"))
				return getfilename(tokens[1], tokens[2]);
			else if (tokens[0].equalsIgnoreCase("allrequests"))
				return showallrequests();
			else if (tokens[0].equalsIgnoreCase("allcache"))
				return showallcache();
			else if (tokens[0].equalsIgnoreCase("clear"))
				return clear();
			else if (tokens[0].equalsIgnoreCase("selectcache")) {
				IFloodlightProviderService.connection.set("selected", tokens[1]);
				return tokens[1];
			}
			else
				return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private String clear() {
		IFloodlightProviderService.requests.clear();
		IFloodlightProviderService.cache.clear();
		return "done";
	}

	private String showallrequests() {
		return IFloodlightProviderService.requests.toString();
	}

	private String showallcache() {
		return IFloodlightProviderService.cache.toString();
	}

	private void writeForkFlow(String server_ip, String proxy_ip, Cache selectedCache) {
		String FORK_SWITCH = "00:00:d4:ae:52:6e:3c:06";
		OFMatch match = new OFMatch();
		IFloodlightProviderService floodlightProvider = (IFloodlightProviderService) getContext()
				.getAttributes().get(IFloodlightProviderService.class.getCanonicalName());
		assert (floodlightProvider != null);
		assert (floodlightProvider.getSwitches() != null);
		IOFSwitch cacheSwitch = floodlightProvider.getSwitches()
				.get(HexString.toLong(FORK_SWITCH));
		assert (cacheSwitch != null);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(IPv4.toIPv4Address(proxy_ip));
		match.setNetworkSource(IPv4.toIPv4Address(server_ip));
		match.setWildcards(~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_SRC_MASK | OFMatch.OFPFW_NW_DST_MASK));

		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue()));
		actions.add(new OFActionNetworkLayerDestination(IPv4
				.toIPv4Address(selectedCache.getIP())));
		actions.add(new OFActionDataLayerDestination(Ethernet
				.toMACAddress(selectedCache.getMAC())));
		actions.add(new OFActionTransportLayerDestination(selectedCache.getPort()));
		actions.add(new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue()));

		OFFlowMod mod = (OFFlowMod) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		mod.setMatch(match)
				.setCookie(4321)
				.setCommand(OFFlowMod.OFPFC_ADD)
				.setIdleTimeout((short) 5)
				//.setHardTimeout((short) 5)
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
			cacheSwitch.write(mod, null);
			cacheSwitch.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Cache selectCache(String ip) {
		for (Cache c : caches) {
			if (c.getIP().equalsIgnoreCase(ip))
				return c;
		}
		return null;
	}

	// query cache dictionary to get destination IP (query from proxy)
	public String retrieve(String file, String server_ip, String proxy_ip) {
		String temp = IFloodlightProviderService.cache.get(file);
		// this is where the controller decides to cache
		if (temp == null) {
			// consider putting this in a seperate thread
			//Cache selected = selectedCache;
			writeForkFlow(server_ip, proxy_ip, //caches.get(0));
					selectCache(IFloodlightProviderService.connection.get("selected")));
			return "none";
		} else
			return temp;
	}

	// saves a HTTP request (comes from proxy)
	public String save(String serverip, String totalpath) {
		IFloodlightProviderService.requests.put(serverip, new HTTPRequest(
				totalpath));
		return IFloodlightProviderService.requests.toString();
	}

	// returns filename from a HTTP request (query from cache)
	public String getfilename(String serverip, String cacheip) {
		String data = serverip;
		Collection<HTTPRequest> requests = IFloodlightProviderService.requests
				.get(data);
		HTTPRequest temprequest = getRequest(requests);
		String filename = temprequest.getFileName();
		String totalpath = temprequest.getTotalPath();
		IFloodlightProviderService.cache.put(totalpath, cacheip);
		return filename;
	}

	private HTTPRequest getRequest(Collection<HTTPRequest> requests) {
		ArrayList<HTTPRequest> temp = new ArrayList<HTTPRequest>();
		for (HTTPRequest r : requests) {
			if (r.getFlag() == false)
				temp.add(r);
		}
		Collections.min(temp, new HTTPRequestComparator());
		temp.get(0).setFlag(true);
		return temp.get(0);
	}
}