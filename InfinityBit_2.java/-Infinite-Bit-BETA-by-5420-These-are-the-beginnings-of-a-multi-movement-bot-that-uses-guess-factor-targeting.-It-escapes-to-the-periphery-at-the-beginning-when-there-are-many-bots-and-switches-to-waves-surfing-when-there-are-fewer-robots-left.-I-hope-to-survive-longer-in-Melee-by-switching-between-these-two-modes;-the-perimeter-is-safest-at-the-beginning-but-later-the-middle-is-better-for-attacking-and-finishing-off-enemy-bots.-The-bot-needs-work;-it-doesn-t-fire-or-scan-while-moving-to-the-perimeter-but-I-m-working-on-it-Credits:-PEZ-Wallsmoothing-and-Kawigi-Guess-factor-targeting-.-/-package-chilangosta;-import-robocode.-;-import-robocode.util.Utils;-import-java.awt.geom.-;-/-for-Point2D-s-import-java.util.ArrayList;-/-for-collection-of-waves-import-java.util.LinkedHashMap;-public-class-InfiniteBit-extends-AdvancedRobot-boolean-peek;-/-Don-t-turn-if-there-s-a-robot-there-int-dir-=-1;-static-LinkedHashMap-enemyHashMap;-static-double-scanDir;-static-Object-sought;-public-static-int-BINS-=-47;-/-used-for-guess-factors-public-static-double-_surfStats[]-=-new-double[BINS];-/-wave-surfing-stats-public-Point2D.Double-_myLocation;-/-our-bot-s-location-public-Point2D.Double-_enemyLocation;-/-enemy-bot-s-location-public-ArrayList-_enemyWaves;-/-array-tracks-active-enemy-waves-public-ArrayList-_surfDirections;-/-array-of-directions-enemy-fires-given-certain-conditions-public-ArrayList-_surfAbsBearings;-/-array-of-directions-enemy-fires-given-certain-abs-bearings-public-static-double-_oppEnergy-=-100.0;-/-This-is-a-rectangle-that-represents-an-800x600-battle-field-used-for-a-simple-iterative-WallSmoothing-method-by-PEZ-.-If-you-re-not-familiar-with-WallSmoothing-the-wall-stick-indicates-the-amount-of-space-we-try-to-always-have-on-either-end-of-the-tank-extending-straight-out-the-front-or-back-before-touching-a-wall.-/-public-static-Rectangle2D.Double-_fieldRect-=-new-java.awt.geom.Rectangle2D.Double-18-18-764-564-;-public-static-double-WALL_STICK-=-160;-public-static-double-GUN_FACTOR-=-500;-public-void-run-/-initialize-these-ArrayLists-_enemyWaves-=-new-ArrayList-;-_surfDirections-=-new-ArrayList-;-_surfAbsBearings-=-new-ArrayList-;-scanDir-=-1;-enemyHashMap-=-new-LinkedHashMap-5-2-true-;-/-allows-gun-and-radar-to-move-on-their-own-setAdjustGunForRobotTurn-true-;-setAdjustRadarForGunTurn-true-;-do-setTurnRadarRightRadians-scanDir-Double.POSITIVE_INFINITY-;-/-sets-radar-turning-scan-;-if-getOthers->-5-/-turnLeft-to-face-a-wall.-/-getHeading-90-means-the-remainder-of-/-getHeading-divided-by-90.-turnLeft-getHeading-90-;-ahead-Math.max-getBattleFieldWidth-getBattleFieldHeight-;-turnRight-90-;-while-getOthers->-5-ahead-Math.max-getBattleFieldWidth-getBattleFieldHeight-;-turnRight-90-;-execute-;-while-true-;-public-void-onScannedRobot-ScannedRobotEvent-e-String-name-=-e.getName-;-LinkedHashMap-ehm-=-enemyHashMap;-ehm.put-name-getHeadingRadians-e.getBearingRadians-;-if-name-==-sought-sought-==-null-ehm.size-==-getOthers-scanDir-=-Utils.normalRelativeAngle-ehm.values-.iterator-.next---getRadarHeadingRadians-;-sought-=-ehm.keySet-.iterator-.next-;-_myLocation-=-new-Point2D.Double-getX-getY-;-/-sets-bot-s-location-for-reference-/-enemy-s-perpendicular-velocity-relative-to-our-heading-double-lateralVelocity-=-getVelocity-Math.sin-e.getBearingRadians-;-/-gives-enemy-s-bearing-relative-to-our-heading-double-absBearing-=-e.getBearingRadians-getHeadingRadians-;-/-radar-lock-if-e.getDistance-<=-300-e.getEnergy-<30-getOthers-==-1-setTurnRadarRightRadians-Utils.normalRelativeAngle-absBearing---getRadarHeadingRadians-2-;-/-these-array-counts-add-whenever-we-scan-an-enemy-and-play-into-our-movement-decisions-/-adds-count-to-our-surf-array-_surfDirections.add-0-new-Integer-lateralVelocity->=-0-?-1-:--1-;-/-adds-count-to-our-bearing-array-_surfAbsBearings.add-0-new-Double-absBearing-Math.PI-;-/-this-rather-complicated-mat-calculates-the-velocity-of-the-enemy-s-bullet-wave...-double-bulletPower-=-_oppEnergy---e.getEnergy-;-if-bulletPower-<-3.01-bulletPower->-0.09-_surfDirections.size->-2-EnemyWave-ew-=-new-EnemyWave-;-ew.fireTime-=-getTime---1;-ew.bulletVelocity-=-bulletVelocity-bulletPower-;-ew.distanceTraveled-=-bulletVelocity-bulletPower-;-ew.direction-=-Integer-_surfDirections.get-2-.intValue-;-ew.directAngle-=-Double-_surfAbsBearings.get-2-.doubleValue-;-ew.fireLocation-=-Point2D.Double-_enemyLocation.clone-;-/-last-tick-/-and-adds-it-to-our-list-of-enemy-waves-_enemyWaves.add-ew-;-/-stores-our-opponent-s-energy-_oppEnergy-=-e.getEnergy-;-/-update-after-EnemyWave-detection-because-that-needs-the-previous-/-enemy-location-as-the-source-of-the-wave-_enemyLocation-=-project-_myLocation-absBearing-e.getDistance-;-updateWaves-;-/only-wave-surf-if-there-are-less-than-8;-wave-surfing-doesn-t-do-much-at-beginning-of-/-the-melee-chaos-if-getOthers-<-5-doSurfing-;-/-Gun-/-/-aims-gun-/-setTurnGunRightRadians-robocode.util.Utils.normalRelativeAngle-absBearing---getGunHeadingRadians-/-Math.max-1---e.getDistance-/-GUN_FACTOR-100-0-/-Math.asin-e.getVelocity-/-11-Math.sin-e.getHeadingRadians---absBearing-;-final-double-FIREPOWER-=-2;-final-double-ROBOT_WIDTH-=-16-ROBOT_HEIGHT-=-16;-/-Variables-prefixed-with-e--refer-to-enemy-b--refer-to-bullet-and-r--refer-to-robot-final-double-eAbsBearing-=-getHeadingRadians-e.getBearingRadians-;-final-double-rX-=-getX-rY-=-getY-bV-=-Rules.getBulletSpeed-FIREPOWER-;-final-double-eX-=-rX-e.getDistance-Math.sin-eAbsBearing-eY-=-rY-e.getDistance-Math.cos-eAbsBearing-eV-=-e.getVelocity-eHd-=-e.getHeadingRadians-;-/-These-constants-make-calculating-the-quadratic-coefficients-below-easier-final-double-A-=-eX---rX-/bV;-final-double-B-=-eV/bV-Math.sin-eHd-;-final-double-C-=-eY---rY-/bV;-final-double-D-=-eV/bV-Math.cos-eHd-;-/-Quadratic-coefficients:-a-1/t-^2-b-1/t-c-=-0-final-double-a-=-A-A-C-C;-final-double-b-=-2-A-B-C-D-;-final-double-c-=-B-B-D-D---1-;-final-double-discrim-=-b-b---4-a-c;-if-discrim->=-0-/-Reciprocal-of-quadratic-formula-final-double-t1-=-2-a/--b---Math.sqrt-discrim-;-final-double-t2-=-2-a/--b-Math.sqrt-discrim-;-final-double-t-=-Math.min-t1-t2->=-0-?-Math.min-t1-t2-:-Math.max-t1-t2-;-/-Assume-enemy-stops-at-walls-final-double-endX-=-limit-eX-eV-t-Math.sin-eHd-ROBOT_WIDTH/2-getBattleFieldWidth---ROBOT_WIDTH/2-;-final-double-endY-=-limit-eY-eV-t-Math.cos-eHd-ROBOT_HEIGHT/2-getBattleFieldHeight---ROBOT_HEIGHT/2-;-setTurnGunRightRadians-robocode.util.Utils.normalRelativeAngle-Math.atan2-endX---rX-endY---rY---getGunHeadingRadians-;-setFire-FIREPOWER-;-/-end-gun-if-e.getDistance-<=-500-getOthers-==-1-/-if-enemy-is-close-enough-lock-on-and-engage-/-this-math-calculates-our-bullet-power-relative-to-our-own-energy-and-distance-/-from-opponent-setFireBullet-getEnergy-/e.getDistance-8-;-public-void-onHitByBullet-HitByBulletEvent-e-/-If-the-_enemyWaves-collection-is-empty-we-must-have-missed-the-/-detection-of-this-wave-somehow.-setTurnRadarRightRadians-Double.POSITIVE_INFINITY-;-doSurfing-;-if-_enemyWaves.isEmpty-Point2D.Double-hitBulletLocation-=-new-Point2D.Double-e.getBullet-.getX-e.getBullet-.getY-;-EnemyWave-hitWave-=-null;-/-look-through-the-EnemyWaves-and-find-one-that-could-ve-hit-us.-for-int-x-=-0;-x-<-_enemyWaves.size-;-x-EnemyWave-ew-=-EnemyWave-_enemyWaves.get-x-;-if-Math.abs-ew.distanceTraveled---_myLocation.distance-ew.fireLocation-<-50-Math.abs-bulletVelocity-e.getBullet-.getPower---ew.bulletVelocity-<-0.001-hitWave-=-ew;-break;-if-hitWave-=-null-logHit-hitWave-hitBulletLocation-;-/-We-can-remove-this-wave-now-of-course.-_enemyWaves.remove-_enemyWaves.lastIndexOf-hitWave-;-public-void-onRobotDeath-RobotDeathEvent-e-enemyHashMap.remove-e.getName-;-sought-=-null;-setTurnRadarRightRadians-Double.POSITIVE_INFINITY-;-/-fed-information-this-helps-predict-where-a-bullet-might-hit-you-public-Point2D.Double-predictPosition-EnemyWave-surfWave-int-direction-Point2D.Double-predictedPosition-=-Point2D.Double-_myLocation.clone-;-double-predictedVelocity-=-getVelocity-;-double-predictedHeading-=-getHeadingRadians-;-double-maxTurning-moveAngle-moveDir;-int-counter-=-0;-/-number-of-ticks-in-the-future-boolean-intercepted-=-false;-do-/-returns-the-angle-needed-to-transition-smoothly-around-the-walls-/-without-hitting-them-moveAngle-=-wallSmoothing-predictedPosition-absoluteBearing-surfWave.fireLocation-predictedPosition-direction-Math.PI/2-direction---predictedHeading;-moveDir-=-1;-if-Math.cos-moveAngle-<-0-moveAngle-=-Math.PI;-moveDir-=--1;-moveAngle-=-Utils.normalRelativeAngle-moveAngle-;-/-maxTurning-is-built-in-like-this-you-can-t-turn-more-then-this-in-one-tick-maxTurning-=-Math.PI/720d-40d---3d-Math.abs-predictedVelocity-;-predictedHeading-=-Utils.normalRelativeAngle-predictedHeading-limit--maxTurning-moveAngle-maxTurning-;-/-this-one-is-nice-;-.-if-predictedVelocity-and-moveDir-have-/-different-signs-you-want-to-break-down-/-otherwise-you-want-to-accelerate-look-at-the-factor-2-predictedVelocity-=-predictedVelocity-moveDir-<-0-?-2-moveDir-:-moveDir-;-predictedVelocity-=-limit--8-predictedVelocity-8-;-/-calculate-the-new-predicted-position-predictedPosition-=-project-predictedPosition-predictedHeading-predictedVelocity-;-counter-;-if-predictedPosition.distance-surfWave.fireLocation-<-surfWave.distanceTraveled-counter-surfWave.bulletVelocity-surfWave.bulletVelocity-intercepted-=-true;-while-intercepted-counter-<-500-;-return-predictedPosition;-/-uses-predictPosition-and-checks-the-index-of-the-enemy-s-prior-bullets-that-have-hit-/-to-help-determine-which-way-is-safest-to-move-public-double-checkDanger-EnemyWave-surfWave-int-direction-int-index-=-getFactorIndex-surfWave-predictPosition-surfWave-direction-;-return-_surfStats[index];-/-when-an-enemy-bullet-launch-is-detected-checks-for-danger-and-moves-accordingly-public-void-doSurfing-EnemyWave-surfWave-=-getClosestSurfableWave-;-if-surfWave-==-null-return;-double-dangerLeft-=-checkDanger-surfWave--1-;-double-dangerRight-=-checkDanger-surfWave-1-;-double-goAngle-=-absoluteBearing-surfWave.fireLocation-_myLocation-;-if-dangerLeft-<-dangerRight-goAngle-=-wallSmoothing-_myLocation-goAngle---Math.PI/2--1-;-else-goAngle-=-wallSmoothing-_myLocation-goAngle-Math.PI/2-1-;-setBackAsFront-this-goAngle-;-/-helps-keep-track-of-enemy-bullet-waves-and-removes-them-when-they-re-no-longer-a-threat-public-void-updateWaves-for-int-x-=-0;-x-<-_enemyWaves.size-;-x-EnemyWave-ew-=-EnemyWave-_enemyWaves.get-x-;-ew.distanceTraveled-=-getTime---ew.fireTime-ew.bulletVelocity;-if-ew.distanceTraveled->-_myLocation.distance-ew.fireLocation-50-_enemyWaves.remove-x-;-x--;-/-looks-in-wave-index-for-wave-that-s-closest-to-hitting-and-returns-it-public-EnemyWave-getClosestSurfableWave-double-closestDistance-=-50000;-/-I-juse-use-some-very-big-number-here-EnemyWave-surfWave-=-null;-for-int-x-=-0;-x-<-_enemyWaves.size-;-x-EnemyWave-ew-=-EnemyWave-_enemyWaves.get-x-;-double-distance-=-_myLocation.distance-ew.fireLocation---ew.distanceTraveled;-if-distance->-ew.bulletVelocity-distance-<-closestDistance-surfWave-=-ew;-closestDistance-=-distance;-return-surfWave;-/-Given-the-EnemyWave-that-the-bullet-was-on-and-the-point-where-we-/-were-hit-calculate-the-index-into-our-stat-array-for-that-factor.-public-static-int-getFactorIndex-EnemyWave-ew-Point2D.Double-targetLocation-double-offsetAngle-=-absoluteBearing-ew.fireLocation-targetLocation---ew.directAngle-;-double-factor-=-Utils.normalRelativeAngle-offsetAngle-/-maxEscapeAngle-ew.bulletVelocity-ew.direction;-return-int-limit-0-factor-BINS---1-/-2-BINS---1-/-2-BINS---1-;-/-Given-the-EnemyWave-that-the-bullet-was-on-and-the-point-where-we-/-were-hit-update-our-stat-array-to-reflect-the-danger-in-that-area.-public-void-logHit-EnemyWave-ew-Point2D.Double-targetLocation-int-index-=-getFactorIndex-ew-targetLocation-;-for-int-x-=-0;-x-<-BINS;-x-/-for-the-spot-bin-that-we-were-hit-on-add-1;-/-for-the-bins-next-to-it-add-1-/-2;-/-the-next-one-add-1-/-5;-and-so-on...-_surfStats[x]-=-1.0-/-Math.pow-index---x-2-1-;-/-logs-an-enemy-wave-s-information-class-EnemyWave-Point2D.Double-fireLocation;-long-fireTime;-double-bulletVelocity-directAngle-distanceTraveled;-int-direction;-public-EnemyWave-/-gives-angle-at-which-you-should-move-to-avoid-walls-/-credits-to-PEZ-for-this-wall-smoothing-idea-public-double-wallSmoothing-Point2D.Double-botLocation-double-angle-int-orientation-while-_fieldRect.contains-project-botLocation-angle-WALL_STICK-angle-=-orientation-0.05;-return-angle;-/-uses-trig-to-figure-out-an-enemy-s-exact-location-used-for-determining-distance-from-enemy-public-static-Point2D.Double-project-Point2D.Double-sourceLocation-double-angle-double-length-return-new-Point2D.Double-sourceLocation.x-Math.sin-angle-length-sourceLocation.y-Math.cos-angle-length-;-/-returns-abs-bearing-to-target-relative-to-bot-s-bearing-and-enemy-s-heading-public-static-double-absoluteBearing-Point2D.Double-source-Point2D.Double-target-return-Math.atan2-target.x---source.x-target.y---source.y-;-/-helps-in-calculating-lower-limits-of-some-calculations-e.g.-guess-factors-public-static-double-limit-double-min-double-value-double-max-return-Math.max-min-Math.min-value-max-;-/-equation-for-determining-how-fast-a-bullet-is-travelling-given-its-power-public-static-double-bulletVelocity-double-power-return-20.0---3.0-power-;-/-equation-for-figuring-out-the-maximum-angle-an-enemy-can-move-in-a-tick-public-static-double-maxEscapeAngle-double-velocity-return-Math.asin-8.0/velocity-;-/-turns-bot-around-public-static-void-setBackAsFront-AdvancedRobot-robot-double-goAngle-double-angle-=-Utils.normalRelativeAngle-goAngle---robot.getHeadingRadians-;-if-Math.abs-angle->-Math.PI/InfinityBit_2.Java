/*
 * Infinite Bit *BETA* by 5420
 *   	These are the beginnings of a multi-movement bot that uses guess-factor targeting. 
 * 		It escapes to the periphery at the beginning when there are many bots and switches 
 * 		to waves surfing when there are fewer robots left.
 * 
 * 		I hope to survive longer in Melee by switching between these two modes; the perimeter
 * 		is safest at the beginning, but later the middle is better for attacking and finishing
 * 		off enemy bots. The bot needs work; it doesn't fire or scan while moving to the perimeter,
 * 		but I'm working on it!
 * 
 * 		Credits: PEZ (Wallsmoothing) and Kawigi (Guess-factor targeting).
 */
