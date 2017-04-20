package net.floodlightcontroller.mactracker;

import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MACTracker implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected Set macAddresses;
	protected static Logger logger;
	
	public String getName() {
		return MACTracker.class.getSimpleName();
	}

	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	public Collection getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	public Map getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	public Collection getModuleDependencies() {
		Collection l = new ArrayList();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = (IFloodlightProviderService) context.getServiceImpl(IFloodlightProviderService.class);
		macAddresses = new ConcurrentSkipListSet();
		logger = LoggerFactory.getLogger(MACTracker.class);
	}

	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        Long sourceMACHash = eth.getSourceMACAddress().getLong();
        if (!macAddresses.contains(sourceMACHash)) {
            macAddresses.add(sourceMACHash);
            logger.info("MAC Address: {} seen on switch: {}",
                    eth.getSourceMACAddress().toString(),
                    sw.getId().toString());
        }
        return Command.CONTINUE;
	}
	

}
