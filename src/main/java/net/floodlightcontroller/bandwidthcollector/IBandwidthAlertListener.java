package net.floodlightcontroller.bandwidthcollector;

import net.floodlightcontroller.core.types.NodePortTuple;

public interface IBandwidthAlertListener {
	
	/**
	 * Deals with bandwidth alerts
	 */
	
	public void receiveBandwidthNotification(String type, long value, NodePortTuple dpid_port);
}
