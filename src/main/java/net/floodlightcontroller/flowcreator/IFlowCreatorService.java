package net.floodlightcontroller.flowcreator;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFlowCreatorService extends IFloodlightService {
	public void writeFlowModDDoS(IOFSwitch sw, int port_src, String MAC_str, String MAC_dst, String IP_src, String IP_dst);
}
