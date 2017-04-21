package net.floodlightcontroller.flowcreator;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFlowCreatorService extends IFloodlightService {
	public void writeFlowMod(IOFSwitch sw);
}
