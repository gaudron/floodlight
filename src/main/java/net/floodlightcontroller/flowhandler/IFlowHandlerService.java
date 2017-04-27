package net.floodlightcontroller.flowhandler;

import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFlowHandlerService extends IFloodlightService {
	public void writeFlowModDDoS(IOFSwitch sw, int port_src, String MAC_str, String MAC_dst, String IP_src, String IP_dst, String MAC_cleaner);
	
	public void writeFlowModCleaner(IOFSwitch sw, String MAC_str);
}
