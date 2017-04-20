package net.floodlightcontroller.statistics;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.statistics.web.SwitchStatisticsWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.State;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StatisticsCollector2 implements IFloodlightModule {
	private static final Logger log = LoggerFactory.getLogger(StatisticsCollector2.class);

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
			
			//All switches, all entries
			Map<DatapathId, List<OFStatsReply>> replies_all = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.FLOW);
			//log.info("replies_all = " + replies_all.toString()) ;

			//Per switch
			for (Entry<DatapathId, List<OFStatsReply>> reply : replies_all.entrySet()) {
				log.info("reply = " + reply);
				
				//Content of switch's answer
				for (OFStatsReply r : reply.getValue()) { 
					//log.info("r = " + r);
					OFFlowStatsReply fsr = (OFFlowStatsReply) r;
					
					//Rules one by one
					for (OFFlowStatsEntry rule : fsr.getEntries()) {
						log.info("rule = " + rule);
						String[] rule_fields = rule.toString().split(",");
						String packetCount_str = rule_fields[8].split("x")[1];
						Long packetCount = Long.parseLong(packetCount_str, 16);
						if (packetCount > 10){
							log.warn("DDoS !!");
						}
						//log.info(packetCount.toString());
						 
						OFPort srcPort = rule.getMatch().get(MatchField.IN_PORT);						
						MacAddress srcDpid = rule.getMatch().get(MatchField.ETH_SRC);
						MacAddress dstDpid = rule.getMatch().get(MatchField.ETH_DST);		
						IPv4Address ipSrc = rule.getMatch().get(MatchField.IPV4_SRC);
						IPv4Address ipDst = rule.getMatch().get(MatchField.IPV4_DST);
						//log.info("srcPort = " + srcPort + " srcDpid = " + srcDpid + " dstDpid = " + dstDpid + " ipSrc = " + ipSrc + " ipDst = " + ipDst );
					}
				}
			}

		}
	}

	/**
	 * Single thread for collecting switch statistics and
	 * containing the reply.
	 * 
	 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
	 *
	 */
	private class GetStatisticsThread extends Thread {
		private List<OFStatsReply> statsReply;
		private DatapathId switchId;
		private OFStatsType statType;

		public GetStatisticsThread(DatapathId switchId, OFStatsType statType) {
			this.switchId = switchId;
			this.statType = statType;
			this.statsReply = null;
		}

		public List<OFStatsReply> getStatisticsReply() {
			//log.info("Reply from thread");
			return statsReply;
		}

		public DatapathId getSwitchId() {
			return switchId;
		}

		@Override
		public void run() {
			//log.info("Thread run + statsReply");
			statsReply = getSwitchStatistics(switchId, statType);
		}
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

	/**
	 * Retrieve the statistics from all switches in parallel.
	 * @param dpids
	 * @param statsType
	 * @return
	 */
	
	// Statistics from all threads = switches
	private Map<DatapathId, List<OFStatsReply>> getSwitchStatistics(Set<DatapathId> dpids, OFStatsType statsType) {
		//log.info("getSwitchStatistics all thread");
		HashMap<DatapathId, List<OFStatsReply>> model = new HashMap<DatapathId, List<OFStatsReply>>();

		List<GetStatisticsThread> activeThreads = new ArrayList<GetStatisticsThread>(dpids.size());
		List<GetStatisticsThread> pendingRemovalThreads = new ArrayList<GetStatisticsThread>();
		GetStatisticsThread t;
		for (DatapathId d : dpids) {
			t = new GetStatisticsThread(d, statsType);
			activeThreads.add(t);
			t.start();
		}

		/* Join all the threads after the timeout. Set a hard timeout
		 * of 12 seconds for the threads to finish. If the thread has not
		 * finished the switch has not replied yet and therefore we won't
		 * add the switch's stats to the reply.
		 */
		for (int iSleepCycles = 0; iSleepCycles < statsInterval; iSleepCycles++) {
			for (GetStatisticsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					model.put(curThread.getSwitchId(), curThread.getStatisticsReply());
					pendingRemovalThreads.add(curThread);
				}
			}

			/* remove the threads that have completed the queries to the switches */
			for (GetStatisticsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}
			
			/* clear the list so we don't try to double remove them */
			pendingRemovalThreads.clear();

			/* if we are done finish early */
			if (activeThreads.isEmpty()) {
				break;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Interrupted while waiting for statistics", e);
			}
		}
		return model;
	}

	/**
	 * Get statistics from a switch.
	 * @param switchId
	 * @param statsType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	//Get statistics from one thread = one switch
	protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		Match match;
		if (sw != null) {
			OFStatsRequest<?> req = null;
			switch (statsType) {
			case FLOW:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildFlowStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.setOutGroup(OFGroup.ANY)
						.build();
				break;
			case AGGREGATE:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildAggregateStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				break;
			case PORT:
				req = sw.getOFFactory().buildPortStatsRequest()
				.setPortNo(OFPort.ANY)
				.build();
				break;
			case QUEUE:
				req = sw.getOFFactory().buildQueueStatsRequest()
				.setPortNo(OFPort.ANY)
				.setQueueId(UnsignedLong.MAX_VALUE.longValue())
				.build();
				break;
			case DESC:
				req = sw.getOFFactory().buildDescStatsRequest()
				.build();
				break;
			case GROUP:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupStatsRequest()				
							.build();
				}
				break;

			case METER:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterStatsRequest()
							.setMeterId(OFMeterSerializerVer13.ALL_VAL)
							.build();
				}
				break;

			case GROUP_DESC:			
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupDescStatsRequest()			
							.build();
				}
				break;

			case GROUP_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
							.build();
				}
				break;

			case METER_CONFIG:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterConfigStatsRequest()
							.build();
				}
				break;

			case METER_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
							.build();
				}
				break;

			case TABLE:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableStatsRequest()
							.build();
				}
				break;

			case TABLE_FEATURES:	
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableFeaturesStatsRequest()
							.build();		
				}
				break;
			case PORT_DESC:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildPortDescStatsRequest()
							.build();
				}
				break;
			case EXPERIMENTER:		
			default:
				log.error("Stats Request Type {} not implemented yet", statsType.name());
				break;
			}

			try {
				if (req != null) {
					future = sw.writeStatsRequest(req); 
					values = (List<OFStatsReply>) future.get(statsInterval / 2, TimeUnit.SECONDS);
				}
			} catch (Exception e) {
				log.error("Failure retrieving statistics from switch {}. {}", sw, e);
			}
		}
		return values;
	}
}