package chilangosta;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.*;     // for Point2D's
import java.util.ArrayList; // for collection of waves
import java.util.LinkedHashMap;

public class InfiniteBit extends AdvancedRobot
{

	boolean peek; // Don't turn if there's a robot there
	int dir = 1;
	
	static LinkedHashMap<String, Double> enemyHashMap;
    static double scanDir;
    static Object sought;
	
	public static int BINS = 47;	// used for guess factors
    public static double _surfStats[] = new double[BINS];	// wave surfing stats
    public Point2D.Double _myLocation;     // our bot's location
    public Point2D.Double _enemyLocation;  // enemy bot's location
 
    public ArrayList _enemyWaves;	// array tracks active enemy waves
    public ArrayList _surfDirections;	// array of directions enemy fires given certain conditions
    public ArrayList _surfAbsBearings;	// array of directions enemy fires given certain abs bearings
 
    public static double _oppEnergy = 100.0;
 
   /* This is a rectangle that represents an 800x600 battle field,
    * used for a simple, iterative WallSmoothing method (by PEZ).
    * If you're not familiar with WallSmoothing, the wall stick indicates
    * the amount of space we try to always have on either end of the tank
    * (extending straight out the front or back) before touching a wall.
    */
    public static Rectangle2D.Double _fieldRect
        = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;
    public static double GUN_FACTOR = 500;
 
