package applications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import core.Application;
import core.Connection;
import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.SADFragmentation;
import routing.TVProphetRouter;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;
import util.Tuple;

public class BroadcasterAppV2 extends StreamingApplication implements ConnectionListener{

	public static final String STREAM_TIME = "streamTime";
	
	private boolean broadcasted=false;
	private static double sTime;
	
	private int		seed = 0;
	private String 	streamID;
	
	private Random	r;
	private Stream 	stream;
	private SADFragmentation fragment;
	private int connectionSize;
	
	private DTNHost curHost;
//	private HashMap<DTNHost, Boolean> helloed;
	private ArrayList<DTNHost> receivedHello;
//	private boolean helloed=false;
	private boolean isListener=false;
	
	public BroadcasterAppV2(Settings s) {
		super(s);
		
		fragment = new SADFragmentation();
		r=new Random();
		sTime = s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		System.out.println("STIME: "+sTime);
		receivedHello = new ArrayList<DTNHost>();
//		helloed = new HashMap<DTNHost, Boolean>();
	}

	public BroadcasterAppV2(BroadcasterAppV2 a) {
		super(a);
		
		this.seed = a.getSeed();
		this.streamID=a.getStreamID();
		
		fragment = new SADFragmentation();
		sTime = a.getSTime();
		receivedHello = new ArrayList<DTNHost>();
//		helloed = new HashMap<DTNHost, Boolean>();
	}
	
	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		try{	
			if(type.equals(APP_TYPE)){
				String msg_type = (String) msg.getProperty("msg_type");
				
				if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)  && msg.getTo()==host){
					System.out.println(host + " Received a request for broadcast.");
					//register listener
					StreamProperties props = new StreamProperties(streamID);
					stream.registerListener(msg.getFrom(), props);
				
					//send first ever waiting
					double t = msg.getCreationTime();
					long needId = stream.getChunk(t).getChunkID();
					ArrayList<StreamChunk> missingC = getMissingChunks(null, needId-1);

					System.out.print("@ register _ found missing: ");
					System.out.println("Chunk needed: " + stream.getChunk(t).getChunkID());
					
					/////ha chunk pala ini
					for(StreamChunk c: missingC){
						sendChunk(c, host, msg.getFrom(), false);
					}
				}
		
				else if(msg_type.equalsIgnoreCase(HELLO)){
					System.out.println(host + " Received a hello! :D");
					
//					helloed=true;
					
					long otherAck = (long) msg.getProperty("ack");
					int otherStatus = (int) msg.getProperty("status");
					ArrayList<Long> chunks = ((ArrayList<Long>) msg.getProperty("buffermap"));
				
					if (broadcasted && otherStatus==-1 && otherAck==-1){
						stream.setTo(msg.getFrom());
						Message m = stream.replicate();
						((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
					}
					else{
						ArrayList<StreamChunk> missingC;
						
						if (otherStatus == WatcherApp.WAITING && otherAck==-1){ //if nahulat first ever chunk
							double t = (double) msg.getProperty("startTime");
							System.out.println("T: "+t);
							System.out.println("Chunk needed: " + stream.getChunk(t).getChunkID());
							long needId= stream.getChunk(t).getChunkID();
							missingC = getMissingChunks(chunks, needId-1);
						}
						else{ //there is something i could offer for future needs?
							missingC = getMissingChunks(chunks, otherAck);
//							System.out.println("Other ack: " + otherAck + " First missing: "+missingC.get(0).getChunkID());
							
						}

//						System.out.print("Found missing: ");
						/////ha chunk pala ini
						System.out.println("REMOVING BUFFERED");
						removeBufferedMessages(host, missingC.get(0).getChunkID());
						for(StreamChunk c: missingC){
//							System.out.print(c.getChunkID() + ",");
							sendChunk(c, host, msg.getFrom(), false);
						}
//						System.out.println("");
					}
					
					receivedHello.add(msg.getFrom());
				}
				
				else if (msg_type.equals(WatcherAppV2.URGENT_HELLO)){
					System.out.println(host + " received urgent. Must send new: " + msg.getReceiveTime());
					long otherAck = (long) msg.getProperty("ack");
					
					ArrayList<Long> chunks = ((ArrayList<Long>) msg.getProperty("buffermap"));
					ArrayList<StreamChunk> missingC = getMissingChunks(chunks, otherAck);
					
					System.out.print("Found urgent: ");
					/////ha chunk pala ini
					for(StreamChunk c: missingC){
						System.out.print(c.getChunkID() + ",");
						sendChunk(c, host, msg.getFrom(), false);
					}
					System.out.println("");
				}
					
			}
		}catch(NullPointerException e){}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		if (!isListener){
			host.getInterface(1).addCListener(this);
			isListener=true;
		}
		
		double curTime = SimClock.getTime();
		
		if (curTime >= sTime && !broadcasted){
			startBroadcast(host);
			broadcasted = true;
		}

