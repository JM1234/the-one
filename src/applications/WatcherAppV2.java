package applications;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.Application;
import core.Connection;
import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import routing.MessageRouter;
import routing.TVProphetRouter;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;
import util.Tuple;

public class WatcherAppV2 extends StreamingApplication implements ConnectionListener {

	public static final String WATCHER_TYPE = "watcherType"; 
	public static final String URGENT_HELLO = "importantHello";
	
	public static final int PLAYING = 1;
	public static final int WAITING = 0;
	
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private Random	rng;
	
	private int 	status =-1;
	private int 	watcherType; //1 if listener. 0 if just a hop
	private boolean isWatching=false;
	private double lastTimePlayed=0;
//	private boolean helloed=false;
	private ArrayList<DTNHost> sentHello;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
	private Message broadcastMsg;
	private int urgentRequest=0;
	private Message m; //temp urgent msg
	private boolean isListener=false;
	
	public WatcherAppV2(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
		sentHello = new ArrayList<DTNHost>();
	}

	public WatcherAppV2(WatcherAppV2 a) {
		super(a);
		
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
//		this.streamSize = a.getStreamSize();
		this.watcherType = a.getWatcherType();
		this.rng = new Random(this.seed);
		props = new StreamProperties("");
		sentHello = new ArrayList<DTNHost>();
	}

	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		if (type.equals(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			if (msg_type.equals(BROADCAST_LIVE)){
				System.out.println(host +" Received a broadcast.");
				
				String id = APP_TYPE+":register" + SimClock.getIntTime() + "-" +host.getAddress();
				if (!isWatching && watcherType==1){
					Message m = new Message(host, msg.getFrom(), id, StreamingApplication.SIMPLE_MSG_SIZE);
					m.addProperty("type", APP_TYPE);
					m.addProperty("msg_type", BROADCAST_REQUEST);
					m.setAppID(APP_ID);
					
					isWatching=true;
					status = WAITING;
					
					String streamID=(String) msg.getProperty("streamID");
					props.setStreamID(streamID);
					props.setStartTime(m.getCreationTime());

					host.createNewMessage(m);
					System.out.println(host + " Sent request for broadcast.");
				}
				///for uninterested watcher, just save
				broadcastMsg = msg.replicate();
			}
			
			else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host +"received a broadcast request.");
				
				//send first ever waiting
				double t = msg.getCreationTime();
				StreamChunk need = props.getChunk(t);
				
				if (need!=null){
					ArrayList<StreamChunk> missingC = getMissingChunks(null, need.getChunkID()-1);

					System.out.print("@ register _ found missing: ");
					System.out.println("Chunk needed: " + need.getChunkID());
				
					/////ha chunk pala ini
					for(StreamChunk c: missingC){
						System.out.print(c.getChunkID() + ",");
						sendChunk(c, host, msg.getFrom(), false);
					}
					System.out.println("");
				}
				else{
					System.out.println("I don't have what you need.");
				}
			}
			
			else if (msg_type.equals(REQUEST_HELLO)){
				System.out.println(host + " Received a request for hello.");
				sendBuffermap(host, msg.getFrom(), props.getBufferMap(), null);
			}
			
			else if(msg_type.equalsIgnoreCase(CHUNK_SENT)){ //received chunks
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				props.addChunk(chunk);
				
				System.out.println(host+ " received: "+chunk.getChunkID() + " for " + msg.getTo());
				
				DTNHost sender = msg.getHops().get(msg.getHops().size()-2); //if an naghatag hini na message == broadcastMsg.getFrom
				Connection curCon = getCurrConnection(host, sender);
				
				System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
				System.out.println("Sender " + sender);
				
				if (status==WAITING && (props.getAck()+1)!=chunk.getChunkID()  
						&& (sender.equals(broadcastMsg.getFrom()) && props.getAck()!=-1) ){ // && urgentRequest<2 
					System.out.println("Waiting for "+ props.getAck() + " but received " + chunk.getChunkID());
					urgentRequest++;					
//						removeBufferedMessages(msg.getFrom(), props.getAck(), curCon);
//						m = sendUrgentMsg(host, sender);
//						int retval = curCon.startTransfer(host, m.replicate());
//						System.out.println("Retval: "+retval);	
				}
				
				if (msg.getTo() == host){
					System.out.println("Props start time:" + props.getStartTime());
					if (msg.getId().contains(":first") || (chunk.getCreationTime() == props.getStartTime() && props.getAck()==-1)){
						System.out.println("First chunk received by " + host + ":" +chunk.getChunkID());
						props.setChunkStart(chunk.getChunkID());
						status = PLAYING;
						this.lastTimePlayed=SimClock.getTime();
					}
					else{
						props.setAck(chunk.getChunkID());
					}
				}

//						String id = APP_TYPE+ ":hello" + "-" +host.getAddress(); 
//						
//						if(host.getRouter().hasMessage(id)){
//							m = ((TVProphetRouter) host.getRouter()).getStoredMessage(id);
//							m.updateProperty("buffermap", props.getBufferMap());
//							System.out.println(" urgent hello just replicated ");
//						}
//						else{
//							m = sendBuffermap(host, sender, props.getBufferMap(), null);
//							System.out.println(" created an urgent hello ");
//						}
//						System.out.println(" sending an urgent hello message ");
////						if (curCon.isTransferring()) curCon.abortTransfer();
//						int retval = curCon.startTransfer(host, m.replicate());
//						System.out.println("Retval: "+retval);	
			}
			
