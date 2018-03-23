package applications;

import java.util.Random;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimScenario;
import core.World;
import routing.TVProphetRouter;
import streaming.StreamChunk;

public abstract class StreamingApplication extends Application{
	
	public static final String APP_ID = "cmsc.janz.StreamingApplication";
	public static final String APP_TYPE = "dtnlivestreaming";
	public static final String STREAM_SEED = "seed";
	public static final String BROADCAST_LIVE = "BROADCAST_LIVE";
	public static final String BROADCAST_REQUEST = "REQUEST_STREAM";
	public static final String CHUNK_REQUEST = "REQUEST_CHUNK";
	public static final String CHUNK_SENT = "CHUNK_SENT";
	public static final String CHUNK_RECEIVED = "RECEIVED_CHUNK";
	public static final String CHUNK_DELIVERED= "DELIVERED_CHUNK"; //as a broadcaster
	public static final String FRAGMENT_RECEIVED = "RECEIVED_FRAGMENT";
	public static final String FRAGMENT_SENT = "SENT_FRAGMENT";
	public static final String FRAGMENT_REQUEST = "REQUEST_FRAGMENT";
	public static final String FRAGMENT_DELIVERED = "DELIVERED_FRAGMENT";
	public static final String HELLO = "hello";
	public static final String REQUEST_HELLO = "requestHello";

	
	public static final String STREAM_DEST_RANGE = "destinationRange";
	public static final String STREAM_SIZE = "streamSize";
	public static final String STREAM_ID = "streamID";
	
	public static final int PEDESTRIAN_INDEX_LEVEL_SIZE = 100*60; //bluetooth transmission * average pedestrian connection duration 
	public static final int VEHICLE_INDEX_LEVEL_SIZE = 100*20;
	
	public static final int SIMPLE_MSG_SIZE = 5;
	public static final int BUFFERMAP_SIZE = 10;
	public static final int HEADER_SIZE = 5;
	
	private int		seed = 0;
	private int		destMin=0;
	private int	    destMax=1;
	private String	streamID = "9999";
	
	private Random	rng;	
	
	public StreamingApplication(Settings s){
		
		if (s.contains(STREAM_DEST_RANGE)){
			int[] destination = s.getCsvInts(STREAM_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}
		if (s.contains(STREAM_SEED)){
			this.seed = s.getInt(STREAM_SEED);
		}
//		if(s.contains(STREAM_SIZE)){
//			this.streamSize = s.getInt(STREAM_SIZE); //////////should be set as chunk size
//		}
		if(s.contains(STREAM_ID)){
			this.streamID = s.getSetting(STREAM_ID);			
		}
		
		rng = new Random(this.seed);					
		super.setAppID(APP_ID);
	}
	
	public StreamingApplication(StreamingApplication a){
		super(a);
		
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
//		this.streamSize = a.getStreamSize();
		this.streamID = a.getStreamID();
		this.rng = new Random(this.seed);
	}

	protected DTNHost randomHost() {

		int destaddr = 0;
		if (destMax == destMin) {
			destaddr = destMin;
		}
		destaddr = destMin + rng.nextInt(destMax - destMin);
		World w = SimScenario.getInstance().getWorld();
		return w.getNodeByAddress(destaddr);
	}
	
	private int getIndexSize(TVProphetRouter router, DTNHost otherHost){
		return (int) router.getIndexSize();
	}
	
	private int getTransSize(TVProphetRouter router, DTNHost otherHost){
		return (int) router.getTransSize(otherHost);	
	}

//	public int getStreamSize() {
//		return streamSize;
//	}
	
	public int getDestMax() {
		return destMax;
	}
	
	public int getDestMin() {
		return destMin;
	}

	public int getSeed() {
		return seed;
	}

	public String getStreamID(){
		return streamID;
	}
	@Override
	public abstract Message handle(Message msg, DTNHost host);

	@Override
	public abstract void update(DTNHost host);
	
	protected abstract void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to, boolean first);
	

	protected Connection getCurrConnection(DTNHost h1, DTNHost h2){
		for(Connection c: h1.getConnections()){
			if ((c.getOtherNode(h1)).equals(h2)){
				return c;
			}
		}
		return null;
	}
}