package net.floodlightcontroller.cachemanager;

import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

public class PacketParser {
	private final String HTTP_TOKEN = "HTTP";
	private final String ARP_TOKEN = "ARP";
	private final String TCP_TOKEN = "TCP";
	private static final String EMPTY = "";
	private final String GET_TOKEN = "GET";
	private final String LINE_END_TOKEN = "\r\n";

	private int sourceAddress;
	private int destinationAddress;
	private short sourcePort;
	private short destinationPort;

	private String innerPacketNames;
	private List<IPacket> innerPackets;

	public PacketParser(BasePacket pkt) {
		assert (pkt != null);
		innerPackets = new ArrayList<IPacket>();
		innerPacketNames = computeInnerPackets(pkt);

		// Find IP packet in the list
		for (IPacket packet : innerPackets) {
			if (packet instanceof IPv4) {
				this.sourceAddress = ((IPv4) packet).getSourceAddress();
				this.destinationAddress = ((IPv4) packet)
						.getDestinationAddress();
				break;
			}
		}

		// Find TCP packet in the list
		for (IPacket packet : innerPackets) {
			if (packet instanceof TCP) {
				this.sourcePort = ((TCP) packet).getSourcePort();
				this.destinationPort = ((TCP) packet).getDestinationPort();
				break;
			}
		}
	}

	public String getSourceIP() {
		return IPv4.fromIPv4Address(sourceAddress);
	}

	public String getDestinationIP() {
		return IPv4.fromIPv4Address(destinationAddress);
	}

	public short getSourcePort() {
		return this.sourcePort;
	}

	public short getDestinationPort() {
		return this.destinationPort;
	}

	public int innerPacketListSize() {
		return innerPackets.size();
	}

	// returns the inner most packet
	public IPacket getInnerPacket() {
		return innerPackets.get(innerPackets.size() - 1);
	}

	public String getInnerPacketNames() {
		return innerPacketNames;
	}
	
	public boolean isARP() {
		String full = this.getInnerPacketNames();
		String sub = this.ARP_TOKEN;
		if (full == null)
			return false;
		return full.indexOf(sub) != -1;
	}
	
	public boolean isTCP() {
		String full = this.getInnerPacketNames();
		String sub = this.TCP_TOKEN;
		if (full == null)
			return false;
		return full.indexOf(sub) != -1;
	}

	public boolean isHTTP() {
		String full = this.getInnerPacketData();
		String sub = this.HTTP_TOKEN;
		if (full == null)
			return false;
		return full.indexOf(sub) != -1;
	}

	// returns data from inner most packet (if it is a data packet)
	public String getInnerPacketData() {
		IPacket ipkt = this.getInnerPacket();
		if (ipkt instanceof Data)
			return new String(((Data) ipkt).getData());
		else
			return "Not a data packet";
	}

	public String[] parseHTTP() {
		String data = this.getInnerPacketData();
		return data.split(LINE_END_TOKEN);
	}
	
	public static String getGETParameter(String data) {
		int begin = data.indexOf("GET");
		int end = data.indexOf("HTTP");
		if ((begin == -1) || (end == -1))
			return EMPTY;
		return data.substring(begin+3, end).trim();
	}

	// returns a string which has fully qualified names of all inner packet
	// staring from Ethernet
	private String computeInnerPackets(BasePacket pkt) {
		innerPackets.add(pkt);
		String packets = pkt.getClass().getName() + "\n";
		IPacket inner = pkt.getPayload();
		while (inner instanceof BasePacket) {
			innerPackets.add(inner);
			packets = packets + inner.getClass().getName() + "\n";
			inner = inner.getPayload();
		}
		return packets;
	}
}