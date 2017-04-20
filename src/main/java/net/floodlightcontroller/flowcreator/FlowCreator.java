package net.floodlightcontroller.flowcreator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
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

	protected static Logger log;
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
		log = LoggerFactory.getLogger(FlowCreator.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		
	}
	
	public static void writeFlowMod(IOFSwitch sw) {
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
		
		Match myMatch = factory.buildMatch()
			    .setExact(MatchField.IN_PORT, OFPort.of(1))
			    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
			    .setExact(MatchField.IPV4_SRC, IPv4Address.of("10.0.0.1"))
			    .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
			    .setExact(MatchField.TCP_DST, TransportPort.of(80))
			    .build();
		
		
		OFFlowAdd flowAdd = factory.buildFlowAdd()
			    .setBufferId(OFBufferId.NO_BUFFER)
			    .setHardTimeout(3600)
			    .setIdleTimeout(10)
			    .setPriority(32768)
			    .setMatch(myMatch)
			    .setTableId(TableId.of(1))
			    .build();
		
		sw = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
		if (sw != null) {
			log.info("Writing to switch");
			sw.write(flowAdd);
		}
		else {
			log.warn("No switch connected !");
		}		
	}
}
	
