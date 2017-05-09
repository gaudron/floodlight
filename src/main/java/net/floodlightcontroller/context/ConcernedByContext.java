package net.floodlightcontroller.context;

import java.util.List;

import org.projectfloodlight.openflow.types.IPv4Address;

public abstract class ConcernedByContext {
	
	private IPv4Address ip;
	private List<Context> contexts;
	
	public ConcernedByContext(IPv4Address ip, Context context) {
		this.ip = ip;	
	}
	
	public List<Context> getContexts() {
		return this.contexts;
	}
	
	public IPv4Address getIPv4Address() {
		return this.ip;
	}

}
