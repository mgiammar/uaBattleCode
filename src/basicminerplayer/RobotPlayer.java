package basicminerplayer;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    
    /**
     * Private Instance Variables used by all robots (note - some may not be valid or used)
     * 
     * TEAM_HQ_LOCATION is MapLocation of where the team HQ is positioned
     * ENEMEY_HQ_LOCATION is MapLocation of where the enemy HQ is positioned
     * SPAWN_LOCATION is MapLocation of where this spesific unit has spawned
     * 
     * MAP_HEIGHT is an int which represents the map height
     * MAP_WIDTH is an int which represents the map width
     * 
     * objective is a numerical representation of what the current robot wants to accomplish.  See objectives.txt to find objective description
     * movement is a numerical representation of how the current robot should move.  See movement.txt to find movement descriptions
     * 
     * targetLocation is MapLocation of where the current target
     * 
     * transactionIdentifier is a number added to transactions to identify that transaction as from this team
     */
    static MapLocation TEAM_HQ_LOCATION = new MapLocation(-1, -1);  
    static MapLocation ENEMY_HQ_LOCATION = new MapLocation(-1, -1); 
    static MapLocation SPAWN_LOCATION = new MapLocation(-1, -1); //can this be updates as the robot is created
    
    static int MAP_HEIGHT = 0;
    static int MAP_WIDTH = 0;
    
    //Maybe add a transaction array for the round one block for every robot to add to?
    //Round 1 block will need more useful information for this to be useful
    
    static int objective = 0; 
