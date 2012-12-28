package net.floodlightcontroller.cachemanager;

import java.sql.Timestamp;
import java.util.Date;

public class HTTPRequest {
	private String filename;
	private String totalpath;
	private boolean flag;
	private Timestamp timestamp;
	
	public HTTPRequest(String path) {
		totalpath = path;
		String[] fragments = path.split("%2F");
		filename = fragments[fragments.length - 1];
		flag = false;
		Date date= new Date();
		timestamp = new Timestamp(date.getTime());
	}
	
	public boolean getFlag() {
		return flag;
	}
	
	public void setFlag(boolean value) {
		flag = value;
	}
	
	public Timestamp getTimestamp() {
		return timestamp;
	}
	
	public String getFileName() {
		return filename;
	}
	
	public String getTotalPath() {
		return totalpath;
	}
	
	public String toString() {
		return "Filename: " + filename + "Path: "+ totalpath + " " + "Flag: " + flag + " " + "Timestamp: " + timestamp.toString();
	}
}