			else if (msg_type.equals(HELLO)){
			try{
				System.out.println(host + " received hello.");
				
				DTNHost sender = msg.getHops().get(msg.getHops().size()-2); //if an naghatag hini na message == broadcastMsg.getFrom
				Connection curCon = getCurrConnection(host, sender);

				int otherStatus = (int) msg.getProperty("status");
				long otherAck = (long) msg.getProperty("ack");
				
//				int watcherType = (int) msg.getProperty("watcherType");

//				if (!cur.isInitiator(host)){ //if nauna pag send HELLO an other node, weigh the needs
					//i shall weigh the needs
				
					if (broadcastMsg!=null &&  otherAck == -1 && otherStatus == -1){ //meaning no sent to this node broadcast yet
						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
						broadcastMsg.setTo(msg.getFrom());
						((TVProphetRouter) host.getRouter()).addUrgentMessage(broadcastMsg.replicate(), false);
					}
						
						//if urgent talaga iya need and mayda ko han iya kailangan
					else if (otherStatus == WAITING && props.getBufferMap().contains(otherAck+1)){ //&& otherAck<=props.getAck() ){
						System.out.println("HANDLING NODE! It's an emergency for it and I have what it needs!");
						handleNode(msg, host, msg.getFrom());
					}
					else if ((status==WAITING || (props.getAck()<otherAck)) && !curCon.isInitiator(host)){ //if my status is waiting and i am a legit watcher 
						System.out.println("Mas importante ak ganap." + host + "to: "+msg.getFrom());
						sendBuffermap(host, msg.getFrom(), props.getBufferMap(), props.getFragments());
					}
					
					else{ //handle its needs
						System.out.println("Nothing left to give.");
//						System.out.println("i can give what i have. just handling node.");
//						handleNode(msg, host, msg.getFrom());
						//send hello after all this
					}
			}catch(NullPointerException e){}
			}
		}
			
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		if (!isListener){
			host.getInterface(1).addCListener(this);
			isListener=true;
			
		}
		
		double curTime = SimClock.getTime();

		try{
			for (Connection c : host.getConnections()){
				if (c.isUp()){
					if (!sentHello.contains(c.getOtherNode(host))){
						System.out.println(host + " is sending hello.");
						sendBuffermap(host, c.getOtherNode(host), props.getBufferMap(), null);
//						helloed=true;
						sentHello.add(c.getOtherNode(host));
//						System.out.println("CONNECTION INTERFACE" + host.getInterface(1));
					}
				}
			}
			
			if (isWatching && (curTime-this.lastTimePlayed >= Stream.getStreamInterval())){
				System.out.println("++++++++++++++MUST BE PLAYING +++++++++++++++++");
				if(props.isReady(props.getNext())){
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
				}
				else{ // if (status==PLAYING)
					status = WAITING;
					System.out.println(host+ " waiting: "+ props.getNext());
				}
				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
		}catch(NullPointerException e){
			e.printStackTrace();
		}
		catch(ArrayIndexOutOfBoundsException i){
			sentHello.clear();
			urgentRequest=0;
		}	
	}
	
//	sakob ini han update dati
//		try{
//			Connection con = host.getConnections().get(host.getConnections().size()-1);
//			
//			for (Connection c : host.getConnections()){
//				if (c.isUp()){
//					if (!sentHello.contains(c.getOtherNode(host))){
//						System.out.println(host + " is sending hello.");
//						sendBuffermap(host, c.getOtherNode(host), props.getBufferMap(), null);
////						helloed=true;
//						sentHello.add(c.getOtherNode(host));
////						System.out.println("CONNECTION INTERFACE" + host.getInterface(1));
//					}
//			}
				
//				if(urgentRequest == 1){
					
//					removeBufferedMessages(con.getOtherNode(host));
//					if (m==null) m = sendUrgentMsg(host, con.getOtherNode(host));
//					System.out.println("sending this message");
//					int retval = con.startTransfer(host, m);
////					
//					if(retval==MessageRouter.RCV_OK){
//						System.out.println(host + "Sending urgent is ok.");
//						urgentRequest=0;
//					}
//					
////				con.getOtherNode(host).sendMessage(m.getId(), con.getOtherNode(host));
//				}
//			}	

