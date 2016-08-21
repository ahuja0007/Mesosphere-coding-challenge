
## Problem Specification

Design and implement an elevator control system. What data structures, interfaces and algorithms will you need? Your elevator control system should be able to handle a few elevators -- up to 16.

You can use the language of your choice to implement an elevator control system. In the end, your control system should provide an interface for:

Querying the state of the elevators (what floor are they on and where they are going),

receiving an update about the status of an elevator,

receiving a pickup request,

time-stepping the simulation.


## Design of two-level scheduling algorithms

From high level, the model of elevator scheduling system is master-slave system. ECS system is the master which delegates the jobs (pickup requests), while elevators
is the slaves which handle the requests internally.

The algorithms can be divided into two levels: Elevator selection algorithm as first level, which decide which elevator to assign requests; 
and individual elevator scheduling algorithm as second level, which decides how to serve the requests internally inside that elevator.

This design has following benefits: 
* 1. simplification of the design. Because each level is responsible for one aspect of scheduling problem, we can design them independently.
* 2. failure isolation. After requests assignement, the requests works independently inside each elevator, so failure are isolated between elevators.
* 3. scaling independently. We can scale each system (either master or slaves) independently based on future needs.
* 4. flexible extension. We can independently change either level of algorithm without impacting the its counterpart.

Now, we detail the algorithms as following. 
First let's define some terms used in below examples. Suppose all elevators have 10 floor, and elevator state denoted as (A, UP), 
where 'A' is current floor and 'UP' is direction. Pickup requests are denoted as (X, Y) whereas X is pickup floor and Y is drop-off floor.

### Individual elevator scheduling algorithm  (Modified SCAN)

We use a slightly modified version of SCAN algorithm as our elevator scheduling algorithm. Because SCAN is widely used scheduling algorithm, it's simple, robust and deterministic.  

* Traditional SCAN algorithm: elevator continues to travel in its current direction (up or down) until empty, stopping only to let individuals off or to pick up new individuals heading in the same direction.

when additional requests arrive, requests are serviced only in the current direction of elevator movement until the it reaches the end of elevator When this happens, the direction of the elevator reverses, and the requests that were remaining in the opposite direction are serviced, and so on. 

* The modified version: By default, elevator travels to the end of elevator before reverse even though there are no requests on that end. We think it's more efficient to travel up to furtherst floor before reverses. For example, the elevator state is (3, UP) and stops is [8], then elevator will reverses at floor 8 (instead of 10). However, this modification brings complexity to distance estimate for given pickup request, because new request which stop is beyond current furtherst stop could increase the elevator travel distance. We will discuss and address this issue in "Request preemption handling" section.

### Elevator selection algorithm

#### Basic Algorithm
  
The second part is to which elevator to select to serve the requests. To simplify the problem we have one design goal: quick response time, because common user experience is based on how fast they can access the elevator. To achieve that, we use metric "travel distance" as score to rate each elevator and pick the one with shortest distance. The "travel distance" is defined as the number of floors the elevator needs to move to arrive the pickup floor based on modified SCAN algorithm.

Regarding the travel distance calculation, there are three cases to consider.

* case 1: The elevator and the pickup request are on the same direction and elevator on the way to pickup floor. For example, elevator state is (1, UP) and the request is (3, 5). So the distance is D = (3 - 1) = 2. 

* case 2: The elevator and the pickup request are on the same direction but elevator has passed pickup floor. For example, elevator state is (6, UP)  and the stops are [1, 8]. For the request (3, 5), the distance will be 2*L - D = 2 * (8 - 1) - (6 - 3) = 17. where L is distance of two furtherst stops and D is distance of pickup floor and currentFloor. In this example, L = (8 - 1), and D = (6 - 3).  Keep in mind we need to handle corner cases of a) there's no stops b) there's only one stop which could be on either side of elevator moving direction. You can find that in the code.

