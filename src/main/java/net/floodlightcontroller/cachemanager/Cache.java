package net.floodlightcontroller.cachemanager;

public class Cache {
	private String ip;
	private String mac;
	private short port;
	
	public Cache(String ip, String mac, short port) {
		this.ip = ip;
		this.mac = mac;
		this.port = port;
	}
	
	public String getIP() {
		return this.ip;
	}
	
	public String getMAC() {
		return this.mac;
	}
	
	public short getPort() {
		return this.port;
	}
}