		if(broadcasted){
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){ //for every interval
				stream.generateChunks(getStreamID(), fragment.getCurrIndex());
			
				try{
					for (Connection c : host.getConnections()){
						curHost = c.getOtherNode(host);
						
						//remove something on hello
						if (c.isUp()  && stream.getAllListener().containsKey(curHost) 
								&& receivedHello.contains(curHost)){ //kailangan ig limit to listeners only
							sendChunk(stream.getLatestChunk(), host, c.getOtherNode(host), false);
						}
					}
				}catch(IndexOutOfBoundsException e){
					curHost=null; //reset currHost
//					helloed=false;
					receivedHello.clear();
				}
			}
		
//				Connection con = host.getConnections().get(0);
//				DTNHost otherNode = con.getOtherNode(host);
//				
//	
//				//if connection is up
//				
//				if (con.isUp() && con.getOtherNode(host).equals(otherNode)){
//					if (!otherNode.getRouter().hasMessage(stream.getId()) && con.isInitiator(host)){
//						System.out.println("Other node has broadcast msg: "+con.getOtherNode(host).getRouter().hasMessage(stream.getId()) 
//								 + " & " + host +" is the initiator");
//						Message m = stream.replicate();
//						m.setTo(con.getOtherNode(host));
//						((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
//					}
//					else if (con.isInitiator(host) && !con.isTransferring()){
//						System.out.println("Just sending a request hello.");
//						String id = APP_TYPE + ":" + REQUEST_HELLO +  "-" + SimClock.getTime();
//						Message m = new Message(host, otherNode, id, SIMPLE_MSG_SIZE);
//						m.setAppID(APP_ID);
//						m.addProperty("msg_type", REQUEST_HELLO);
//						((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
//					}	
//				}
//			}catch(IndexOutOfBoundsException e){}
		}
	}
	
	private void startBroadcast(DTNHost host){
		stream= new Stream(host, null, APP_TYPE + ":broadcast" + 
				SimClock.getIntTime() + "-" + host.getAddress(),
				SIMPLE_MSG_SIZE);
		stream.addProperty("type", APP_TYPE);
		stream.addProperty("msg_type", BROADCAST_LIVE);
		stream.addProperty("streamID", getStreamID());
		stream.setAppID(APP_ID);
		stream.startLiveStream();
		host.createNewMessage(stream); //must override, meaning start a broadcast that a stream is initiated from this peer
		//////set response size
		super.sendEventToListeners(BROADCAST_LIVE, null, host);

	}
	
	public void sendUpdateToListeners(StreamChunk chunk, DTNHost host, DTNHost listener){
		sendChunk(chunk, host, listener, false);
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to, boolean first) {
		
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;
			
//		if (host.getRouter().hasMessage(id)){
//			Message m =  ((TVProphetRouter) host.getRouter()).getStoredMessage(id); ///////////returns null. kailangan ayuson
//			Message repM = m.replicate();
//			repM.setTo(to);
//			System.out.println("Message already exists. Replicated." + m.getId());
//			((TVProphetRouter) host.getRouter()).addUrgentMessage(repM, false);
//
////			System.out.println("RECEIVE TIME: " + host + " : "+repM.getReceiveTime());
//		}
//		
//		else{	
		if (host.getRouter().hasMessage(id)){
			Message m =  ((TVProphetRouter) host.getRouter()).getStoredMessage(id);
//			m.setReceiveTime(SimClock.getIntTime());
			m.setTo(to);
			System.out.println(m + "exists for "+ m.getTo() + " . Rtime: "+m.getReceiveTime());

			Message repM = m.replicate();
			repM.setTo(to);
			host.getRouter().deleteMessage(m.getId(), false);
			((TVProphetRouter) host.getRouter()).addUrgentMessage(repM, false);
			System.out.println(repM + "replicated for "+ m.getTo() + " . Rtime: "+repM.getReceiveTime());

		}
		else if (!host.getRouter().hasMessage(id)){
			System.out.println("Created new message to send." +chunk.getChunkID());
			Message m = new Message(host, to, id, (int) chunk.getSize());		
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", CHUNK_SENT);
			m.addProperty("chunk", chunk);	
			
			host.createNewMessage(m);
			System.out.println("RECEIVE TIME: "+ host + " : " + m.getReceiveTime() + " Creation time: "+m.getCreationTime());
//			((TVProphetRouter) host.getRouter()).addUrgentMessage(m, true);  
			
			sendEventToListeners(CHUNK_DELIVERED, chunk, host);
		}
	}

	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack){
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		ArrayList<StreamChunk> has = (	ArrayList<StreamChunk>) stream.getChunks();

		int i=0;
		while(has.get(i).getChunkID()<=ack){
			i++;
		}

		for (; i<has.size(); i++){
			StreamChunk c = has.get(i);
			try{
				if (c.getChunkID() > ack && !chunks.contains(c)){
					missing.add(c);
				}
			}catch(NullPointerException e){
				missing.add(c);
			}
		}
		return missing;
	}
	
	//tawagon ko ini once, if may urgent messages na isesend
	private void removeBufferedMessages(DTNHost host, long firstUrgent){

		List<Tuple<Message, Connection>> msgs = ((TVProphetRouter) host.getRouter()).getMessagesForConnected();
	
		System.out.println("@removeBufferedMsgs" + msgs.size());
		
		for(Tuple<Message, Connection> m : msgs){
			
			try{
				Message stored = m.getKey();
				System.out.println("StoredMsg: "+stored);
				
				StreamChunk chunk = (StreamChunk) stored.getProperty("chunk");
				if (chunk.getChunkID() < firstUrgent){
						host.getRouter().deleteMessage(stored.getId(), false);
						System.out.println("Stored deleted.");
				}
			}catch(NullPointerException e){}
		}
	}
	
	@Override
	public Application replicate() {
		return new BroadcasterAppV2(this);
	}

	public double getSTime(){
		return sTime;
	}
	
	@Override
	public void hostsConnected(DTNHost host1, DTNHost host2) {
//		System.out.println("HOSTS Connected: " + host1 + " : " + host2);
		
	}

	@Override
	public void hostsDisconnected(DTNHost host1, DTNHost host2) {
//		System.out.println("HOSTS not connected anymore: " + host1 + " : " +host2);
		
		if(receivedHello.contains(host1)){
			receivedHello.remove(host1);
		}
		else if(receivedHello.contains(host2)){
			receivedHello.remove(host2);
		}
	}
	
}
