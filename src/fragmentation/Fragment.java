package fragmentation;

import java.util.ArrayList;

import core.SimClock;
import streaming.StreamChunk;

public class Fragment {

	
	private int id; //index id
	private ArrayList<StreamChunk> bChunks;
	private double timeCreated;
	
	public Fragment(int id, ArrayList<StreamChunk> bChunks){
		this.id = id;
		this.bChunks = new ArrayList<StreamChunk> (bChunks);
		timeCreated = SimClock.getTime();
	}
	
	public double getTimeCreated(){
		return timeCreated;
	}
	
	public ArrayList<StreamChunk> getBundle(){
		return bChunks;
	}
	
	public int getId(){
		return id;
	}
	
}
