package net.floodlightcontroller.statistics;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.statistics.web.SwitchStatisticsWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.flowcreator.FlowCreator;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowMod; 
import org.projectfloodlight.openflow.protocol.OFMessage; 
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.State;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

//Without thread, only one switch

public class StatisticsFlowCollector implements IFloodlightModule {
	private static final Logger log = LoggerFactory.getLogger(StatisticsFlowCollector.class);

	private static IOFSwitchService switchService;
	private static IThreadPoolService threadPoolService;
	private static IRestApiService restApiService;

	private static boolean isEnabled = false;
	
	private static int statsInterval = 10; /* could be set by REST API, so not final */
	private static ScheduledFuture<?> statsCollector;

	
	private static final String INTERVAL_STATS_STR = "collectionIntervalStatsSeconds";
	private static final String ENABLED_STR = "enable";

	/**
	 * Run periodically to collect all port statistics. This only collects
	 * bandwidth stats right now, but it could be expanded to record other
	 * information as well. The difference between the most recent and the
	 * current RX/TX bytes is used to determine the "elapsed" bytes. A 
	 * timestamp is saved each time stats results are saved to compute the
	 * bits per second over the elapsed time. There isn't a better way to
	 * compute the precise bandwidth unless the switch were to include a
	 * timestamp in the stats reply message, which would be nice but isn't
	 * likely to happen. It would be even better if the switch recorded 
	 * bandwidth and reported bandwidth directly.
	 * 
	 * Stats are not reported unless at least two iterations have occurred
	 * for a single switch's reply. This must happen to compare the byte 
	 * counts and to get an elapsed time.
	 * 
	 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
	 *
	 */
	private class FlowStatsCollector implements Runnable {


		@Override
		public void run() {
			log.info("Run FlowStatsCollector");
			this.flowStats();
		}
		

		public void flowStats (){
			IOFSwitch sw1 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
			OFFactory flowFactory = sw1.getOFFactory();
			Match match = flowFactory.buildMatch()
  						.build();
			OFStatsRequest<?> request = flowFactory.buildFlowStatsRequest()
					.setMatch(match)
					.setOutPort(OFPort.ANY)
					.setTableId(TableId.ALL)
					.setOutGroup(OFGroup.ANY)
					.build();
			/*OFStatsRequest<?> request = flowFactory.buildPortStatsRequest()
					.setPortNo(OFPort.ANY)
					.build();*/
		

			try {
				// Poll specified switch, wait for reply
				
				ListenableFuture<?> future = sw1.writeStatsRequest(request);

				@SuppressWarnings("unchecked")
				List<OFStatsReply> replies = (List<OFStatsReply>) future.get(10, TimeUnit.SECONDS);
				log.info("replies size = " + replies.size() );
				// Get statistics of interest
				for (OFStatsReply reply : replies) {
					log.info("reply = " + reply);
						/*log.info("reply =" + reply.toString());
						for (OFStatsReply r : reply.getValue()) {
							log.info("r = " + r.toString());
							OFPortStatsReply psr = (OFPortStatsReply) r;
							log.info("psr = " + psr.toString());
							for (OFPortStatsEntry pse : psr.getEntries()) {
								log.info("pse = " + pse.toString());
							}
						}*/
					}
				
			}
			catch (Exception e) {
				log.warn("*** FlowMonitor: An error occurred while polling switch: " + e);
			}
			
			net.floodlightcontroller.flowcreator.FlowCreator.writeFlowMod(sw1);
		}
		
		//private void writeFlowMod(IOFSwitch sw) {
			//OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
			/*OFMessageFactory ofMessageFactory;
			OFFlowMod flowMod = floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
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
					.setXid(10)
					.build();*/
			
			/*Match myMatch = factory.buildMatch()
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
				log.warn("No switch !");
			}
			
			//writeOFMessageToSwitch(DatapathId.of("00:00:00:00:00:00:00:01"), flowMod);
		}*/
	}


	/*
	 * IFloodlightModule implementation
	 */
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);

		Map<String, String> config = context.getConfigParams(this);
		if (config.containsKey(ENABLED_STR)) {
			try {
				isEnabled = Boolean.parseBoolean(config.get(ENABLED_STR).trim());
			} catch (Exception e) {
				log.error("Could not parse '{}'. Using default of {}", ENABLED_STR, isEnabled);
			}
		}
		log.info("Statistics collection {}", isEnabled ? "enabled" : "disabled");

		if (config.containsKey(INTERVAL_STATS_STR)) {
			try {
				statsInterval = Integer.parseInt(config.get(INTERVAL_STATS_STR).trim());
			} catch (Exception e) {
				log.error("Could not parse '{}'. Using default of {}", INTERVAL_STATS_STR, statsInterval);
			}
		}
		log.info("Port statistics collection interval set to {}s", statsInterval);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new SwitchStatisticsWebRoutable());
		if (isEnabled) {
			startStatisticsCollection();
		}
	}

	
	/*
	 * Helper functions
	 */
	
	/**
	 * Start all stats threads.
	 */
	private void startStatisticsCollection() {
		statsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new FlowStatsCollector(), statsInterval, statsInterval, TimeUnit.SECONDS);
		log.warn("Statistics collection thread(s) started");
	}
	
	/**
	 * Stop all stats threads.
	 */
	private void stopStatisticsCollection() {
		if (!statsCollector.cancel(false)) {
			log.error("Could not cancel port stats thread");
		} else {
			log.warn("Statistics collection thread(s) stopped");
		}
	}
}