* case 3: The elevator and the pickup request are on opposite direction. For example, elevator state is (5, UP) with stops [8] and the request (3, 1). The distance will be abs(currFloor - endfloor) + abs(pickupFloor - endFloor) = abs(5 - 8) + abs(3 - 8) = 8.  Where currFloor is 5, endFloor 8 and pickupFloor 3.

#### Enhancements

Beyond the basic algorithm, there are several areas to enhance our ECS system.

* Request preemption handling. 
Current distance calculation doesn't consider the preemption of new requests which potentially increases the travel distance of elevator to pickup floor. For example, in case 2, elevator state is (6, UP) with stops [1, 8], and pickup request (3, 5). The static distance is 2*L - D = 17. When the new request(10,1) arrives immediately after the calculation, now the distance will be 21 due to the increased L = (10-1). 

The problem is, the calculation of the distance is based on point-in-time snapshot of requests/stops state, but new requests could change the state. This problem exists only in case 2 and 3, and to address that, we add a fixed preemptive_adjust value to the distance.

* Default floor to park idle elevator.
Another detail is which floor to park idle elevator. Our strategy is choosing the middle floor based on the assumption that pickup requests are uniformly distributed across all floors. So when elevator is idle, it should move in the background and park at the default floor.

* Randomizing elevator selection. To make sure all elevators get equal selection opportunity, we randomize the elevator ids before selecting them. This ensure we don't always end up selecting the same elevator if there are multiple elevators under the same conditions. For example, when all elevators are idle, we don't want to end up with selecting elevator 1 every time. 

### Thoughts on future improvements

Currently, we use only travel distance as score to rate elevator, based on design goal of quick response. However, in reality, user experience may depend on other factors such as how many stops before the destination, or crowdedness of elevator etc. 

* Load balance. We could factor in the load of each elevator. For example, some users are wiling to wait longer time to choose less crowded elevator. However, that will complicates the elevator selection algorithm due to two potential conflicting goals: a) the quick response time b) load balanced across elevator. 

* Fast track. Currently we treat all elevator equals which stops at every floor. In real world, there should be fast track elevator which stops only at certain floors. That improve user experience by reducing number of stops before destination, especially for passengers travel long distance of floors.

* Machine learning. Currently we assume the requests are uniformly distributed across all floors. In real world, this might not be true. For example, the requests could be more frequent on first floor as this normally is entrance to the building. Moreover, we could discover some patterns based on usage heuristics, and design the elevator to adaptive to these pattern. For example, if requests are mostly coming from certain floor range, then we could assign dedicated elevator to that floor range (for certain period of time).

* Dynamic change of request from inside of the elevator. Some users may want to change the drop-off floors after getting into the elevators. 

* Fine-grain locking. We currently have big lock for reach ECS operation. we could improve that by using finer grain locking mechanism.


## Implementation 

In the systems, there are two main objects: the ECS (elevator control system) object and Elevator object.

### Elevator object

#### Members 

It includes elevator id, size, direction, current floor number, requests, and stops.
* requests: maitain pickup requests for each floor. List of list, indexed by (floor number - 1).
* stops: a boolean array indexed by (floor number - 1).

#### Methods: 

* status: return the status of the elevator
* assign(pickupFloor, dropOffFloor): append the request(pickup, dropoff) to request list of corresponding floor
* updateFloor(floor): remove pickup requests from request list if on the same direction, followed by update the stops array.
* getNextFloor: decide the elevator next direction, followed by move elevator by 1 floor

### ECS object

#### Members

* elev: array of elevators
* numOfElevator: number of total elevators
* elevatorSize: size of the elevators

#### Methods

* status: return status of the all elevatators 
* pickup(pickupFloor, dropffFloor): select elevator and assign the request to it. Implement the elevator selection algorithm.
* getDistance: return travel distance to the pickup floor. Implement the SCAN algorithm. 
* step: time-stepping the simulation