    public void run() {
    	// initialize these ArrayLists
        _enemyWaves = new ArrayList();
        _surfDirections = new ArrayList();
        _surfAbsBearings = new ArrayList();
        
        scanDir = 1;
        enemyHashMap = new LinkedHashMap<String, Double>(5, 2, true);
 
        // allows gun and radar to move on their own
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
 
        do {
        	setTurnRadarRightRadians(scanDir * Double.POSITIVE_INFINITY);	// sets radar turning
            scan();
        	
            if (getOthers() > 5){
         // turnLeft to face a wall.
    		// getHeading() % 90 means the remainder of
    		// getHeading() divided by 90.
    		turnLeft(getHeading() % 90);
    		ahead(Math.max(getBattleFieldWidth(), getBattleFieldHeight()));
    		turnRight(90);
            }
     
    		while (getOthers() > 5) {
    			
    			ahead(Math.max(getBattleFieldWidth(), getBattleFieldHeight()));
        		turnRight(90);
            }    
            execute();
        } while (true);

    }

    public void onScannedRobot(ScannedRobotEvent e) {
    	String name = e.getName();
        LinkedHashMap<String, Double> ehm = enemyHashMap;
     
        ehm.put(name, getHeadingRadians() + e.getBearingRadians());
     
        if ((name == sought || sought == null) && ehm.size() == getOthers()) {
    	scanDir = Utils.normalRelativeAngle(ehm.values().iterator().next()
                - getRadarHeadingRadians());
            sought = ehm.keySet().iterator().next();
        }
    	
        _myLocation = new Point2D.Double(getX(), getY());	// sets bot's location (for reference)
 
        // enemy's perpendicular velocity, relative to our heading
        double lateralVelocity = getVelocity()*Math.sin(e.getBearingRadians());
        // gives enemy's bearing relative to our heading
        double absBearing = e.getBearingRadians() + getHeadingRadians();
 
        // radar lock
        if ((e.getDistance() <= 300 & e.getEnergy()<30) || getOthers() == 1)
	        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing
	            - getRadarHeadingRadians()) * 2);
	 
        // these array counts add whenever we scan an enemy, and play into our movement decisions
        // adds count to our surf array
        _surfDirections.add(0,
            new Integer((lateralVelocity >= 0) ? 1 : -1));
        // adds count to our bearing array
        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));
 
 
        // this rather complicated mat calculates the velocity of the enemy's bullet wave...

        double bulletPower = _oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09
            && _surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = bulletVelocity(bulletPower);
            ew.distanceTraveled = bulletVelocity(bulletPower);
            ew.direction = ((Integer)_surfDirections.get(2)).intValue();
            ew.directAngle = ((Double)_surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double)_enemyLocation.clone(); // last tick
 
            // and adds it to our list of enemy waves
            _enemyWaves.add(ew);
        }
 
        // stores our opponent's energy
        _oppEnergy = e.getEnergy();
 
        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        _enemyLocation = project(_myLocation, absBearing, e.getDistance());
 
        updateWaves();
        
        //only wave surf if there are less than 8; wave surfing doesn't do much at beginning of
        // the melee chaos
        if (getOthers() < 5)
        	doSurfing();
        
        /*
         * Gun
         */
        
        // aims gun
