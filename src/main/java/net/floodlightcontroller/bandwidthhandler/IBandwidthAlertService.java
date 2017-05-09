package net.floodlightcontroller.bandwidthhandler;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public interface IBandwidthAlertService extends IFloodlightService {
	
	/**
	 * Sends notifications to bandwidth alert listeners
	 */
	
	public void addBandwidthAlertListener(IBandwidthAlertListener listener);

	public void notifyListeners(String type, float value, NodePortTuple dpid_port);

}
