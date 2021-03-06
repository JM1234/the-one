package applications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.SADFragmentation;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;

public class BroadcasterAppV3 extends StreamingApplication{

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
	private ArrayList<DTNHost> receivedHello;
	private boolean isListener=false;
	private double lastChokeInterval = 0;
	
	private HashMap<DTNHost, Long> latestHello;
	
	public BroadcasterAppV3(Settings s) {
		super(s);
		
		fragment = new SADFragmentation();
		r=new Random();
		sTime = 0; //s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		System.out.println("STIME: "+sTime);
		receivedHello = new ArrayList<DTNHost>();
		initUnchoke();
		
	}

	public BroadcasterAppV3(BroadcasterAppV3 a) {
		super(a);
		
		this.seed = a.getSeed();
		this.streamID=a.getStreamID();
		
		fragment = new SADFragmentation();
		sTime = a.getSTime();
		receivedHello = new ArrayList<DTNHost>();
		initUnchoke();
	}

	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		try{
			if(type.equals(APP_TYPE)){
				String msg_type = (String) msg.getProperty("msg_type");
				
				if (msg_type.equalsIgnoreCase(HELLO)){
					System.out.println(host + " Received a hello! :D");
					
					long otherAck = (long) msg.getProperty("ack");
					int otherStatus = (int) msg.getProperty("status");
					ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
					
					if (!stream.isRegistered(msg.getFrom())){ //save that we met this node for the first time
						stream.registerListener(msg.getFrom());
					}
					
					updateChunkCount(otherBuffermap); //update records of neighbor's data
				
//					//if otherNode is not listening to any stream yet
					System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
					if (broadcasted && otherStatus==-1 && otherAck==-1){
						stream.setTo(msg.getFrom());
						Message m = stream.replicate();
						((TVProphetRouterV2) host.getRouter()).addUrgentMessage(m, false);
						System.out.println(host + " SENDING BROADCAST to " + m.getTo());
					}
					sendBuffermap(host, msg.getFrom(), stream.getBuffermap());
					stream.setLastUpdate(msg.getFrom(), stream.getBuffermap().size());
					receivedHello.add(msg.getFrom());
					
					
				}
				
				else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
					long chunkNeeded = (long) msg.getProperty("chunk");
					
					System.out.println("ReceivedRequest from "+msg.getFrom());
					//evaluate if fragment or chunk it isesend
					if (stream.getChunk(chunkNeeded)!=null){
						sendChunk(stream.getChunk(chunkNeeded), host, msg.getFrom()); //simply sending. no buffer limit yet
					}
				}
				
				else if (msg_type.equals(INTERESTED)){
					System.out.println(host + " received INTERESTED from " + msg.getFrom());
					//evaluate response if choke or unchoke
					
					interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
					evaluateResponse(host, msg.getFrom());				
				}
			}
			
			
		}catch(NullPointerException e){}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		double curTime = SimClock.getTime();
		
		//startBroadcast here, once
		if (!broadcasted && curTime>=sTime){
			startBroadcast(host);
			broadcasted =  true;
		}
		
		if (broadcasted){
			//generate chunks here
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){ //for every interval
				stream.generateChunks(getStreamID(), fragment.getCurrIndex());
			}
			
