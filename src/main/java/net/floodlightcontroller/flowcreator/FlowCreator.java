package net.floodlightcontroller.flowcreator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.staticentry.*;

public class FlowCreator implements IFloodlightModule {

	protected static Logger logger;
	private static IOFSwitchService switchService;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		logger = LoggerFactory.getLogger(FlowCreator.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		//writeFlowMod(switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01")));
	}
	
	/**
	 * Writes a single OFMessage to a switch
	 * @param dpid The datapath ID of the switch to write to
	 * @param message The OFMessage to write.
	 */
	private void writeOFMessageToSwitch(DatapathId dpid, OFMessage message) {
		IOFSwitch sw1 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
		if (sw1 != null) {  // is the switch connected
			logger.info("Writing rule to switch");
			//ofswitch.write(message);
		}
		else {
			logger.info("no switch !");
		}
	}

	private void writeFlowMod(IOFSwitch sw) {
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
		OFFlowMod flowMod;
		flowMod = factory.buildFlowModify().build();
		Match match;
		match = MatchUtils.fromString("eth_type=0x800,ipv4_dst=10.0.0.2", factory.getVersion());
		List<OFAction> actions = new LinkedList<OFAction>();
		actions.add(factory.actions().output(OFPort.CONTROLLER, Integer.MAX_VALUE));

		flowMod = flowMod.createBuilder().setMatch(match)
				.setActions(actions)
				.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
				.setBufferId(OFBufferId.NO_BUFFER)
				.setOutPort(OFPort.ANY)
				.setPriority(Integer.MAX_VALUE)
				.setXid(4)
				.build();
		
		IOFSwitch sw1 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
		if (sw1 != null) {
			sw1.write(flowMod);
		}
		else {
			logger.warn("No switch !");
		}
		
		//writeOFMessageToSwitch(DatapathId.of("00:00:00:00:00:00:00:01"), flowMod);
	}

}
