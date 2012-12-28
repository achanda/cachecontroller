package net.floodlightcontroller.cachemanager;

import java.util.HashMap;

public class CacheDictionary {
	private HashMap<String,String> cache;
	
	public CacheDictionary() {
		cache = new HashMap<String,String>();
		cache.put("chocolate.jpg", "192.168.122.11");
		cache.put("rc.jpg", "192.168.122.21");
	}
	
	public String get(String key) {
		return cache.get(key);
	}
	
	public boolean has(String key) {
		if ((cache.get(key)) == null)
			return false;
		else
			return true;
	}
}