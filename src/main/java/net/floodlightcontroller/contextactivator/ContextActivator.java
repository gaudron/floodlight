package net.floodlightcontroller.contextactivator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.bandwidthcollector.BandwidthCollector;
import net.floodlightcontroller.bandwidthcollector.IBandwidthAlertListener;
import net.floodlightcontroller.bandwidthcollector.IBandwidthAlertService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public class ContextActivator implements IFloodlightModule, IBandwidthAlertListener {
	
	
	// Services used
	protected IBandwidthAlertService bandwidthAlertsProvider;

	private static final Logger log = LoggerFactory.getLogger(BandwidthCollector.class);
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IBandwidthAlertService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		bandwidthAlertsProvider = context.getServiceImpl(IBandwidthAlertService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		bandwidthAlertsProvider.addBandwidthAlertListener(this);
	}
	
	
	
	// Implementation of ContextActivator	
	@Override
	public void receiveBandwidthNotification(String type, NodePortTuple dpid_port) {
		log.info("Alert of type: " + type + " received from " + dpid_port);
	}

}
