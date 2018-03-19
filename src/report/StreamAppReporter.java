package report;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import applications.StreamingApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import core.SimClock;
import streaming.StreamChunk;

public class StreamAppReporter extends Report implements ApplicationListener{

	private int chunksReceived = 0 , chunksSent = 0 ;
	private int responseTime;
	private int throughput;
	private int timeRequestSent;
	private int nrofListeners;
	private int nrofTimesBroadcasted = 0;
	private int timeLastChunkReceived;
	private LinkedHashMap<Long, Double> receiveTime = new LinkedHashMap<Long, Double>();
	
	
	public void gotEvent(String event, Object params, Application app, DTNHost host) {
		
		if (!(app instanceof StreamingApplication)) return;
		
		if(event.equalsIgnoreCase(StreamingApplication.BROADCAST_REQUEST)){
			//record time request was sent
			timeRequestSent = SimClock.getIntTime();
		}
		
		else if (event.equalsIgnoreCase(StreamingApplication.BROADCAST_LIVE)){
			nrofTimesBroadcasted++;
		}
		
		else if (event.equalsIgnoreCase(StreamingApplication.CHUNK_RECEIVED)){
			//on params: if first node:
			int chunkType = -2;
			double chunkTime = 0;
			long chunkID = 0;
			if (params instanceof StreamChunk){
//				chunkType = ((StreamChunk) params).getChunkType();
				chunkID = ((StreamChunk) params).getChunkID();
				chunkTime = ((StreamChunk) params).getCreationTime();
			}
			
			if (chunkType == 0){ //if first chunk received since request
				responseTime = SimClock.getIntTime() - timeRequestSent; 				
			}
			else if (chunkType == 1){ //if last chunk to be received
				timeLastChunkReceived = SimClock.getIntTime();
			}
			
			receiveTime.put(chunkID, chunkTime);
		}
		
		else if (event.equalsIgnoreCase(StreamingApplication.CHUNK_SENT)){
			//response time for sender.
			chunksSent++;
		}
		
	}

	public void done(){
		
		System.out.println("DONE!");
		String chunkRecord= "HELLO I AM DONE";
		for (long key : receiveTime.keySet()){
			chunkRecord+= " \nt" + key + " : " + receiveTime.get(key);
		}
		
		write(chunkRecord);
		super.done();
	}
}
