package net.floodlightcontroller.flowcreator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
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

public class FlowCreator implements IFloodlightModule, IFlowCreatorService {

	protected static Logger log;
	private static IOFSwitchService switchService;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFlowCreatorService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IFlowCreatorService.class, this);
		return m;
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
	
	@Override
	public void writeFlowModDDoS(IOFSwitch sw, int port_src, String MAC_src, String MAC_dst, String IP_src, String IP_dst) {
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
		
		//Match
		Match myMatch = factory.buildMatch()
			    .setExact(MatchField.IN_PORT, OFPort.of(port_src))
			    .setExact(MatchField.ETH_TYPE, EthType.IPv4) // Needed when specifying IPv4 address
			    .setExact(MatchField.ETH_SRC, MacAddress.of(MAC_src))
			    .setExact(MatchField.ETH_DST, MacAddress.of(MAC_dst))
			    .setExact(MatchField.IPV4_SRC, IPv4Address.of(IP_src))
			    .setExact(MatchField.IPV4_DST, IPv4Address.of(IP_dst))
			    .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
			    .setExact(MatchField.TCP_DST, TransportPort.of(80))
			    .build();
		
		//Action
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActions actions = factory.actions();
		OFOxms oxms = factory.oxms();
		
		/* Use OXM to modify header fields. */
		OFActionSetField setEthDst = actions.buildSetField()
		    .setField(
		        oxms.buildEthDst()
		        .setValue(MacAddress.of("00:00:00:00:00:05"))
		        .build()
		    )
		    .build();
		actionList.add(setEthDst);
		
		OFActionSetField setIPDst = actions.buildSetField()
			    .setField(
			        oxms.buildIpv4Dst()
			        .setValue(IPv4Address.of("10.0.0.5"))
			        .build()
			    )
			    .build();
		actionList.add(setIPDst);
		
		/* Output to a port is also an OFAction, not an OXM. */
		OFActionOutput output_port = actions.buildOutput()
		    .setPort(OFPort.of(5))
		    .build();
		actionList.add(output_port);
		
		
		//Flow
		OFFlowAdd flowAdd = factory.buildFlowAdd()
			    .setBufferId(OFBufferId.NO_BUFFER)
			    .setHardTimeout(3600)
			    .setIdleTimeout(10)
			    .setPriority(32768)
			    .setMatch(myMatch)
			    .setActions(actionList)
			    .setTableId(TableId.of(0))
			    .build();
		
		if (sw != null) {
			log.info("Writing to switch");
			sw.write(flowAdd);
		}
		else {
			log.warn("No switch connected !");
		}		
	}	
}
	