//			//for maintaining -- choking and unchoking
//			if ( ((curTime - lastChokeInterval) % 5) == 0){
//				
////				System.out.println("INTERESTED NEIGHBORS: " + interestedNeighbors.keySet());
//				if (hasNewInterested()){
//					
//					ArrayList<DTNHost> recognized = getRecognized();
//					removeUninterested(host, recognized);
//					
////					System.out.println("RECOGNIZED NODES: " + recognized);
//					
//					if(!recognized.isEmpty()){
//						if (curTime-lastChokeInterval >= 15){
//							unchokeTop3(host, recognized);
//							unchokeRand(host, recognized);
//							chokeOthers(host, recognized);
//							lastChokeInterval = curTime;
//						}
//						else if (!recognized.isEmpty()){
//							unchokeRand(host, recognized);
//						}
//					}
//				}
//
////				System.out.println("UNCHOKED: "+ unchoked);
//			}
			
			checkHelloedConnection(host); //remove data of disconnected nodes
			updateHello(host);
		}
	}

	public void sendBuffermap(DTNHost host, DTNHost to, Collection<Long> list){
		String id = APP_TYPE+ ":hello-" + SimClock.getIntTime() +"-" +host.getAddress() +"-" + to; // + SimClock.getIntTime();
	
		System.out.println(host+ " sending HELLO to " + to);
		
		Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must have basis
		m.setAppID(APP_ID);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", HELLO);
		m.addProperty("status", 2);
		m.addProperty("ack", stream.getLatestChunk().getChunkID());
		m.addProperty("buffermap", list); // this is full buffermap
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1); //important
		host.createNewMessage(m);
		m.setTtl(5);
	
		sentHello.put(to, SimClock.getIntTime());
	}
	
	public void startBroadcast(DTNHost host){
		stream= new Stream(host, null, APP_TYPE + ":broadcast" + 
				SimClock.getIntTime() + "-" + host.getAddress(),
				SIMPLE_MSG_SIZE);
		stream.addProperty("type", APP_TYPE);
		stream.addProperty("msg_type", BROADCAST_LIVE);
		stream.addProperty("streamID", getStreamID());
		stream.addProperty("stream_name", "tempstream");
		stream.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		stream.addProperty("time_started", SimClock.getIntTime());
		stream.setAppID(APP_ID);
		stream.startLiveStream();
		host.createNewMessage(stream); //must override, meaning start a broadcast that a stream is initiated from this peer
		//////set response size
		
		lastChokeInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to) {
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime() +"-" +to;
		
		System.out.println("Created new message to send." +chunk.getChunkID());
		Message m = new Message(host, to, id, (int) chunk.getSize());		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
		m.addProperty("chunk", chunk);	
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 3);
		host.createNewMessage(m);

		sendEventToListeners(CHUNK_DELIVERED, chunk, host);
	}
	
	@Override
	public Application replicate() {
		return new BroadcasterAppV3(this);
	}

	public double getSTime(){
		return sTime;
	}
	
	public void updateHello(DTNHost host){
		int curTime = SimClock.getIntTime();	

		for (DTNHost h : sentHello.keySet()){
			if (curTime - sentHello.get(h) >= HELLO_UPDATE) { //&& //if it's time to send an updated HELLO
//					(getCurrConnection(host, h).isTransferring()!=true)){ //and nothing is happening in the connection (i.e, we are not sending anything)
				System.out.println(host +" sending an updated hello to "+ h + " @ " + curTime);
//				System.out.println("Last sent update: " + stream.getChunk(stream.getBuffermap().size()-1).getChunkID() + 
//						"With respect to time: " + stream.getChunk(curTime));
				
				System.out.println("Last UPDATE (index) " + stream.getLastUpdate(h) + " End update now (index): " + stream.getBuffermap().size());
				ArrayList<Long> latestUpdates = new ArrayList<Long> (stream.getBuffermap().subList(stream.getLastUpdate(h), stream.getBuffermap().size()));
				sendBuffermap(host, h, latestUpdates);  
				sentHello.put(h, curTime);
				stream.setLastUpdate(h, stream.getBuffermap().size());
			}
		}
	}
	
	
	/*
	 * 
	 * CHOKE/UNCHOKE STARTS HERE ---------------------->
	 * 
	 */
	public void evaluateResponse(DTNHost host, DTNHost to){ //evaluate if we should choke or unchoke this node that sent INTERESTED
		System.out.println("@ evaluating response");
		int ctr=0;
		while(unchoked.get(ctr)!=null && ctr<3){
			ctr++;
		}
		if (interestedNeighbors.size()<3 && ctr<3 && !unchoked.contains(to)){
			sendResponse(host, to, true);
			unchoked.set(ctr,to);
			System.out.println(host +" ADDED TO UNCHOKED: "+ to);
			interestedNeighbors.remove(to);
		}
		else if (unchoked.contains(to)){
			sendResponse(host, to, true);
		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE-" + SimClock.getIntTime() + "-" + to;
			msgType = UNCHOKE;
		}
		else{
			id = APP_TYPE + ":CHOKE-" + SimClock.getIntTime() + "-" + to;
			msgType = CHOKE;
		}
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		host.createNewMessage(m);
	}

	private void removeUninterested(DTNHost host, ArrayList<DTNHost> recognized){
		
		for (int i=0; i<4; i++){
			if (recognized.contains(unchoked.get(i)) && unchoked.get(i)!=null){
				sendResponse(host, unchoked.get(i), false); //send CHOKE to nodes that are uninterested but in our unchoked before
				unchoked.set(i, null);
			}
		}
	}
	
	private boolean hasNewInterested(){
		// count if an sulod han interested is same la ha mga unchoked
		if (interestedNeighbors.isEmpty()) return false;
			
		for (DTNHost node : interestedNeighbors.keySet()){
			if (!unchoked.contains(node)){
				return true;
			}
		}
		return false;
	}
	
	private ArrayList<DTNHost> getRecognized(){
		int curTime = SimClock.getIntTime();
		ArrayList<DTNHost> recognized = new ArrayList<DTNHost>(); //save here the recent INTERESTED requests
	
		//extract recent INTERESTED messages, delete an diri recent
		Iterator<Map.Entry<DTNHost, Integer>> entryIt = interestedNeighbors.entrySet().iterator();
		while(entryIt.hasNext()){
			Entry<DTNHost, Integer> entry = entryIt.next();
//			if ( (curTime - entry.getValue()) <= 10 ){ //irerecognize ko la an mga nagsend interested for the past 10 seconds
				recognized.add(entry.getKey());
//			}
//			else{
//				entryIt.remove();
//			}
		}
		sortNeighborsByBandwidth(recognized);
		return recognized;
	}
	
	/*
	 * called every 15 seconds.
	 */
	private void unchokeTop3(DTNHost host, ArrayList<DTNHost> recognized){
		if (recognized.isEmpty()) return;

		Iterator<DTNHost> i = recognized.iterator();
		for (int ctr=0; ctr<3; ctr++){ //send UNCHOKE to top 3
			DTNHost other=null;
			try{
				other = i.next();	
				sendResponse(host, other, true); //send UNCHOKE
				i.remove(); //notification granted, remove
			}catch(NoSuchElementException e){}
			System.out.println("ctr: "+ctr);
			unchoked.set(ctr, other);	
		}
	}	
	
	/*
	 * called every 5 seconds. Get a random node to be unchoked that is not included in the top3
	 * @param recognized interestedNeighbors that are not included in the top 3
	 * 
	 */
	private void unchokeRand(DTNHost host, ArrayList<DTNHost> recognized){ 	//every 5 seconds. i-sure na diri same han last //tas diri dapat api ha top3
		if (recognized.isEmpty()) return;

		Random r = new Random();
		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index);
		DTNHost prevRand;
		
		try{
			recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
			prevRand = unchoked.get(3);
			
			if (prevRand!=randNode){
				sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			}
		}catch(IndexOutOfBoundsException i){}

		updateUnchoked(3, randNode);
		sendResponse(host, randNode, true); //sendUnchoke to this random node
		interestedNeighbors.remove(randNode);  //notification granted. remove
	}
	
	private void chokeOthers(DTNHost host, ArrayList<DTNHost> recognized){
		//sendChoke to tanan na nabilin
		for (DTNHost r : recognized){
			sendResponse(host, r, false); 
		}
	}
	
	private void initUnchoke(){
		for (int i=0; i<4; i++){
			unchoked.add(i, null);
		}
	}
}