//        setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + 
//        		Math.max((1 - e.getDistance() / (GUN_FACTOR+100)),0) * 
//        		Math.asin(e.getVelocity() / 11) * Math.sin(e.getHeadingRadians() - absBearing) ));
        final double FIREPOWER = 2;
        final double ROBOT_WIDTH = 16,ROBOT_HEIGHT = 16;
        // Variables prefixed with e- refer to enemy, b- refer to bullet and r- refer to robot
        final double eAbsBearing = getHeadingRadians() + e.getBearingRadians();
        final double rX = getX(), rY = getY(),
            bV = Rules.getBulletSpeed(FIREPOWER);
        final double eX = rX + e.getDistance()*Math.sin(eAbsBearing),
            eY = rY + e.getDistance()*Math.cos(eAbsBearing),
            eV = e.getVelocity(),
            eHd = e.getHeadingRadians();
        // These constants make calculating the quadratic coefficients below easier
        final double A = (eX - rX)/bV;
        final double B = eV/bV*Math.sin(eHd);
        final double C = (eY - rY)/bV;
        final double D = eV/bV*Math.cos(eHd);
        // Quadratic coefficients: a*(1/t)^2 + b*(1/t) + c = 0
        final double a = A*A + C*C;
        final double b = 2*(A*B + C*D);
        final double c = (B*B + D*D - 1);
        final double discrim = b*b - 4*a*c;
        if (discrim >= 0) {
            // Reciprocal of quadratic formula
            final double t1 = 2*a/(-b - Math.sqrt(discrim));
            final double t2 = 2*a/(-b + Math.sqrt(discrim));
            final double t = Math.min(t1, t2) >= 0 ? Math.min(t1, t2) : Math.max(t1, t2);
            // Assume enemy stops at walls
            final double endX = limit(
                eX + eV*t*Math.sin(eHd),
                ROBOT_WIDTH/2, getBattleFieldWidth() - ROBOT_WIDTH/2);
            final double endY = limit(
                eY + eV*t*Math.cos(eHd),
                ROBOT_HEIGHT/2, getBattleFieldHeight() - ROBOT_HEIGHT/2);
            setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(
                Math.atan2(endX - rX, endY - rY)
                - getGunHeadingRadians()));
            setFire(FIREPOWER);
        }
    
     
 
        // end gun
        
        if (e.getDistance() <= 500 || getOthers() == 1) // if enemy is close enough, lock on and engage
        	// this math calculates our bullet power relative to our own energy and distance
        	// from opponent
        	setFireBullet((getEnergy()/e.getDistance())*8);
    }
    
    public void onHitByBullet(HitByBulletEvent e) {
        // If the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
    	setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    	doSurfing();

    	if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;
 
            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
 
                if (Math.abs(ew.distanceTraveled -
                    _myLocation.distance(ew.fireLocation)) < 50
                    && Math.abs(bulletVelocity(e.getBullet().getPower()) 
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }
 
            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);
 
                // We can remove this wave now, of course.
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }
    
    public void onRobotDeath(RobotDeathEvent e){
    	enemyHashMap.remove(e.getName());
        sought = null;
    	
    	setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    }
    
    // fed information, this helps predict where a bullet might hit you
    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
        double predictedVelocity = getVelocity();
        double predictedHeading = getHeadingRadians();
        double maxTurning, moveAngle, moveDir;
 
        int counter = 0; // number of ticks in the future
        boolean intercepted = false;
 
        do {    // returns the angle needed to transition smoothly around the walls
        		// without hitting them
            moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                predictedPosition) + (direction * (Math.PI/2)), direction)
                - predictedHeading;
            moveDir = 1;
 
            if(Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }
 
            moveAngle = Utils.normalRelativeAngle(moveAngle);
 
            // maxTurning is built in like this, you can't turn more then this in one tick
            maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading
                + limit(-maxTurning, moveAngle, maxTurning));
 
            // this one is nice ;). if predictedVelocity and moveDir have
            // different signs you want to break down
            // otherwise you want to accelerate (look at the factor "2")
            predictedVelocity +=
                (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);
 
            // calculate the new predicted position
            predictedPosition = project(predictedPosition, predictedHeading,
                predictedVelocity);
 
            counter++;
 
            if (predictedPosition.distance(surfWave.fireLocation) <
                surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while(!intercepted && counter < 500);
 
        return predictedPosition;
    }
    // uses predictPosition and checks the index of the enemy's prior bullets that have hit
    // to help determine which way is safest to move
    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave,
            predictPosition(surfWave, direction));
 
        return _surfStats[index];
    }
 
    // when an enemy bullet launch is detected, checks for danger and moves accordingly
    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
 
        if (surfWave == null) { return; }
 
        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);
 
        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(_myLocation, goAngle - (Math.PI/2), -1);
        } else {
            goAngle = wallSmoothing(_myLocation, goAngle + (Math.PI/2), 1);
        }
 
        setBackAsFront(this, goAngle);
    }
    
    // helps keep track of enemy bullet waves, and removes them when they're no longer a threat
    public void updateWaves() {
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
 
            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled >
                _myLocation.distance(ew.fireLocation) + 50) {
                _enemyWaves.remove(x);
                x--;
            }
        }
    }
    
    // looks in wave index for wave that's closest to hitting and returns it 
    public EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000; // I juse use some very big number here
        EnemyWave surfWave = null;
 
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                - ew.distanceTraveled;
 
            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
 
        return surfWave;
    }
    
    // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, calculate the index into our stat array for that factor.
    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
            - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
            / maxEscapeAngle(ew.bulletVelocity) * ew.direction;
 
        return (int)limit(0,
            (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
            BINS - 1);
    }
    
 // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, update our stat array to reflect the danger in that area.
    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);
 
        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1 / 2;
            // the next one, add 1 / 5; and so on...
            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

