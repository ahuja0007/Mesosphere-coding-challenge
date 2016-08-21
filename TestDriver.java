

import java.util.*;
import java.lang.*;
import java.io.*;

/*
 * This class define the two ends of an elevator
 * based on current requests and stops
 */
class Ends {
	public int low;
	public int high;
	
	public Ends (int f, int s) { low = f; high = s; }
}

class ElevStatus {
	public int id;
	public int currFloor;
	public List<Integer> stops;
	public int direction;
	public Ends ends;
	public List<List<Integer>> requests;
	
	public ElevStatus(int _id, int _floor, int direction, Ends ends, List<Integer> stops, List<List<Integer>> req) {
		this.id = _id; 
		this.currFloor = _floor; 
		this.direction = direction;
		this.stops = stops; 
		this.requests = req;
		this.ends = ends;
	}
	
	public String toString() {
		return new String("[elev " + id + "] : " +  currFloor + " : " + 
				   "stops " + stops.toString() + " reqs " + requests.toString());
	}
}

class Request {
	public int pickupFloor;
	public int destFloor;
	public void Request(int p, int d) {
		pickupFloor = p; destFloor = d;
	}
}

class Elevator {
	private int id;
	private int currFloor;
	private int direction;
	private int size;
	private List<List<Integer>> requests;
	private boolean[] stops;

	// When idle, elevator should stop at middle floor
	// because the chance of requests coming from either directions
	// are equal
	public Elevator(int id, int size) {
		this.size = size;
		this.id = id;
		this.currFloor = size / 2;
		this.direction = 1;
		requests = new ArrayList<List<Integer>>();
		for(int i=0; i<size; i++) requests.add(new ArrayList<Integer>());
		stops = new boolean[size];
	}
	
	public ElevStatus status() {
		List<Integer> stops = new ArrayList<Integer>();
		
		for(int i=0; i<this.stops.length; i++) {
			if(this.stops[i]) stops.add(i+1);
		}
		
		Ends ends = getEnds();
		return new ElevStatus(id, currFloor, direction, ends, stops, requests);
	}
	
	
	public boolean isIdle() {	
		// Check if the elevator is empty
		for(List<Integer> e : this.requests) {
			if (!e.isEmpty()) {
				return false;
			}
		}
		for(boolean e : this.stops) {
			if (e) {
				return false;
			}
		}
		return true;
	}	
	
	// Get the ends of elevator could reach based on requests and stops 
	public Ends getEnds() {
		int lo=this.currFloor, hi = this.currFloor;
		for(int i=0; i<size; i++) {
			if(stops[i] || !requests.get(i).isEmpty()) {
				lo = Math.min(i+1, lo);
				hi = Math.max(i+1, hi);
			}
		}
		return new Ends(lo,hi);
	}
	
	// This method is for testing purpose
	public void set(int currFloor, int direction) {
		this.currFloor = currFloor;
		this.direction = direction;
	}
	
	// Assign the request to given floor of the elevator
	public void assign(int pickupFloor, int destFloor) {
		System.out.println("Elevator update " + pickupFloor + " : " + destFloor);
		
		if (pickupFloor == destFloor) {
			throw new IllegalArgumentException("pickupFloor is same as destination");
		}
		
		if (pickupFloor < 1 || pickupFloor > this.size || destFloor < 1 || destFloor > this.size) {
			throw new IllegalArgumentException("The floor is out of range");
		}
		requests.get(pickupFloor-1).add(destFloor);
	}
	
	// Update current floor followed by pick up and release passengers
	public void updateFloor(int floor) {
		this.currFloor = floor;
		int idx = this.currFloor-1;
		
		this.stops[idx] = false; // Release passengers
		if(!requests.get(idx).isEmpty()) {
			
			Iterator<Integer> it = requests.get(idx).iterator();
			// Pick up passenger and assign requests to stops of floors
			while (it.hasNext()) {
				Integer e = it.next();
				
				// Only pickup request on the same direction with elevator
				if( (this.direction * (e - this.currFloor)) > 0 || this.currFloor == 1 
					  || this.currFloor == this.size) {
					this.stops[e-1] = true;
					it.remove();
				} 
			}
		}
	}
	
	// Implementation of SCAN algorithm: 
	// The elevator always travel to the same direction until
	// there's no furthur requests or stops on that direction.
	public int getNextFloor() {
		
		int middleFloor = this.size/2;
		// Return immediately, if there's no requests/stops AND
		// the elevator is at middle floor
		if (this.isIdle()) {
			if (this.currFloor == middleFloor) {
				return this.currFloor;
			} else {
				this.direction = (middleFloor > this.currFloor) ? 1 : -1;
				return this.currFloor + this.direction;
			}
		}
	
		// Decide the next direction.
		// If there are requests or stops in current direction, keep the direction; 
		// otherwise, reverse the direction
		int i=0;
		int idx = this.currFloor-1;
		for(i=idx+this.direction; i<this.size && i>=0; i += this.direction) {
			if(!requests.get(i).isEmpty() || this.stops[i]) break;
		}
		if(i == this.size || i<0) {
			this.direction = -this.direction;
		}
		
		return this.currFloor + this.direction;
	}
}