//    static int movement = 0;
    
    static MapLocation targetLocation = new MapLocation(-1, -1);
    static final MapLocation INVALID_LOCATION = new MapLocation(-1, -1);
    
    static int transactionIdentifier = 420; //Blaze it
    
    
    /**
     * Private Instance Variables only used by the Miner robot
     * 
     * MIN_SOUP_AMOUNT is the minimum amount of soup a tile must have so the miner goes toward that tile
     * MIN_SOUP_DEPOSIT_AMOUNT is the minimum amount of soup a miner must collect before refining 
     * 
     * closestRefinery is a MapLocation of the last closest refinery.  New refinery are searched for every (x) rounds
     */
    static final int MIN_SOUP_AMOUNT = 200;
    static final int MIN_SOUP_DEPOSIT_AMOUNT = 100;
    
    static MapLocation closestRefinery = new MapLocation(-1, -1); //MapLocation of the nearest refinery
    
    
    
    
    
    //Need different variables to describe what the objective of current robot.  Ex miners want to go to soup unless they have too much
    //Also need to differentiate between robot types

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        //System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
                //System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case REFINERY:           runRefinery();          break;
                    case VAPORATOR:          runVaporator();         break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
                    case NET_GUN:            runNetGun();            break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }
    
    //Code for the HQ
    static void runHQ() throws GameActionException {
    	
    	if(turnCount == 1) { //First turn of the HQ
    		TEAM_HQ_LOCATION = rc.getLocation();
    		MAP_HEIGHT = rc.getMapHeight();
    		MAP_WIDTH = rc.getMapWidth();
    		System.out.println(turnCount);
    		System.out.println("I am the HQ located at " + TEAM_HQ_LOCATION);
    		System.out.println("The map is " + MAP_HEIGHT + " by " + MAP_WIDTH);
    		
    		MapLocation maxSoupLocation = advancedScanForSoup(rc.getCurrentSensorRadiusSquared());
    		//System.out.println("The maximum amount of soup is located at (" + maxSoup.x + ", " + maxSoup.y + ")");
    		int[] message = {TEAM_HQ_LOCATION.x, TEAM_HQ_LOCATION.y, maxSoupLocation.x, maxSoupLocation.y, 0, 0, transactionIdentifier}; //first two ints are the HQ xy pos and second are closest soup locations
    		rc.submitTransaction(message, 1);
    		
    		if(maxSoupLocation.x != -1) { // If there is soup near, spawn closest to soup.  Otherwise go towards center of map
    			//cnage these to tryBuild in case there is an unhandeled exception
    			rc.buildRobot(RobotType.MINER, TEAM_HQ_LOCATION.directionTo(maxSoupLocation));
    		} else {
    			rc.buildRobot(RobotType.MINER, TEAM_HQ_LOCATION.directionTo(new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2)));
    		}
    		
    	}
        
    }

    //Code for the miner
    //Currently want to move around to seek soup and mine that soup
    //Once an amount of soup has been gathered, move back to HQ and deposit
    static void runMiner() throws GameActionException {
    	System.out.println("Miner turn " + turnCount);
    	System.out.println("Soup: " + rc.getSoupCarrying());
    	
    	if(turnCount == 1) {
    		Transaction[] roundOneBlock = rc.getBlock(1);
    		debugPrintTransactionBlock(roundOneBlock);
    		Transaction[] roundOneTeamBlock = seperateTransactionsFromTeam(roundOneBlock, rc.getTeam());
    		int[] roundOneMessage = roundOneTeamBlock[0].getMessage();
    		
    		TEAM_HQ_LOCATION = new MapLocation(roundOneMessage[0], roundOneMessage[1]);
    		MAP_HEIGHT = rc.getMapHeight();
    		MAP_WIDTH = rc.getMapWidth();
    		closestRefinery = TEAM_HQ_LOCATION;
    		
    		targetLocation = new MapLocation(roundOneMessage[2], roundOneMessage[3]);
    		if(targetLocation.equals(INVALID_LOCATION)) {
    			objective = 1;
//    			movement = 2;
    		} else {
    			targetLocation = advancedScanForSoup(rc.getCurrentSensorRadiusSquared());
    			objective = 2;
//    			movement = 1;
    		}
    	}
    	

/**
 *   	switch(movement) {
 *   		case 0: //no movement
 *   		break; 
 *  		
 *   		case 1: //Move toward the current objective
 *   			if(targetLocation.isAdjacentTo(rc.getLocation())) {
 *   				movement = 0;
 *   				objective = 2;
 *   			}
 *   			moveToward(targetLocation);
 *   		break;
 *   		
 *   		case 2: //Wander Randomly	
 *   			Direction d = randomDirection();
 *   			while(!(tryMove(d))) {
 *   				//update scan
 *   				d = randomDirection();
 *  				Clock.yield();
 *   			}
 *   		
 *   	}
**/
    	
    	switch(objective) {
    		case 0: //do nothing
    		break;
    		
    		case 1: //Search for soup
    			if(turnCount % 4 == 0) { //Will search for soup every 4 turns (or there is enough bytecode left)
		    		MapLocation soupLocation = advancedScanForSoup(rc.getCurrentSensorRadiusSquared());
		    		if(!(soupLocation.equals(INVALID_LOCATION))) { //If soup has been found
		    			objective = 2;
		    			targetLocation = soupLocation;
		    		}
    			}
    			moveToward(targetLocation);
    		break;
    		
    		
    		case 2: //Go to soup
    			if(rc.getLocation().isAdjacentTo(targetLocation)) {
    				objective = 3;
    				tryMine(rc.getLocation().directionTo(targetLocation));
    			} else {
    				moveToward(targetLocation);
    			}
    		break;
    		
    		
    		case 3: //Mine soup
    			if(rc.getSoupCarrying() >= MIN_SOUP_DEPOSIT_AMOUNT) {
    				objective = 4;
    				moveToward(closestRefinery);
    			} else {
    				tryMine(rc.getLocation().directionTo(targetLocation));
    			}
    		break;
    		
    		
    		case 4: //Move to refinery and then deposit
    			if(rc.getLocation().isAdjacentTo(closestRefinery)) {
    				if(tryRefine(rc.getLocation().directionTo(closestRefinery)))
    					objective = 1;
    			} else {
    				moveToward(closestRefinery);
    			}
    			
    			
    	}
    } 
    
    //Code for the ref
    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }
    
    //Code for the vap
    static void runVaporator() throws GameActionException {

    }

    //Code for the des school
    static void runDesignSchool() throws GameActionException {

    }
    
    //Code for the ful center
    static void runFulfillmentCenter() throws GameActionException {

    }
    
    //Code to run landscaper
    //Currently wan to build a wall around HQ
    static void runLandscaper() throws GameActionException {

    }
    
    //Code to run delivery drones
    static void runDeliveryDrone() throws GameActionException {
       
    }

    //code to run net gun
    static void runNetGun() throws GameActionException {

    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }
    
    //Code to check if a robot can move
    //@return true if can move, else false
    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }


    static void tryBlockchain() throws GameActionException {
        if (turnCount < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
    

    /**
     * Scans the area for the highest amount of soup
     * 
     * @param None
     * @return MapLocation of the highest amount of soup
     * @throws GameActionException
     */
    static MapLocation scanForSoup() throws GameActionException {
    	int maxSoup = 0;
    	MapLocation maxSoupLocation = new MapLocation(-1, -1); 
    	
    	for(int x = -5; x <= 5; x++) {
    		for(int y = -5; y <= 5; y++) {
    			MapLocation testLoc = rc.getLocation().translate(x, y);
    			if(rc.canSenseLocation(testLoc)) {
    				if(rc.senseSoup(testLoc) > maxSoup && !(rc.senseFlooding(testLoc))) {
    					maxSoup = rc.senseSoup(testLoc);
    					maxSoupLocation = testLoc;
    				}
    			}
    		}
    	}
    	return maxSoupLocation;
    }
    
    static MapLocation advancedScanForSoup(int radius) throws GameActionException {
    	int maxSoup = 0;
    	MapLocation maxSoupLocation = new MapLocation(-1, -1); 
    	
    	int rootRadius = (int) Math.floor(Math.sqrt(radius));
    	
    	for(int x = -rootRadius; x <= rootRadius; x++) {
    		for(int y = -rootRadius; y <= rootRadius; y++) {
    			//System.out.println(x + " " + y);
    			if(rc.canSenseLocation(rc.getLocation().translate(x, y))) {
    				if(rc.senseSoup(rc.getLocation().translate(x, y)) > maxSoup && !(rc.senseFlooding(rc.getLocation().translate(x, y)))) {
    					maxSoup = rc.senseSoup(rc.getLocation().translate(x, y));
    					maxSoupLocation = rc.getLocation().translate(x, y);
    				}
    			}
    		}
    	}
    	
    	
    	return maxSoupLocation;
    }
    
    /**
     * Moves toward the objective location 
     * 
     * @param loc to move toward
     * @return true if the movement was performed
     * @throws GameActionException
     */
    static boolean moveToward(MapLocation loc) throws GameActionException {
    	Direction dir = rc.getLocation().directionTo(loc);
    	
    	if(!(rc.senseFlooding(rc.getLocation().add(dir))) && rc.canMove(dir)) {
    		rc.move(dir);
    		return true;
    	}
    	return false;
    }
    
    /**
     * Finds the nearest type of robot
     * 
     * @param type of robot to look for
     * @return MapLocation of the robot, -1, -1 otherwise (invalid MapLocation
     * @throws GameActionException
     */
    static MapLocation findNearestRobotType(RobotType type) {
    	RobotInfo[] robots = rc.senseNearbyRobots();
    	for(RobotInfo robot : robots) {
    		if(robot.type.equals(type)) {
    			return robot.location;
    		}
    	}
    	return null;
    }
    
    /**
     * Finds the nearest type of robot
     * 
     * @param type of robot to look for and which team
     * @return MapLocation of the robot, null otherwise
     * @throws GameActionException
     */
    static MapLocation findNearestRobotTypeOnTeam(RobotType type, Team team) {
    	RobotInfo[] robots = rc.senseNearbyRobots();
    	for(RobotInfo robot : robots) {
    		if(robot.type.equals(type) && robot.team.equals(team)) {
    			return robot.location;
    		}
    	}
    	return null;
    }
    
    /**
     * Prints off block of transactions
     * 
     * @param array of transactions
     * @throws GameActionException
     */
    static void debugPrintTransactionBlock(Transaction[] block) throws GameActionException {
    	for(Transaction trans : block) {
			for(int test : trans.getMessage()) {
				System.out.print(test + ", ");
			}
			System.out.println();
		}
    }
    
    /**
     * Verifies a message is from our team
     * 
     * @param message (int array) to be verified
     * @returns true if the message is from our team, false otherwise
     * @throws GameActionException
     */
    static boolean verifyMessage(int[] message) throws GameActionException {
    	//Implement later
    	return(message[6] == transactionIdentifier);
    }
    
    /**
     * Returns transactions from a specific team from an array of transactions (block)
     * 
     * @param block of transactions and team
     * @returns block of transactions from specific team
     * @throws GameActionException
     */
    static Transaction[] seperateTransactionsFromTeam(Transaction[] block, Team team) throws GameActionException {
    	Transaction[] teamBlock = new Transaction[7];
    	int teamBlockIndex = 0;
    	
    	for(int i = 0; i < block.length; i++) {
    		if(verifyMessage(block[i].getMessage())) {
    			teamBlock[teamBlockIndex++] = block[i];
    		}
    	}
    	
    	return teamBlock;
    }
}