// logs an enemy wave's information
class EnemyWave {
    Point2D.Double fireLocation;
    long fireTime;
    double bulletVelocity, directAngle, distanceTraveled;
    int direction;

    public EnemyWave() { }
}

// gives angle at which you should move to avoid walls
// credits to PEZ for this wall smoothing idea
public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
    while (!_fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
        angle += orientation*0.05;
    }
    return angle;
}

// uses trig to figure out an enemy's exact location (used for determining distance from enemy)
public static Point2D.Double project(Point2D.Double sourceLocation,
    double angle, double length) {
    return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
        sourceLocation.y + Math.cos(angle) * length);
}

// returns abs bearing to target (relative to bot's bearing and enemy's heading)
public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
    return Math.atan2(target.x - source.x, target.y - source.y);
}

// helps in calculating lower limits of some calculations (e.g. guess factors)
public static double limit(double min, double value, double max) {
    return Math.max(min, Math.min(value, max));
}

// equation for determining how fast a bullet is travelling given its power
public static double bulletVelocity(double power) {
    return (20.0 - (3.0*power));
}

// equation for figuring out the maximum angle an enemy can move in a tick
public static double maxEscapeAngle(double velocity) {
    return Math.asin(8.0/velocity);
}

// turns bot around
public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
    double angle =
        Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
    if (Math.abs(angle) > (Math.PI/2)) {
        if (angle < 0) {
            robot.setTurnRightRadians(Math.PI + angle);
        } else {
            robot.setTurnLeftRadians(Math.PI - angle);
        }
        robot.setBack(100);
    } else {
        if (angle < 0) {
            robot.setTurnLeftRadians(-1*angle);
       } else {
            robot.setTurnRightRadians(angle);
       }
        robot.setAhead(100);
    }
}
}
