package net.floodlightcontroller.bandwidthhandler;

import net.floodlightcontroller.core.types.NodePortTuple;

public interface IBandwidthAlertListener {
	
	/**
	 * Deals with bandwidth alerts
	 */
	
	public void receiveBandwidthNotification(String type, float value, NodePortTuple dpid_port);
}
