package net.floodlightcontroller.flowhandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
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
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.staticentry.*;

public class FlowHandler implements IFloodlightModule, IFlowHandlerService {

	protected static Logger log;
	private static IOFSwitchService switchService;
	protected IDeviceService deviceService;


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFlowHandlerService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IFlowHandlerService.class, this);
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
		log = LoggerFactory.getLogger(FlowHandler.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

	}

	@Override
	public void writeFlowModCleaner(IOFSwitch sw, String MAC_src){
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
		Match myMatch = factory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) // Needed when specifying IPv4 address
				.setExact(MatchField.ETH_SRC, MacAddress.of(MAC_src)).build();


		//Flow
		OFFlowAdd flowAdd = factory.buildFlowAdd()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setHardTimeout(3600)
				.setIdleTimeout(15)
				.setPriority(32768)
				.setMatch(myMatch)
				.setTableId(TableId.of(0))
				.build();

		if (sw != null) {
			log.info("Writing cleaner rule to switch");
			sw.write(flowAdd);
		}
		else {
			log.warn("No switch connected !");
		}

	}

	@Override
	public void writeFlowModDDoS(IOFSwitch sw, int port_src, String MAC_src, String MAC_dst, 
			String IP_src, String IP_dst, String MAC_cleaner) {
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

		if(MAC_cleaner != "10.0.0.5"){
			//Match
			Match myMatch = factory.buildMatch()
					.setExact(MatchField.IN_PORT, OFPort.of(port_src))
					.setExact(MatchField.ETH_TYPE, EthType.IPv4) // Needed when specifying IPv4 address
					.setExact(MatchField.ETH_SRC, MacAddress.of(MAC_src))
					.setExact(MatchField.ETH_DST, MacAddress.of(MAC_dst))
					.setExact(MatchField.IPV4_SRC, IPv4Address.of(IP_src))
					.setExact(MatchField.IPV4_DST, IPv4Address.of(IP_dst))
					//.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
					//.setExact(MatchField.TCP_DST, TransportPort.of(80))
					.build();

			//Action
			ArrayList<OFAction> actionList = new ArrayList<OFAction>();
			OFActions actions = factory.actions();
			OFOxms oxms = factory.oxms();

			/* Use OXM to modify header fields. */
			OFActionSetField setEthDst = actions.buildSetField()
					.setField(
							oxms.buildEthDst()
							.setValue(MacAddress.of(MAC_cleaner))
							.build()
							)
					.build();
			actionList.add(setEthDst);

			/*OFActionSetField setIPDst = actions.buildSetField()
					.setField(
							oxms.buildIpv4Dst()
							.setValue(IPv4Address.of("10.0.0.5"))
							.build()
							)
					.build();
			actionList.add(setIPDst);*/


			/*Iterator<? extends IDevice> device = deviceService.queryDevices(MacAddress.of("00:00:00:00:00:05"), VlanVid.ZERO, 
					IPv4Address.NONE, IPv6Address.NONE, DatapathId.NONE, OFPort.of(1));
			if (!device.hasNext()) { 
				log.error("The target machine is not in the network ");
			}
			else {
				IPv4Address[] port_cleaner = device.next().getIPv4Addresses();
				log.info("IDevice:" + port_cleaner.length);
			}*/

			/*Collection<? extends IDevice> devices = deviceService.getAllDevices();
			Iterator<? extends IDevice> device = devices.iterator();
			while(device.hasNext()){
				log.info(device.next().getMACAddressString());
			}*/


			/* Output to a port is also an OFAction, not an OXM. */
			OFActionOutput output_port = actions.buildOutput()
					.setPort(OFPort.of(5))
					.build();
			actionList.add(output_port);


			//Flow
			OFFlowAdd flowAdd = factory.buildFlowAdd()
					.setBufferId(OFBufferId.NO_BUFFER)
					.setHardTimeout(3600)
					.setIdleTimeout(15)
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


	@Override
	public void writeNewFlowModCleaner(IOFSwitch sw, String IP_victim, String MAC_cleaner) {
		OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
		OFPort port = null;

		//Match
		Match myMatch = factory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) // Needed when specifying IPv4 address
				.setExact(MatchField.IPV4_DST, IPv4Address.of(IP_victim))
				//.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				//.setExact(MatchField.TCP_DST, TransportPort.of(80))
				.build();

		//Action
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActions actions = factory.actions();
		OFOxms oxms = factory.oxms();

		/* Use OXM to modify header fields. */
		OFActionSetField setEthDst = actions.buildSetField()
				.setField(
						oxms.buildEthDst()
						.setValue(MacAddress.of(MAC_cleaner))
						.build()
						)
				.build();
		actionList.add(setEthDst);

		IDevice find_device = deviceService.findDevice(MacAddress.of(MAC_cleaner), VlanVid.ZERO, IPv4Address.NONE, IPv6Address.NONE, DatapathId.NONE, OFPort.ZERO);
		if(find_device != null){
			log.info("Find_device: " + find_device.getMACAddressString());
			port = find_device.getAttachmentPoints()[0].getPortId();
		}
		else {
			log.info("find_device null");
		}

		/*Collection<? extends IDevice> devices = deviceService.getAllDevices(); // all machines 
			Iterator<? extends IDevice> device = devices.iterator();
			while(device.hasNext()){
				IDevice current_device = device.next();
				log.info(current_device.getMACAddressString());
				if(current_device.getMACAddressString().equals(MAC_cleaner)){
					log.info("Device found for dpid " + current_device.getAttachmentPoints()[0].getNodeId() + 
						" and port " + current_device.getAttachmentPoints()[0].getPortId()); 
					}
			}*/

		/* Output to a port is also an OFAction, not an OXM. */
		OFActionOutput output_port = actions.buildOutput()
				.setPort(port)
				.build();
		actionList.add(output_port);


		//Flow
		OFFlowAdd flowAdd = factory.buildFlowAdd()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setHardTimeout(3600)
				.setIdleTimeout(15)
				.setPriority(32000)
				.setMatch(myMatch)
				.setActions(actionList)
				.setCookie(U64.of(0x01))
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

	@Override
	public void deleteFlow(OFFlowStatsEntry rule, U64 cookie, IOFSwitch sw){
		
		if(rule.getCookie().equals(cookie)){
			
			OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

			//Match
			Match myMatch = rule.getMatch();
			log.info("myMatch : " + myMatch);
			/*Match myMatch = factory.buildMatch()
					.setExact(MatchField.ETH_TYPE, EthType.IPv4) // Needed when specifying IPv4 address
					.setExact(MatchField.IPV4_DST, IPv4Address.of("10.0.0.2"))
					//.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
					//.setExact(MatchField.TCP_DST, TransportPort.of(80))
					.build();*/
			
			OFFlowDelete flowDel = factory.buildFlowDelete()
					.setMatch(myMatch)
					.setTableId(TableId.of(0))
					.setPriority(32767)
					.build();
			
			//OFFlowDelete flowDelete = FlowModUtils.toFlowDelete((OFFlowMod) rule);
			
			if (sw != null) {
				log.info("Deleting rule in switch");
				sw.write(flowDel);
			}
			else {
				log.warn("No switch connected !");
			}
		}

	}
}