class ElevatorControlSystem {
	private Elevator[] elev;
	private int numOfElevators;
	private int elevatorSize;
	private boolean enableElevatorRandomizing = true;
	private boolean enablePreemptAdjust = true;
	
	
	public ElevatorControlSystem(int num, int size) {
		this.numOfElevators = num;
		this.elevatorSize = size;
		elev = new Elevator[num];
		for(int i=0; i<elev.length; i++) {
			elev[i] = new Elevator(i, size);
		}
	}
	
	// This is for testing purpose
	public void disableRandomizing() {
		enableElevatorRandomizing = false;
	}
	
	// This is for testing purpose
	public void disablePreemptAdjust() {
		enablePreemptAdjust = false;
	}
	
	public synchronized String status() {
		StringBuffer sb = new StringBuffer();
		for(Elevator e : elev) {
			sb.append(e.status().toString());
			sb.append(" ");
		}
		return sb.toString();
	}
	
	public synchronized List<ElevStatus> status2() {
		List<ElevStatus> lst = new ArrayList<ElevStatus>();
		for(Elevator e : elev) {
			lst.add(e.status());
		}
		return lst;
	}
	
	// Calculate the distance between elevator and the pickupFloor based
	// on SCAN algorithm.
	// There are three cases:
	// case 1. Elevator and request on same direction and elev on the way to pickupFloor
	// case 2. Elevator and request on same direction and elev passed pickup floor
	// case 3. Elevator and request on opposite direction
	public synchronized int getDistance(int id, int pickupFloor, int destFloor) {
		ElevStatus status = elev[id].status();
		int requestDirection = (destFloor > pickupFloor) ?  1 : -1;
		int D = Math.abs(pickupFloor - status.currFloor);
		int high_end = Math.max(Math.max(pickupFloor, status.currFloor), status.ends.high); 
		int low_end =  Math.min(Math.min(pickupFloor,status.currFloor), status.ends.low);
		int L = high_end - low_end;
		boolean isIdle = elev[id].isIdle();
		int distance;
		// We calcuate the distance value based on snapshot of requests/stops state, but
		// if there're new requests arrives after this evaluation (in case 2 and 3), the 
		// actual distance will be longer than the estimate. Hence, we add a fixed adjust
		// value to distance.
		int preempted_adjust = this.enablePreemptAdjust ? 1 : 0;
		
		// If elevator is idle, then this is special case of same direction as empty elevator
		// can go either direction
		if (requestDirection == status.direction || isIdle) {
			
			if(requestDirection * D > 0) {
				// case 1.  distance = D
				distance = D;
			} else {
				// case 2. distance = 2L - D
				distance = 2*L - D + preempted_adjust;
			}
			
		} else {
			// case 3. distance = (currFloor to one end) + (one end to pickup floor)
			int endFloor = status.direction > 0 ? high_end : low_end;
			distance = Math.abs(status.currFloor - endFloor) + Math.abs(pickupFloor - endFloor) + preempted_adjust;
		}
			
		return distance;
	}
	
	// This method is for testing purpose
	public synchronized void set(int id, int currFloor, int direction) {
		elev[id].set(currFloor, direction);	
	}
	
	// This is for testing purpose
	public synchronized Elevator get(int id) {
		return elev[id];
	}
	
	// Find the optimal elevator and assign the request
	public synchronized int pickup(int pickupFloor, int destFloor) {
		if(pickupFloor == destFloor) return 0;
		
		// Randomized elevator ids 
		ArrayList<Integer> idArr = new ArrayList<Integer>();
		for(Elevator e : elev) idArr.add(e.status().id);
		if (this.enableElevatorRandomizing == true) {
			long seed = System.nanoTime();
			Collections.shuffle(idArr, new Random(seed));
		}
		
		int dis = 2 * this.elevatorSize + 1;
		int id = this.numOfElevators;
	
		for(int i : idArr) {
			int tmp = getDistance(i, pickupFloor, destFloor);
			// System.out.println("Distance to elev "+ i + " : " + tmp);
			if (tmp < dis) {
				dis = tmp; id = i;
			}
		}
		
		// Assign the request to elev[id]
		assert(id != this.numOfElevators);
		elev[id].assign(pickupFloor, destFloor);
		
		System.out.println("elevator " + id + " status" + elev[id].status().toString());
		elev[id].updateFloor(elev[id].status().currFloor);
		
		return dis;
	}
	