//		}catch(IndexOutOfBoundsException e){
////			helloed=false;
//			sentHello.clear();
//			urgentRequest=0;
//		}
//	}

	private Message sendUrgentMsg(DTNHost host, DTNHost to){
		String id = APP_TYPE + ":urgent" + SimClock.getIntTime() + "-" + host.getAddress();
		
		Message m = new Message(host, to, id, BUFFERMAP_SIZE);
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", URGENT_HELLO);
		m.addProperty("status", this.status);
		m.addProperty("buffermap", props.getBufferMap()); //////should be full buffermap
		m.addProperty("ack", props.getAck());
		host.createNewMessage(m);
		m.setTtl(3);
		return m;
	}
	
	private Message sendBuffermap(DTNHost host, DTNHost to, ArrayList<Long> chunks, ArrayList<Integer> fragments){
		String id = APP_TYPE+ ":hello" + + SimClock.getIntTime() +"-" +host.getAddress(); //+ SimClock.getIntTime()
		
//		if(!host.getRouter().hasMessage(id)){
			Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", HELLO);
			m.addProperty("status", this.status);
			m.addProperty("buffermap", chunks); //////should be full buffermap
			m.addProperty("ack", props.getAck());
//			if (props.getStartTime() >= 0) m.addProperty("startTime", props.getStartTime());
			host.createNewMessage(m);
			m.setTtl(3);
			
//			host.sendMessage(m.getId(), to);
//			int retval = curCon.startTransfer(host, m.replicate());
//			System.out.println("Retval: "+retval);
			
			return m;
//		}
	}
	private void handleNode(Message msg, DTNHost src, DTNHost to){
		
		ArrayList<Long> c = (ArrayList<Long>) msg.getProperty("buffermap");
		ArrayList<StreamChunk> missingC = getMissingChunks(c, (long) msg.getProperty("ack"));

		for (StreamChunk m : missingC){
			sendChunk(m, src, to, false);
		}
	}
	
	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack) throws NullPointerException{ ////optimize
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		try{
			ArrayList<StreamChunk> has = props.getReceived();
			
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
		}catch(IndexOutOfBoundsException i){
			System.out.println("Error. No chunks received yet.");
		}
		return missing;
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to, boolean first) {
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;
		
//		if (host.getRouter().hasMessage(id)){
//			Message m =  ((TVProphetRouter) host.getRouter()).getStoredMessage(id); 
//			Message repM = m.replicate();
//			repM.setTo(to);
//			System.out.println("Message already exists. Replicated." + repM.getId());
//			((TVProphetRouter) host.getRouter()).addUrgentMessage(repM, false);
//		}
//		
//		else{	
//			System.out.println("Created new message to send." +chunk.getChunkID());
			Message m = new Message(host, to, id, (int) chunk.getSize());		
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", CHUNK_SENT);
			m.addProperty("chunk", chunk);	
			host.createNewMessage(m);
			
//			sendEventToListeners(CHUNK_DELIVERED, chunk, host);
//		}
	}
	
	private void removeBufferedMessages(DTNHost host, long id, Connection con){
		System.out.println(host + " REMOVED BUFFER FROM OTHER HOST.");
		
		List<Tuple<Message, Connection>> msgs = ((TVProphetRouter) host.getRouter()).getMessagesForConnected();
		System.out.println("Messages for connected size: "+msgs.size());
		for(Tuple<Message, Connection> m : msgs){
			Message stored = m.getKey();

			System.out.println("Message: "+ stored);
			
			try{
				StreamChunk oc = (StreamChunk) stored.getProperty("chunk");
				
				if (oc.getChunkID() != props.getNext()){
					host.getRouter().deleteMessage(stored.getId(), false);
					System.out.println("Deleted on "+ host + " buffer: " + stored);
				}
//				else{
//					host.getRouter().sendMessage(stored.getId(), con.getOtherNode(host));
//					System.out.println("Sending " + stored + " to: " + con.getOtherNode(host));
//				}

//				////////////////usa pa na approach
//				if (oc.getChunkID() < props.getNext()){
//					host.getRouter().deleteMessage(stored.getId(), false);
//					System.out.println("Deleted on "+ host + " buffer: " + stored);
//				}
//				else{
//					host.getRouter().sendMessage(stored.getId(), con.getOtherNode(host));
//					System.out.println("Sending " + stored + " to: " + con.getOtherNode(host));
//					break;
//				}
					
			}catch(NullPointerException e){}
			
//			if (!stored.equals(firstUrgent)){
//				host.getRouter().deleteMessage(stored.getId(), false);
//			}
		}
	}
	
	public int getWatcherType(){
		return watcherType;
	}

	@Override
	public Application replicate() {
		return new WatcherAppV2(this);
	}

	@Override
	public void hostsConnected(DTNHost host1, DTNHost host2) {
//		System.out.println("HOSTS Connected: " + host1 + " : " + host2);
	}

	@Override
	public void hostsDisconnected(DTNHost host1, DTNHost host2) {
//		System.out.println("HOSTS not connected anymore: " + host1 + " : " +host2);
		
		if(sentHello.contains(host1)){
			sentHello.remove(host1);
		}
		else if(sentHello.contains(host2)){
			sentHello.remove(host2);
		}
		
	}

}