	public synchronized void step() {
		for(Elevator e : elev) {
			e.updateFloor(e.getNextFloor());
		}
		
		System.out.println(this.status());
	}

}

/* Name of the class has to be "Main" only if the class is public. */
public class TestDriver
{

	// case 1: elevator and pickup request on the same direction 
	// And elevator on the way to pickup floor
	public static void pickup_test_case1() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		System.out.println("\n\n#####  " + funcname + " Started");
		
		ElevatorControlSystem ecs = new ElevatorControlSystem(1, 6);
		ecs.set(0, 1, 1);
		ecs.pickup(3, 5);
		
		if (ecs.pickup(3, 5) != 2) {
			System.out.println(funcname + " failed");
		} else {
			System.out.println(funcname + " succeed");
		}
	}
	
	// case 2: elevator and pickup request on the same direction 
	// But elevator has passed pickup floor
	public static void pickup_test_case2() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		System.out.println("\n\n#####  " + funcname + " Started");
		ElevatorControlSystem ecs = new ElevatorControlSystem(1, 6);
		ecs.disablePreemptAdjust();
		ecs.set(0, 3, 1);
		// case 1:
		if (ecs.pickup(5, 6) != 2) {
			System.out.println(funcname + " failed");
			return;
		}
		
		// case 2:
		if (ecs.pickup(3, 5) != 4) {
			System.out.println(funcname + " failed");
			return;
		}
		System.out.println(funcname + " succeed");
	}
	
	
	// case 3: elevator and pickup request on the opposite direction 
	public static void pickup_test_case3() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		System.out.println("\n\n#####  " + funcname + " Started");
		ElevatorControlSystem ecs = new ElevatorControlSystem(1, 6);
		ecs.disablePreemptAdjust();
		ecs.set(0, 4, 1);
		
		// case 1:
		if (ecs.pickup(5, 6) != 1){
			System.out.println(funcname + " failed");
			return;
		}
		// case 3:
		if (ecs.pickup(3, 1) != 3) {
			System.out.println(funcname + " failed");
			return;
		}
		System.out.println(funcname + " succeed");
	}
	
	// Test the SCAN algorithm
	public static void scan_test() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		ElevatorControlSystem ecs = new ElevatorControlSystem(1, 6);
		System.out.println("\n\n#####  " + funcname + " Started");
	
		ecs.set(0, 5,-1);
		ecs.pickup(1, 6);
		for(int i=0; i<9; i++) ecs.step();
		
		// The elevator should reach floor 6 after 9 steps (down 4 steps, up 5 steps)
		ElevStatus status = ecs.status2().get(0);
		if(status.currFloor != 6) {
			System.out.println(funcname + " Failed");
		} else {
			System.out.println(funcname + " Succeed");
		}
		
	}
	
	// The elev should return to middle if there's no requests
	public static void idle_park_test() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		ElevatorControlSystem ecs = new ElevatorControlSystem(1, 6);
		System.out.println("\n\n#####  " + funcname + " Started");
	
		ecs.pickup(1, 5);
		for(int i=0; i<10; i++) ecs.step();
		
		// After the test, the idle elevator should return to middle floor: 6/2 = 3
		ElevStatus status = ecs.status2().get(0);
		if(status.currFloor != 6/2) {
			System.out.println(funcname + " Failed");
		} else {
			System.out.println(funcname + " Succeed");
		}
	}
	
	public static void pickup_test() {
		pickup_test_case1();
		pickup_test_case2();
		pickup_test_case3();
	}
	
	public static void multi_elev_test() {
		String funcname = Thread.currentThread().getStackTrace()[1].getMethodName();
		System.out.println("\n\n#####  " + funcname + " Started");
		
		ElevatorControlSystem ecs = new ElevatorControlSystem(2, 6);
		ecs.disableRandomizing();
		
		ecs.pickup(1, 6);
		for(int i=0; i<3; i++) ecs.step();
		
		// Only elevator 0 should be used at this point
		if(ecs.get(0).isIdle() || !ecs.get(1).isIdle()) {
			System.out.println(funcname + " Failed");
		}

		ecs.pickup(4, 1);
		for(int i=0; i<2; i++) ecs.step();

		// Both elevator should be used at this point
		if(ecs.get(0).isIdle() || ecs.get(1).isIdle()) {
			System.out.println(funcname + " Failed");
		} else {
			System.out.println(funcname + " Succeed");
		}
	}
	
	public static void main (String[] args) throws java.lang.Exception
	{
		
		scan_test();
		idle_park_test();
		pickup_test();
		
		multi_elev_test();
	}
}